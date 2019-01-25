// Copyright 2017 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#pragma once

#include <string>

#include "VideoBackends/Vulkan/VulkanContext.h"

#include "VideoCommon/PostProcessing.h"
#include "VideoCommon/VideoCommon.h"

namespace Vulkan
{
class Texture2D;

class VulkanPostProcessing : public PostProcessingShaderImplementation
{
public:
  VulkanPostProcessing() = default;
  ~VulkanPostProcessing();

  bool Initialize();

  void BlitFromTexture(const TargetRectangle& dst, const TargetRectangle& src,
                       const Texture2D* src_tex, int src_layer, VkRenderPass render_pass);

  void UpdateConfig();

private:
  size_t CalculateUniformsSize() const;
  void FillUniformBuffer(u8* buf, const TargetRectangle& src, const Texture2D* src_tex,
                         int src_layer);

  bool RecompileShader();
  std::string GetGLSLUniformBlock(bool is_vertex_shader) const;
  std::string ConvertToVulkanGLSL(const std::string& code) const;

  VkShaderModule m_vertex_shader = VK_NULL_HANDLE;
  VkShaderModule m_fragment_shader = VK_NULL_HANDLE;
  bool m_load_vertex_uniforms;
  bool m_load_fragment_uniforms;
};

}  // namespace
