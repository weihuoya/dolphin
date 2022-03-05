// Copyright 2019 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include "VideoCommon/NetPlayGolfUI.h"

#include <fmt/format.h>

#include "Core/NetPlayClient.h"

constexpr float DEFAULT_WINDOW_WIDTH = 220.0f;
constexpr float DEFAULT_WINDOW_HEIGHT = 45.0f;

std::unique_ptr<NetPlayGolfUI> g_netplay_golf_ui;

NetPlayGolfUI::NetPlayGolfUI(std::shared_ptr<NetPlay::NetPlayClient> netplay_client)
    : m_netplay_client{netplay_client}
{
}

NetPlayGolfUI::~NetPlayGolfUI() = default;

void NetPlayGolfUI::Display()
{
}
