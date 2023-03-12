// Copyright 2008 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

// This is currently only used by the DX backend, but it may make sense to
// use it in the GL backend or a future DX10 backend too.

#pragma once

#include "Common/CommonTypes.h"

class IndexGenerator
{
public:
  static void Start(u16* index_ptr);

  static void AddIndices(int primitive, u32 num_vertices);

  static void AddExternalIndices(const u16* indices, u32 num_indices, u32 num_vertices);

  // returns numprimitives
  static u32 GetNumVerts() { return m_base_index; }
  static u32 GetIndexLen() { return (u32)(m_index_buffer_current - m_base_index_ptr); }
  static u32 GetRemainingIndices() { return 65535 - m_base_index; }

private:
  // Triangles
  static u16* AddList(u16* index_ptr, u32 num_verts, u32 index);
  static u16* AddStrip(u16* index_ptr, u32 num_verts, u32 index);
  static u16* AddFan(u16* index_ptr, u32 num_verts, u32 index);

  // Quads
  static u16* AddQuads(u16* index_ptr, u32 num_verts, u32 index);
  static u16* AddQuads_nonstandard(u16* index_ptr, u32 num_verts, u32 index);

  // Lines
  static u16* AddLineList(u16* index_ptr, u32 num_verts, u32 index);
  static u16* AddLineStrip(u16* index_ptr, u32 num_verts, u32 index);

  // Points
  static u16* AddPoints(u16* index_ptr, u32 num_verts, u32 index);

  static u16* m_index_buffer_current;
  static u16* m_base_index_ptr;
  static u32 m_base_index;
};
