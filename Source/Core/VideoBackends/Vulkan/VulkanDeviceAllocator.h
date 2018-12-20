// VulkanDeviceAllocator
//
// Implements a slab based allocator that manages suballocations inside the slabs.
// Bitmaps are used to handle allocation state, with a 1KB grain.
#pragma once

#include <string>
#include <vector>
#include <unordered_map>

#include "VideoBackends/Vulkan/Constants.h"

namespace Vulkan
{

class VulkanDeviceAllocator {
public:
    // Slab sizes start at minSlabSize and double until maxSlabSize.
    // Total slab count is unlimited, as long as there's free memory.
    VulkanDeviceAllocator(size_t minSlabSize, size_t maxSlabSize);
    ~VulkanDeviceAllocator();

    // Requires all memory be free beforehand (including all pending deletes.)
    void Destroy();

    void Begin() {
      Decimate();
    }

    void End() {}

    // May return ALLOCATE_FAILED if the allocation fails.
    size_t Allocate(const VkMemoryRequirements &reqs, VkDeviceMemory *deviceMemory);

    // Crashes on a double or misfree.
    void Free(VkDeviceMemory deviceMemory, size_t offset);

    static const size_t ALLOCATE_FAILED = -1;

private:
    static const size_t SLAB_GRAIN_SIZE = 1024;
    static const uint8_t SLAB_GRAIN_SHIFT = 10;
    static const uint32_t UNDEFINED_MEMORY_TYPE = -1;

    struct Slab {
        VkDeviceMemory deviceMemory;
        std::vector<uint8_t> usage;
        std::unordered_map<size_t, size_t> allocSizes;
        size_t nextFree;
        size_t totalUsage;

        size_t Size() {
          return usage.size() * SLAB_GRAIN_SIZE;
        }
    };

    struct FreeInfo {
        explicit FreeInfo(VulkanDeviceAllocator *a, VkDeviceMemory d, size_t o)
          : allocator(a), deviceMemory(d), offset(o) {
        }

        VulkanDeviceAllocator *allocator;
        VkDeviceMemory deviceMemory;
        size_t offset;
    };

    static void DispatchFree(void *userdata) {
      auto freeInfo = static_cast<FreeInfo *>(userdata);
      freeInfo->allocator->ExecuteFree(freeInfo);  // this deletes freeInfo
    }

    bool AllocateSlab(VkDeviceSize minBytes);
    bool AllocateFromSlab(Slab &slab, size_t &start, size_t blocks);
    void Decimate();
    void ExecuteFree(FreeInfo *userdata);

    std::vector<Slab> slabs_;
    size_t lastSlab_ = 0;
    size_t minSlabSize_;
    const size_t maxSlabSize_;
    uint32_t memoryTypeIndex_ = UNDEFINED_MEMORY_TYPE;
    bool destroyed_ = false;
};

}  // namespace Vulkan
