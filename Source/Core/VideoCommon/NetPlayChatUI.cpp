// Copyright 2019 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include "VideoCommon/NetPlayChatUI.h"

constexpr float DEFAULT_WINDOW_WIDTH = 220.0f;
constexpr float DEFAULT_WINDOW_HEIGHT = 400.0f;

constexpr size_t MAX_BACKLOG_SIZE = 100;

std::unique_ptr<NetPlayChatUI> g_netplay_chat_ui;

NetPlayChatUI::NetPlayChatUI(std::function<void(const std::string&)> callback)
    : m_message_callback{std::move(callback)}
{
}

NetPlayChatUI::~NetPlayChatUI() = default;

void NetPlayChatUI::Display()
{
}

void NetPlayChatUI::AppendChat(std::string message, Color color)
{
  if (m_messages.size() > MAX_BACKLOG_SIZE)
    m_messages.pop_front();

  m_messages.emplace_back(std::move(message), color);

  // Only scroll to bottom, if we were at the bottom previously
  if (m_is_scrolled_to_bottom)
    m_scroll_to_bottom = true;
}

void NetPlayChatUI::SendMessage()
{
  // Check whether the input field is empty
  if (m_message_buf[0] != '\0')
  {
    if (m_message_callback)
      m_message_callback(m_message_buf);

    // 'Empty' the buffer
    m_message_buf[0] = '\0';
  }
}

void NetPlayChatUI::Activate()
{
}
