// Copyright 2009 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "VideoBackends/OGL/PostProcessing.h"

#include "Common/CommonTypes.h"
#include "Common/Logging/Log.h"
#include "Common/StringUtil.h"

#include "Core/Config/GraphicsSettings.h"

#include "VideoBackends/OGL/FramebufferManager.h"
#include "VideoBackends/OGL/OGLTexture.h"
#include "VideoBackends/OGL/ProgramShaderCache.h"
#include "VideoBackends/OGL/SamplerCache.h"

#include "VideoCommon/VideoCommon.h"
#include "VideoCommon/VideoConfig.h"

namespace OGL
{
static const char s_default_vertex_shader[] = R"(
out vec2 uv0;
uniform vec4 src_rect;
void main()
{
  vec2 rawpos = vec2(gl_VertexID&1, gl_VertexID&2);
  gl_Position = vec4(rawpos * 2.0-1.0, 0.0, 1.0);
  uv0 = vec2(mix(src_rect.xy, src_rect.zw, rawpos));
}
)";

static const char s_default_fragment_shader[] = R"(
out float4 ocol0;
in float2 uv0;
uniform int layer;
SAMPLER_BINDING(9) uniform sampler2DArray samp9;
void main()
{
  ocol0 = texture(samp9, float3(uv0, layer));
}
)";

static const char s_vertex_header[] = R"(
  out vec2 uv0;
  uniform vec4 src_rect;
  // Resolution
  uniform vec4 resolution;
  #define VERTEX_SETUP vec2 rawpos = vec2(gl_VertexID&1, gl_VertexID&2); gl_Position = vec4(rawpos * 2.0-1.0, 0.0, 1.0); uv0 = vec2(mix(src_rect.xy, src_rect.zw, rawpos));
  vec2 GetResolution() { return resolution.xy; }
  vec2 GetInvResolution() { return resolution.zw; }
  vec2 GetCoordinates() { return uv0; }
)";

static const char s_fragment_header[] = R"(
  SAMPLER_BINDING(8) uniform sampler2D samp8;
  SAMPLER_BINDING(9) uniform sampler2DArray samp9;

  // Output variable
  out float4 ocol0;
  // Input coordinates
  in float2 uv0;
  // Resolution
  uniform float4 resolution;
  // Time
  uniform uint time;
  // Layer
  uniform int layer;

  // Interfacing functions
  float4 Sample() { return texture(samp9, float3(uv0, layer)); }
  float4 SampleLocation(float2 location) { return texture(samp9, float3(location, layer)); }
  #define SampleOffset(offset) textureOffset(samp9, float3(uv0, layer), offset)
  float4 SampleFontLocation(float2 location) { return texture(samp8, location); }

  float2 GetResolution() { return resolution.xy; }
  float2 GetInvResolution() { return resolution.zw; }
  float2 GetCoordinates() { return uv0; }
  uint GetTime() { return time; }

  void SetOutput(float4 color) { ocol0 = color; }

  #define GetOption() (options.x)
  #define OptionEnabled() (options.x != 0)
)";

OpenGLPostProcessing::OpenGLPostProcessing() : m_initialized(false)
{
}

OpenGLPostProcessing::~OpenGLPostProcessing()
{
  m_shader.Destroy();
}

void OpenGLPostProcessing::BlitFromTexture(TargetRectangle src, TargetRectangle dst,
                                           int src_texture, int src_width, int src_height,
                                           int layer)
{
  ApplyShader();

  glViewport(dst.left, dst.bottom, dst.GetWidth(), dst.GetHeight());

  ProgramShaderCache::BindVertexFormat(nullptr);

  m_shader.Bind();

  glUniform4f(m_uniform_resolution, (float)src_width, (float)src_height, 1.0f / (float)src_width,
              1.0f / (float)src_height);
  glUniform4f(m_uniform_src_rect, src.left / (float)src_width, src.top / (float)src_height,
              src.right / (float)src_width, src.bottom / (float)src_height);
  glUniform1ui(m_uniform_time, (GLuint)m_timer.GetTimeElapsed());
  glUniform1i(m_uniform_layer, layer);

  if (m_config.IsDirty())
  {
    for (auto& it : m_config.GetOptions())
    {
      if (it.second.m_dirty)
      {
        switch (it.second.m_type)
        {
        case PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_BOOL:
          glUniform1i(m_uniform_bindings[it.first], it.second.m_bool_value);
          break;
        case PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_INTEGER:
          switch (it.second.m_integer_values.size())
          {
          case 1:
            glUniform1i(m_uniform_bindings[it.first], it.second.m_integer_values[0]);
            break;
          case 2:
            glUniform2i(m_uniform_bindings[it.first], it.second.m_integer_values[0],
                        it.second.m_integer_values[1]);
            break;
          case 3:
            glUniform3i(m_uniform_bindings[it.first], it.second.m_integer_values[0],
                        it.second.m_integer_values[1], it.second.m_integer_values[2]);
            break;
          case 4:
            glUniform4i(m_uniform_bindings[it.first], it.second.m_integer_values[0],
                        it.second.m_integer_values[1], it.second.m_integer_values[2],
                        it.second.m_integer_values[3]);
            break;
          }
          break;
        case PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_FLOAT:
          switch (it.second.m_float_values.size())
          {
          case 1:
            glUniform1f(m_uniform_bindings[it.first], it.second.m_float_values[0]);
            break;
          case 2:
            glUniform2f(m_uniform_bindings[it.first], it.second.m_float_values[0],
                        it.second.m_float_values[1]);
            break;
          case 3:
            glUniform3f(m_uniform_bindings[it.first], it.second.m_float_values[0],
                        it.second.m_float_values[1], it.second.m_float_values[2]);
            break;
          case 4:
            glUniform4f(m_uniform_bindings[it.first], it.second.m_float_values[0],
                        it.second.m_float_values[1], it.second.m_float_values[2],
                        it.second.m_float_values[3]);
            break;
          }
          break;
        }
        it.second.m_dirty = false;
      }
    }
    m_config.SetDirty(false);
  }

  glActiveTexture(GL_TEXTURE9);
  glBindTexture(GL_TEXTURE_2D_ARRAY, src_texture);
  g_sampler_cache->BindLinearSampler(9);
  glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void OpenGLPostProcessing::ApplyShader()
{
  // shader didn't changed
  if (m_initialized && m_config.GetShader() == g_ActiveConfig.sPostProcessingShader)
    return;

  m_shader.Destroy();
  m_uniform_bindings.clear();

  bool load_all_uniform = false;
  std::string vertex_code;
  std::string fragment_code;

  if(!g_ActiveConfig.sPostProcessingShader.empty())
  {
    // load shader code
    std::string main_code = m_config.LoadShader(g_ActiveConfig.sPostProcessingShader);
    if(!main_code.empty())
    {
      fragment_code = s_fragment_header + LoadShaderOptions() + main_code;
      load_all_uniform = true;
    }
    else
    {
      Config::SetCurrent(Config::GFX_ENHANCE_POST_SHADER, "");
    }

    main_code = m_config.LoadVertexShader(g_ActiveConfig.sPostProcessingShader);
    if(!main_code.empty())
    {
      vertex_code = s_vertex_header + main_code;
    }
  }

  if(vertex_code.empty())
    vertex_code = s_default_vertex_shader;
  if(fragment_code.empty())
    fragment_code = s_default_fragment_shader;

  // and compile it
  if (!ProgramShaderCache::CompileShader(m_shader, vertex_code, fragment_code))
  {
    ERROR_LOG(VIDEO, "Failed to compile post-processing shader %s", m_config.GetShader().c_str());
    Config::SetCurrent(Config::GFX_ENHANCE_POST_SHADER, "");
  }

  // read uniform locations
  m_uniform_src_rect = glGetUniformLocation(m_shader.glprogid, "src_rect");
  m_uniform_layer = glGetUniformLocation(m_shader.glprogid, "layer");
  m_uniform_resolution = GL_INVALID_VALUE;
  m_uniform_time = GL_INVALID_VALUE;

  if(load_all_uniform)
  {
    m_uniform_resolution = glGetUniformLocation(m_shader.glprogid, "resolution");
    m_uniform_time = glGetUniformLocation(m_shader.glprogid, "time");

    for (const auto& it : m_config.GetOptions())
    {
      std::string glsl_name = "options." + it.first;
      m_uniform_bindings[it.first] = glGetUniformLocation(m_shader.glprogid, glsl_name.c_str());
    }
  }

  m_initialized = true;
}

std::string OpenGLPostProcessing::LoadShaderOptions()
{
  m_uniform_bindings.clear();
  if (m_config.GetOptions().empty())
    return "";

  std::string glsl_options = "struct Options\n{\n";

  for (const auto& it : m_config.GetOptions())
  {
    if (it.second.m_type ==
        PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_BOOL)
    {
      glsl_options += StringFromFormat("int     %s;\n", it.first.c_str());
    }
    else if (it.second.m_type ==
             PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_INTEGER)
    {
      u32 count = static_cast<u32>(it.second.m_integer_values.size());
      if (count == 1)
        glsl_options += StringFromFormat("int     %s;\n", it.first.c_str());
      else
        glsl_options += StringFromFormat("int%d   %s;\n", count, it.first.c_str());
    }
    else if (it.second.m_type ==
             PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_FLOAT)
    {
      u32 count = static_cast<u32>(it.second.m_float_values.size());
      if (count == 1)
        glsl_options += StringFromFormat("float   %s;\n", it.first.c_str());
      else
        glsl_options += StringFromFormat("float%d %s;\n", count, it.first.c_str());
    }

    m_uniform_bindings[it.first] = 0;
  }

  glsl_options += "};\n";
  glsl_options += "uniform Options options;\n";

  return glsl_options;
}

}  // namespace OGL
