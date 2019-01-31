// Copyright 2016 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#pragma once

#include <memory>
#include <string>

#include "Common/CommonTypes.h"
#include "VideoBackends/Vulkan/Constants.h"

namespace Vulkan
{
class Texture2D;

class RasterFont
{
public:
  RasterFont();
  ~RasterFont();

  void PrintMultiLineText(VkRenderPass render_pass, const std::string& text, float start_x,
                          float start_y, int bbWidth, int bbHeight, u32 color, float scale = 1.0f);

private:
  bool CreateTexture();
  bool CreateShaders();

  std::unique_ptr<Texture2D> m_texture;

  VkShaderModule m_vertex_shader = VK_NULL_HANDLE;
  VkShaderModule m_fragment_shader = VK_NULL_HANDLE;
};

}  // namespace Vulkan
