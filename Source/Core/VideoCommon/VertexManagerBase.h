// Copyright 2010 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#pragma once

#include <memory>
#include <vector>

#include "Common/CommonTypes.h"
#include "Common/MathUtil.h"
#include "VideoCommon/RenderState.h"
#include "VideoCommon/ShaderCache.h"

class DataReader;
class NativeVertexFormat;
class PointerWrap;
struct PortableVertexDeclaration;

struct Slope
{
  float dfdx;
  float dfdy;
  float f0;
  bool dirty;
};

// View format of the input data to the texture decoding shader.
enum TexelBufferFormat : u32
{
  TEXEL_BUFFER_FORMAT_R8_UINT,
  TEXEL_BUFFER_FORMAT_R16_UINT,
  TEXEL_BUFFER_FORMAT_RGBA8_UINT,
  TEXEL_BUFFER_FORMAT_R32G32_UINT,
  NUM_TEXEL_BUFFER_FORMATS
};

class VertexManagerBase
{
private:
  // 3 pos
  static constexpr u32 SMALLEST_POSSIBLE_VERTEX = sizeof(float) * 3;
  // 3 pos, 3*3 normal, 2*u32 color, 8*4 tex, 1 posMat
  static constexpr u32 LARGEST_POSSIBLE_VERTEX = sizeof(float) * 45 + sizeof(u32) * 2;

  static constexpr u32 MAX_PRIMITIVES_PER_COMMAND = 65535;

public:
  static constexpr u32 MAXVBUFFERSIZE =
      MathUtil::NextPowerOf2(MAX_PRIMITIVES_PER_COMMAND * LARGEST_POSSIBLE_VERTEX);

  // We may convert triangle-fans to triangle-lists, almost 3x as many indices.
  static constexpr u32 MAXIBUFFERSIZE = MathUtil::NextPowerOf2(MAX_PRIMITIVES_PER_COMMAND * 3);

  // Streaming buffer sizes.
  // Texel buffer will fit the maximum size of an encoded GX texture. 1024x1024, RGBA8 = 4MB.
  static constexpr u32 VERTEX_STREAM_BUFFER_SIZE = 32 * 1024 * 1024;
  static constexpr u32 INDEX_STREAM_BUFFER_SIZE = 4 * 1024 * 1024;
  static constexpr u32 UNIFORM_STREAM_BUFFER_SIZE = 16 * 1024 * 1024;
  static constexpr u32 TEXEL_STREAM_BUFFER_SIZE = 16 * 1024 * 1024;

  VertexManagerBase();
  virtual ~VertexManagerBase();

  virtual bool Initialize();

  PrimitiveType GetCurrentPrimitiveType() const { return m_current_primitive_type; }
  DataReader PrepareForAdditionalData(int primitive, u32 count, u32 stride, bool cullall);
  void FlushData(u32 count, u32 stride);

  void Flush();

  void DoState(PointerWrap& p);

  std::pair<size_t, size_t> ResetFlushAspectRatioCount();

  // State setters, called from register update functions.
  void SetRasterizationStateChanged() { m_rasterization_state_changed = true; }
  void SetDepthStateChanged() { m_depth_state_changed = true; }
  void SetBlendingStateChanged() { m_blending_state_changed = true; }
  void InvalidatePipelineObject()
  {
    m_current_pipeline_object = nullptr;
    m_pipeline_config_changed = true;
  }

  // In the Vulkan backend, we manually manage our uniform buffers. Therefore, we must invalidate
  // the values when a command buffer is submitted, as the buffer can be re-used.
  static void InvalidateConstants();

  // Utility pipeline drawing (e.g. EFB copies, post-processing, UI).
  virtual void UploadUtilityUniforms(const void* uniforms, u32 uniforms_size);
  void UploadUtilityVertices(const void* vertices, u32 vertex_stride, u32 num_vertices,
                             const u16* indices, u32 num_indices, u32* out_base_vertex,
                             u32* out_base_index);

  // Determine how many bytes there are in each element of the texel buffer.
  // Needed for alignment and stride calculations.
  static u32 GetTexelBufferElementSize(TexelBufferFormat buffer_format);

  // Texel buffer, used for palette conversion.
  virtual bool UploadTexelBuffer(const void* data, u32 data_size, TexelBufferFormat format,
                                 u32* out_offset);

  // The second set of parameters uploads a second blob in the same buffer, used for GPU texture
  // decoding for palette textures, as both the texture data and palette must be uploaded.
  virtual bool UploadTexelBuffer(const void* data, u32 data_size, TexelBufferFormat format,
                                 u32* out_offset, const void* palette_data, u32 palette_size,
                                 TexelBufferFormat palette_format, u32* out_palette_offset);

  // CPU access tracking - call after a draw call is made.
  void OnDraw();

  // Call after CPU access is requested.
  void OnCPUEFBAccess();

  // Call after an EFB copy to RAM. If true, the current command buffer should be executed.
  void OnEFBCopyToRAM();

  // Call at the end of a frame.
  void OnEndFrame();

protected:
  // Prepares the buffer for the next batch of vertices.
  virtual void ResetBuffer(u32 vertex_stride);

  // Commits/uploads the current batch of vertices.
  virtual void CommitBuffer(u32 num_vertices, u32 vertex_stride, u32 num_indices,
                            u32* out_base_vertex, u32* out_base_index);

  // Uploads uniform buffers for GX draws.
  virtual void UploadUniforms();

  // Issues the draw call for the current batch in the backend.
  virtual void DrawCurrentBatch(u32 base_index, u32 num_indices, u32 base_vertex);

  u32 GetRemainingSize() const;
  static u32 GetRemainingIndices(int primitive);

  void CalculateZSlope(NativeVertexFormat* format);

  u8* m_cur_buffer_pointer = nullptr;
  u8* m_base_buffer_pointer = nullptr;
  u8* m_end_buffer_pointer = nullptr;

  // Alternative buffers in CPU memory for primitives we are going to discard.
  std::vector<u8> m_cpu_vertex_buffer;
  std::vector<u16> m_cpu_index_buffer;

  Slope m_zslope = {};

  // dual source blend
  const AbstractPipeline* GetPipelineForAlphaPass();

  VideoCommon::GXPipelineUid m_current_pipeline_config;
  const AbstractPipeline* m_current_pipeline_object = nullptr;
  PrimitiveType m_current_primitive_type = PrimitiveType::Points;
  bool m_pipeline_config_changed = true;
  bool m_rasterization_state_changed = true;
  bool m_depth_state_changed = true;
  bool m_blending_state_changed = true;
  bool m_cull_all = false;

private:
  // Minimum number of draws per command buffer when attempting to preempt a readback operation.
  static constexpr u32 MINIMUM_DRAW_CALLS_PER_COMMAND_BUFFER_FOR_READBACK = 10;

  void UpdatePipelineConfig();
  void UpdatePipelineObject();

  bool m_is_flushed = true;
  size_t m_flush_count_4_3 = 0;
  size_t m_flush_count_anamorphic = 0;

  // CPU access tracking
  u32 m_draw_counter = 0;
  u32 m_last_efb_copy_draw_counter = 0;
  std::vector<u32> m_cpu_accesses_this_frame;
  std::vector<u32> m_scheduled_command_buffer_kicks;
  bool m_allow_background_execution = true;
};

extern std::unique_ptr<VertexManagerBase> g_vertex_manager;
