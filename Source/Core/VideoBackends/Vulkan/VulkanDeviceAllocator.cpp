#include "Common/Logging/Log.h"
#include "Common/Assert.h"
#include "VideoBackends/Vulkan/VulkanContext.h"
#include "VideoBackends/Vulkan/CommandBufferManager.h"
#include "VideoBackends/Vulkan/VulkanDeviceAllocator.h"

namespace Vulkan
{

VulkanDeviceAllocator::VulkanDeviceAllocator(size_t minSlabSize, size_t maxSlabSize)
  : minSlabSize_(minSlabSize), maxSlabSize_(maxSlabSize)
{
  ASSERT((minSlabSize_ & (SLAB_GRAIN_SIZE - 1)) == 0);
}

VulkanDeviceAllocator::~VulkanDeviceAllocator()
{
  ASSERT(destroyed_);
  ASSERT(slabs_.empty());
}

void VulkanDeviceAllocator::Destroy()
{
  for (Slab &slab : slabs_)
  {
    // Did anyone forget to free?
    for (auto pair : slab.allocSizes)
    {
      int slabUsage = slab.usage[pair.first];
      // If it's not 2 (queued), there's a leak.
      // If it's zero, it means allocSizes is somehow out of sync.
      if (slabUsage == 1)
      {
        ERROR_LOG(VIDEO, "VulkanDeviceAllocator detected memory leak of size %d", (int)pair.second);
      }
      else
        {
        ASSERT_MSG(MASTER_LOG, slabUsage == 2, "Destroy: slabUsage has unexpected value %d", slabUsage);
      }
    }

    ASSERT(slab.deviceMemory);
    vkFreeMemory(g_vulkan_context->GetDevice(), slab.deviceMemory, nullptr);
  }
  slabs_.clear();
  destroyed_ = true;
}

size_t VulkanDeviceAllocator::Allocate(const VkMemoryRequirements &reqs, VkDeviceMemory *deviceMemory)
{
  ASSERT(!destroyed_);
  uint32_t memoryTypeIndex = g_vulkan_context->GetMemoryType(reqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

  if (memoryTypeIndex_ == UNDEFINED_MEMORY_TYPE)
  {
    memoryTypeIndex_ = memoryTypeIndex;
  }
  else if (memoryTypeIndex_ != memoryTypeIndex)
  {
    ASSERT(memoryTypeIndex_ == memoryTypeIndex);
    return ALLOCATE_FAILED;
  }

  size_t size = reqs.size;

  size_t align = reqs.alignment <= SLAB_GRAIN_SIZE ? 1 : (size_t)(reqs.alignment >> SLAB_GRAIN_SHIFT);
  size_t blocks = (size_t)((size + SLAB_GRAIN_SIZE - 1) >> SLAB_GRAIN_SHIFT);

  const size_t numSlabs = slabs_.size();
  for (size_t i = 0; i < numSlabs; ++i)
  {
    // We loop starting at the last successful allocation.
    // This helps us "creep forward", and also spend less time allocating.
    const size_t actualSlab = (lastSlab_ + i) % numSlabs;
    Slab &slab = slabs_[actualSlab];
    size_t start = slab.nextFree;

    while (start < slab.usage.size())
    {
      start = (start + align - 1) & ~(align - 1);
      if (AllocateFromSlab(slab, start, blocks))
      {
        // Allocated?  Great, let's return right away.
        *deviceMemory = slab.deviceMemory;
        lastSlab_ = actualSlab;
        return start << SLAB_GRAIN_SHIFT;
      }
    }
  }

  // Okay, we couldn't fit it into any existing slabs.  We need a new one.
  if (!AllocateSlab(size))
  {
    return ALLOCATE_FAILED;
  }

  // Guaranteed to be the last one, unless it failed to allocate.
  Slab &slab = slabs_[slabs_.size() - 1];
  size_t start = 0;
  if (AllocateFromSlab(slab, start, blocks))
  {
    *deviceMemory = slab.deviceMemory;
    lastSlab_ = slabs_.size() - 1;
    return start << SLAB_GRAIN_SHIFT;
  }

  // Somehow... we're out of space.  Darn.
  return ALLOCATE_FAILED;
}

bool VulkanDeviceAllocator::AllocateFromSlab(Slab &slab, size_t &start, size_t blocks)
{
  ASSERT(!destroyed_);

  if (start + blocks > slab.usage.size())
  {
    start = slab.usage.size();
    return false;
  }

  for (size_t i = 0; i < blocks; ++i)
  {
    if (slab.usage[start + i])
    {
      // If we just ran into one, there's probably an allocation size.
      auto it = slab.allocSizes.find(start + i);
      if (it != slab.allocSizes.end())
      {
        start += i + it->second;
      }
      else
      {
        // We don't know how big it is, so just skip to the next one.
        start += i + 1;
      }
      return false;
    }
  }

  // Okay, this run is good.  Actually mark it.
  for (size_t i = 0; i < blocks; ++i)
  {
    slab.usage[start + i] = 1;
  }
  slab.nextFree = start + blocks;
  if (slab.nextFree >= slab.usage.size())
  {
    slab.nextFree = 0;
  }

  // Remember the size so we can free.
  slab.allocSizes[start] = blocks;
  slab.totalUsage += blocks;
  return true;
}

void VulkanDeviceAllocator::Free(VkDeviceMemory deviceMemory, size_t offset)
{
  ASSERT(!destroyed_);
  ASSERT_MSG(MASTER_LOG, !slabs_.empty(), "No slabs - can't be anything to free! double-freed?");

  // First, let's validate.  This will allow stack traces to tell us when frees are bad.
  size_t start = offset >> SLAB_GRAIN_SHIFT;
  bool found = false;
  for (Slab &slab : slabs_)
  {
    if (slab.deviceMemory != deviceMemory)
    {
      continue;
    }

    auto it = slab.allocSizes.find(start);
    ASSERT_MSG(MASTER_LOG, it != slab.allocSizes.end(), "Double free?");
    // This means a double free, while queued to actually free.
    ASSERT_MSG(MASTER_LOG, slab.usage[start] == 1, "Double free when queued to free!");

    // Mark it as "free in progress".
    slab.usage[start] = 2;
    found = true;
    break;
  }

  // Wrong deviceMemory even?  Maybe it was already decimated, but that means a double-free.
  ASSERT_MSG(MASTER_LOG, found, "Failed to find allocation to free! Double-freed?");

  // Okay, now enqueue.  It's valid.
  FreeInfo *info = new FreeInfo(this, deviceMemory, offset);
  // Dispatches a call to ExecuteFree on the next delete round.
  g_command_buffer_mgr->DeferCallback(std::bind(&DispatchFree, info));
}

void VulkanDeviceAllocator::ExecuteFree(FreeInfo *userdata)
{
  if (destroyed_)
  {
    // We already freed this, and it's been validated.
    delete userdata;
    return;
  }

  VkDeviceMemory deviceMemory = userdata->deviceMemory;
  size_t offset = userdata->offset;

  // Revalidate in case something else got freed and made things inconsistent.
  size_t start = offset >> SLAB_GRAIN_SHIFT;
  bool found = false;
  for (Slab &slab : slabs_)
  {
    if (slab.deviceMemory != deviceMemory)
    {
      continue;
    }

    auto it = slab.allocSizes.find(start);
    if (it != slab.allocSizes.end())
    {
      size_t size = it->second;
      for (size_t i = 0; i < size; ++i)
      {
        slab.usage[start + i] = 0;
      }
      slab.allocSizes.erase(it);
      slab.totalUsage -= size;

      // Allow reusing.
      if (slab.nextFree > start)
      {
        slab.nextFree = start;
      }
    }
    else
    {
      // Ack, a double free?
      ASSERT_MSG(MASTER_LOG, false, "Double free? Block missing at offset %d", (int)userdata->offset);
    }
    found = true;
    break;
  }

  // Wrong deviceMemory even?  Maybe it was already decimated, but that means a double-free.
  ASSERT_MSG(MASTER_LOG, found, "ExecuteFree: Block not found (offset %d)", (int)offset);
  delete userdata;
}

bool VulkanDeviceAllocator::AllocateSlab(VkDeviceSize minBytes)
{
  ASSERT(!destroyed_);
  if (!slabs_.empty() && minSlabSize_ < maxSlabSize_)
  {
    // We're allocating an additional slab, so rachet up its size.
    minSlabSize_ <<= 1;
  }

  VkMemoryAllocateInfo alloc{ VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO };
  alloc.allocationSize = minSlabSize_;
  alloc.memoryTypeIndex = memoryTypeIndex_;

  while (alloc.allocationSize < minBytes)
  {
    alloc.allocationSize <<= 1;
  }

  VkDeviceMemory deviceMemory;
  VkResult res = vkAllocateMemory(g_vulkan_context->GetDevice(), &alloc, NULL, &deviceMemory);
  if (res != VK_SUCCESS)
  {
    // If it's something else, we used it wrong?
    ASSERT(res == VK_ERROR_OUT_OF_HOST_MEMORY || res == VK_ERROR_OUT_OF_DEVICE_MEMORY ||
           res == VK_ERROR_TOO_MANY_OBJECTS);
    // Okay, so we ran out of memory.
    return false;
  }

  slabs_.resize(slabs_.size() + 1);
  Slab &slab = slabs_[slabs_.size() - 1];
  slab.deviceMemory = deviceMemory;
  slab.usage.resize((size_t)(alloc.allocationSize >> SLAB_GRAIN_SHIFT));

  return true;
}

void VulkanDeviceAllocator::Decimate()
{
  ASSERT(!destroyed_);
  bool foundFree = false;

  for (size_t i = 0; i < slabs_.size(); ++i)
  {
    // Go backwards.  This way, we keep the largest free slab.
    // We do this here (instead of the for) since size_t is unsigned.
    size_t index = slabs_.size() - i - 1;
    auto &slab = slabs_[index];

    if (!slab.allocSizes.empty()) {
      size_t usagePercent = 100 * slab.totalUsage / slab.usage.size();
      size_t freeNextPercent = 100 * slab.nextFree / slab.usage.size();

      // This may mean we're going to leave an allocation hanging.  Reset nextFree instead.
      if (freeNextPercent >= 100 - usagePercent)
      {
        size_t newFree = 0;
        while (newFree < slab.usage.size())
        {
          auto it = slab.allocSizes.find(newFree);
          if (it == slab.allocSizes.end())
          {
            break;
          }

          newFree += it->second;
        }

        slab.nextFree = newFree;
      }
      continue;
    }

    if (!foundFree)
    {
      // Let's allow one free slab, so we have room.
      foundFree = true;
      continue;
    }

    // Okay, let's free this one up.
    vkFreeMemory(g_vulkan_context->GetDevice(), slab.deviceMemory, nullptr);
    slabs_.erase(slabs_.begin() + index);

    // Let's check the next one, which is now in this same slot.
    --i;
  }
}

}  // namespace Vulkan
