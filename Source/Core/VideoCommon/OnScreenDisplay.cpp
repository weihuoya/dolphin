// Copyright 2009 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include "VideoCommon/OnScreenDisplay.h"

#include <algorithm>
#include <atomic>
#include <map>
#include <mutex>
#include <string>

#include <fmt/format.h>
#include <imgui.h>

#include "Common/CommonTypes.h"
#include "Common/Config/Config.h"
#include "Common/Timer.h"

#include "Core/Config/MainSettings.h"
#include "RenderBase.h"

namespace OSD
{
static std::atomic<int> s_obscured_pixels_left = 0;
static std::atomic<int> s_obscured_pixels_top = 0;
static std::multimap<MessageType, Message> s_messages;
static std::mutex s_messages_mutex;

void AddTypedMessage(MessageType type, const std::string& message, u32 ms, u32 argb)
{
  if (Config::Get(Config::MAIN_OSD_MESSAGES))
  {
    std::lock_guard<std::mutex> lock(s_messages_mutex);
    s_messages.erase(type);
    u32 timestamp = Common::Timer::GetTimeMs() + ms;
    s_messages.emplace(type, Message(message, timestamp, argb));
  }
}

void AddMessage(const std::string message, u32 ms, u32 argb)
{
  if (Config::Get(Config::MAIN_OSD_MESSAGES))
  {
    std::lock_guard<std::mutex> lock(s_messages_mutex);
    u32 timestamp = Common::Timer::GetTimeMs() + ms;
    s_messages.emplace(MessageType::Typeless, Message(message, timestamp, argb));
  }
}

void DrawMessage(const Message& msg, int top, int left, int time_left)
{
  float alpha = std::min(1.0f, std::max(0.0f, time_left / 1024.0f));
  u32 color = (msg.color & 0xFFFFFF) | ((u32)((msg.color >> 24) * alpha) << 24);
  g_renderer->RenderText(msg.text, left, top, color);
}

void DrawMessages()
{
  if (Config::Get(Config::MAIN_OSD_MESSAGES))
  {
    int left = 10, top = 28;
    u32 now = Common::Timer::GetTimeMs();
    std::lock_guard<std::mutex> lock(s_messages_mutex);
    auto it = s_messages.begin();
    while (it != s_messages.end())
    {
      const Message& msg = it->second;
      int time_left = (int)(msg.timestamp - now);
      DrawMessage(msg, top, left, time_left);

      if (time_left <= 0)
        it = s_messages.erase(it);
      else
        ++it;
      top += 15;
    }
  }
}

void ClearMessages()
{
  std::lock_guard lock{s_messages_mutex};
  s_messages.clear();
}

void SetObscuredPixelsLeft(int width)
{
  s_obscured_pixels_left = width;
}

void SetObscuredPixelsTop(int height)
{
  s_obscured_pixels_top = height;
}
}  // namespace OSD
