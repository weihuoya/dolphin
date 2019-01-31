// Copyright 2008 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include <cstring>
#include <string>
#include <utility>

#include "Common/StringUtil.h"
#include "VideoCommon/Statistics.h"
#include "VideoCommon/VertexLoaderManager.h"
#include "VideoCommon/VideoConfig.h"

Statistics stats;

void Statistics::ResetFrame()
{
  memset(&thisFrame, 0, sizeof(ThisFrame));
}

void Statistics::SwapDL()
{
  std::swap(stats.thisFrame.numDLPrims, stats.thisFrame.numPrims);
  std::swap(stats.thisFrame.numXFLoadsInDL, stats.thisFrame.numXFLoads);
  std::swap(stats.thisFrame.numCPLoadsInDL, stats.thisFrame.numCPLoads);
  std::swap(stats.thisFrame.numBPLoadsInDL, stats.thisFrame.numBPLoads);
}

void Statistics::Display()
{
}

// Is this really needed?
void Statistics::DisplayProj()
{
}
