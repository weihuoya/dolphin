// Copyright 2017 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "VideoBackends/Vulkan/PostProcessing.h"
#include <sstream>

#include "Common/Assert.h"
#include "Common/StringUtil.h"

#include "Core/Config/GraphicsSettings.h"

#include "VideoBackends/Vulkan/CommandBufferManager.h"
#include "VideoBackends/Vulkan/ObjectCache.h"
#include "VideoBackends/Vulkan/ShaderCache.h"
#include "VideoBackends/Vulkan/Texture2D.h"
#include "VideoBackends/Vulkan/Util.h"
#include "VideoBackends/Vulkan/VulkanContext.h"

#include "VideoCommon/VideoCommon.h"
#include "VideoCommon/VideoConfig.h"

namespace Vulkan
{
VulkanPostProcessing::~VulkanPostProcessing()
{
  if (m_vertex_shader != VK_NULL_HANDLE)
    vkDestroyShaderModule(g_vulkan_context->GetDevice(), m_vertex_shader, nullptr);
  if (m_fragment_shader != VK_NULL_HANDLE)
    vkDestroyShaderModule(g_vulkan_context->GetDevice(), m_fragment_shader, nullptr);
}

bool VulkanPostProcessing::Initialize()
{
  return RecompileShader();
}

void VulkanPostProcessing::BlitFromTexture(const TargetRectangle& dst, const TargetRectangle& src,
                                           const Texture2D* src_tex, int src_layer,
                                           VkRenderPass render_pass)
{
  VkShaderModule vertex_shader = m_vertex_shader;
  VkShaderModule fragment_shader = m_fragment_shader;
  if (vertex_shader == VK_NULL_HANDLE)
  {
    vertex_shader = g_shader_cache->GetScreenQuadVertexShader();
  }
  UtilityShaderDraw draw(g_command_buffer_mgr->GetCurrentCommandBuffer(),
                         g_object_cache->GetPipelineLayout(PIPELINE_LAYOUT_STANDARD), render_pass,
                         vertex_shader, VK_NULL_HANDLE, fragment_shader);

  // Source is always bound.
  draw.SetPSSampler(0, src_tex->GetView(), g_object_cache->GetLinearSampler());

  // No need to allocate uniforms for the default shader.
  // The config will also still contain the invalid shader at this point.
  if (m_load_fragment_uniforms)
  {
    size_t uniforms_size = CalculateUniformsSize();
    u8* uniforms = draw.AllocatePSUniforms(uniforms_size);
    FillUniformBuffer(uniforms, src, src_tex, src_layer);
    draw.CommitPSUniforms(uniforms_size);

    if(m_load_vertex_uniforms)
    {
      uniforms = draw.AllocateVSUniforms(uniforms_size);
      FillUniformBuffer(uniforms, src, src_tex, src_layer);
      draw.CommitVSUniforms(uniforms_size);
    }
  }

  draw.SetViewportAndScissor(dst.left, dst.top, dst.GetWidth(), dst.GetHeight());
  draw.DrawWithoutVertexBuffer(4);
}

struct BuiltinUniforms
{
  float resolution[4];
  float src_rect[4];
  u32 time;
  u32 unused[3];
};

size_t VulkanPostProcessing::CalculateUniformsSize() const
{
  // Allocate a vec4 for each uniform to simplify allocation.
  return sizeof(BuiltinUniforms) + m_config.GetOptions().size() * sizeof(float) * 4;
}

void VulkanPostProcessing::FillUniformBuffer(u8* buf, const TargetRectangle& src,
                                             const Texture2D* src_tex, int src_layer)
{
  float src_width_float = static_cast<float>(src_tex->GetWidth());
  float src_height_float = static_cast<float>(src_tex->GetHeight());
  BuiltinUniforms builtin_uniforms = {
      {src_width_float, src_height_float, 1.0f / src_width_float, 1.0f / src_height_float},
      {static_cast<float>(src.left) / src_width_float,
       static_cast<float>(src.top) / src_height_float,
       static_cast<float>(src.GetWidth()) / src_width_float,
       static_cast<float>(src.GetHeight()) / src_height_float},
      static_cast<u32>(m_timer.GetTimeElapsed())};

  std::memcpy(buf, &builtin_uniforms, sizeof(builtin_uniforms));
  buf += sizeof(builtin_uniforms);

  for (const auto& it : m_config.GetOptions())
  {
    union
    {
      u32 as_bool[4];
      s32 as_int[4];
      float as_float[4];
    } value = {};

    switch (it.second.m_type)
    {
    case PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_BOOL:
      value.as_bool[0] = it.second.m_bool_value ? 1 : 0;
      break;

    case PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_INTEGER:
      ASSERT(it.second.m_integer_values.size() < 4);
      std::copy_n(it.second.m_integer_values.begin(), it.second.m_integer_values.size(),
                  value.as_int);
      break;

    case PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_FLOAT:
      ASSERT(it.second.m_float_values.size() < 4);
      std::copy_n(it.second.m_float_values.begin(), it.second.m_float_values.size(),
                  value.as_float);
      break;
    }

    std::memcpy(buf, &value, sizeof(value));
    buf += sizeof(value);
  }
}

constexpr char DEFAULT_VERTEX_SHADER_SOURCE[] = R"(
  layout(location = 0) out float3 uv0;

  void main()
  {
      /*
       * id   &1    &2   clamp(*2-1)
       * 0    0,0   0,0  -1,-1      TL
       * 1    1,0   1,0  1,-1       TR
       * 2    0,2   0,1  -1,1       BL
       * 3    1,2   1,1  1,1        BR
       */
      float2 rawpos = float2(gl_VertexID&1, gl_VertexID&2);
      gl_Position = float4(rawpos * 2.0f - 1.0f, 0.0f, 1.0f);
      uv0 = float3(rawpos, 0.0f);
  }
)";

constexpr char DEFAULT_FRAGMENT_SHADER_SOURCE[] = R"(
  layout(set = 1, binding = 0) uniform sampler2DArray samp9;

  layout(location = 0) in float3 uv0;
  layout(location = 0) out float4 ocol0;

  void main()
  {
    ocol0 = float4(texture(samp9, uv0).xyz, 1.0);
  }
)";

constexpr char POSTPROCESSING_VERTEX_HEADER[] = R"(
  layout(location = 0) out float3 uv0;

  #define VERTEX_SETUP float2 rawpos = float2(gl_VertexID&1, gl_VertexID&2); gl_Position = float4(rawpos * 2.0f - 1.0f, 0.0f, 1.0f); uv0 = float3(rawpos, 0.0f);
  #define GetResolution() (options.resolution.xy)
  #define GetInvResolution() (options.resolution.zw)
  #define GetCoordinates() (uv0.xy)
)";

constexpr char POSTPROCESSING_FRAGMENT_HEADER[] = R"(
  SAMPLER_BINDING(0) uniform sampler2DArray samp9;

  layout(location = 0) in float3 uv0;
  layout(location = 0) out float4 ocol0;

  // Interfacing functions
  #define Sample() texture(samp9, uv0)
  #define SampleLocation(location) texture(samp9, float3(location, uv0.z))

  #define SetOutput(color) (ocol0 = color)
  #define GetResolution() (options.resolution.xy)
  #define GetInvResolution() (options.resolution.zw)
  #define GetCoordinates() (uv0.xy)
  #define GetTime() (options.time)
  #define GetOption() (options.x)
  #define OptionEnabled() (options.x != 0)
)";

void VulkanPostProcessing::UpdateConfig()
{
  if (m_config.GetShader() == g_ActiveConfig.sPostProcessingShader)
    return;

  RecompileShader();
}

bool VulkanPostProcessing::RecompileShader()
{
  // As a driver can return the same new module pointer when destroying a shader and re-compiling,
  // we need to wipe out the pipeline cache, otherwise we risk using old pipelines with old shaders.
  // We can't just clear a single pipeline, because we don't know which render pass is going to be
  // used here either.
  if (m_fragment_shader != VK_NULL_HANDLE)
  {
    g_command_buffer_mgr->WaitForGPUIdle();
    g_shader_cache->ClearPipelineCache();
    vkDestroyShaderModule(g_vulkan_context->GetDevice(), m_fragment_shader, nullptr);
    m_fragment_shader = VK_NULL_HANDLE;

    if (m_vertex_shader != VK_NULL_HANDLE)
    {
      vkDestroyShaderModule(g_vulkan_context->GetDevice(), m_vertex_shader, nullptr);
      m_vertex_shader = VK_NULL_HANDLE;
    }
  }

  std::string fragment_code;
  m_load_vertex_uniforms = false;
  m_load_fragment_uniforms = false;

  // Generate GLSL and compile the new shader.
  if (!g_ActiveConfig.sPostProcessingShader.empty())
  {
    std::string main_code = m_config.LoadShader(g_ActiveConfig.sPostProcessingShader);
    if (!main_code.empty())
    {
      std::string options_code = GetGLSLUniformBlock(false);
      fragment_code =
          options_code + POSTPROCESSING_FRAGMENT_HEADER + ConvertToVulkanGLSL(main_code);
      m_load_fragment_uniforms = true;
    }
    else
    {
      Config::SetCurrent(Config::GFX_ENHANCE_POST_SHADER, "");
    }

    // load vertex shader
    main_code = m_config.LoadVertexShader(g_ActiveConfig.sPostProcessingShader);
    if (!main_code.empty())
    {
      std::string options_code = GetGLSLUniformBlock(true);
      std::string vertex_code =
        options_code + POSTPROCESSING_VERTEX_HEADER + ConvertToVulkanGLSL(main_code);
      m_vertex_shader = Util::CompileAndCreateVertexShader(vertex_code);
      m_load_vertex_uniforms = true;
      if (m_vertex_shader == VK_NULL_HANDLE)
      {
        // BlitFromTexture will use the default shader as a fallback.
        PanicAlert("Failed to compile post-processing vertex shader %s", vertex_code.c_str());
        Config::SetCurrent(Config::GFX_ENHANCE_POST_SHADER, "");
        return false;
      }
    }
  }

  if (fragment_code.empty())
    fragment_code = DEFAULT_FRAGMENT_SHADER_SOURCE;

  m_fragment_shader = Util::CompileAndCreateFragmentShader(fragment_code);
  if (m_fragment_shader == VK_NULL_HANDLE)
  {
    // BlitFromTexture will use the default shader as a fallback.
    PanicAlert("Failed to compile post-processing shader %s", fragment_code.c_str());
    Config::SetCurrent(Config::GFX_ENHANCE_POST_SHADER, "");
    return false;
  }

  return true;
}

std::string VulkanPostProcessing::GetGLSLUniformBlock(bool is_vertex_shader) const
{
  std::stringstream ss;
  u32 unused_counter = 1;

  if(is_vertex_shader)
  {
    ss << "UBO_BINDING(std140, 2) uniform VSBlock {\n";
  }
  else
  {
    ss << "UBO_BINDING(std140, 1) uniform PSBlock {\n";
  }

  // Builtin uniforms
  ss << "  float4 resolution;\n";
  ss << "  float4 src_rect;\n";
  ss << "  uint time;\n";
  for (u32 i = 0; i < 3; i++)
    ss << "  uint unused" << unused_counter++ << ";\n\n";

  // Custom options/uniforms
  for (const auto& it : m_config.GetOptions())
  {
    if (it.second.m_type ==
        PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_BOOL)
    {
      ss << StringFromFormat("  int %s;\n", it.first.c_str());
      for (u32 i = 0; i < 3; i++)
        ss << "  int unused" << unused_counter++ << ";\n";
    }
    else if (it.second.m_type ==
             PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_INTEGER)
    {
      u32 count = static_cast<u32>(it.second.m_integer_values.size());
      if (count == 1)
        ss << StringFromFormat("  int %s;\n", it.first.c_str());
      else
        ss << StringFromFormat("  int%u %s;\n", count, it.first.c_str());

      for (u32 i = count; i < 4; i++)
        ss << "  int unused" << unused_counter++ << ";\n";
    }
    else if (it.second.m_type ==
             PostProcessingShaderConfiguration::ConfigurationOption::OptionType::OPTION_FLOAT)
    {
      u32 count = static_cast<u32>(it.second.m_float_values.size());
      if (count == 1)
        ss << StringFromFormat("  float %s;\n", it.first.c_str());
      else
        ss << StringFromFormat("  float%u %s;\n", count, it.first.c_str());

      for (u32 i = count; i < 4; i++)
        ss << "  float unused" << unused_counter++ << ";\n";
    }
  }

  ss << "} options;\n\n";

  return ss.str();
}

std::string VulkanPostProcessing::ConvertToVulkanGLSL(const std::string& code) const
{
  std::string line;
  std::string result;
  std::stringstream instream(code);
  int location_index = 1;
  while (std::getline(instream, line))
  {
    if (line.find("in ") == 0 || line.find("out ") == 0)
    {
      result += StringFromFormat("layout(location = %d) %s\n", location_index++, line.c_str());
    }
    else
    {
      result += line;
      result += "\n";
    }
  }
  return result;
}

}  // namespace Vulkan
