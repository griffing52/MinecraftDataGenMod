# Entity Segmentation & Depth Fix

## Issue
Entities (like sheep, cows, etc.) were not appearing in segmentation masks, even though the code had entity detection.

## Root Cause
The `renderSegmentationMaskFast()` method had a bug where it was calling `raycast()` instead of `raycastWithEntities()`. This meant entities were being skipped during the raycasting phase.

## Fix Applied
Changed line in `SegmentationRenderer.java`:
```java
// BEFORE (BUG):
HitResult hit = raycast(world, cameraPos, rayDir, 100.0);

// AFTER (FIXED):
HitResult hit = raycastWithEntities(world, cameraPos, rayDir, 100.0);
```

## How It Works Now

### Segmentation Mask
1. **Raycasting includes entities**: For each pixel, the ray now checks:
   - First: Is there an entity along this ray?
   - Second: Is there a block along this ray?
   - Entity hits take priority over block hits if they're closer

2. **Entity colors**: Each entity type gets a unique color from `EntityClassMap`
   - Different from block colors to avoid collisions
   - Deterministic based on entity type ID

### Depth Map
The depth map **already includes entities** because:
- Entities render to the same depth buffer as blocks
- When entities are rendered, they write their depth values
- The depth buffer is captured AFTER entities render (`WorldRenderEvents.AFTER_ENTITIES`)

**So entities should already appear in the depth map!** If they don't, it might be because:
1. The entity is transparent/translucent and didn't write depth
2. The entity rendered after the capture (timing issue)
3. The entity is too small to see in the depth visualization

## Testing

To verify entities are now included:

1. **Build the mod**:
   ```powershell
   ./gradlew build
   ```

2. **Test in-game**:
   - Spawn some sheep/cows near the camera
   - Press F8 to capture a frame
   - Check the `screenshots/segmod/` folder

3. **Verify segmentation mask** (`*_seg.png`):
   - ✅ Entities should have unique colors (different from blocks)
   - ✅ Entity colors should be consistent per entity type
   - ✅ Entities should occlude blocks behind them

4. **Verify depth map** (`*_depth.png`):
   - ✅ Entities should appear as dark regions (if close) or light regions (if far)
   - ✅ Entity depth should match their actual distance
   - ✅ Entities should occlude blocks at the same depth

## Code Changes
- **Modified**: `SegmentationRenderer.java` - Fixed `renderSegmentationMaskFast()` to use `raycastWithEntities()`
- **No other changes needed** - Entity support was already implemented, just not being called!

## Performance Note
Entity raycasting adds some overhead because it needs to:
1. Check bounding boxes of nearby entities
2. Perform ray-box intersection tests
3. Compare distances to find closest hit

But this is necessary for accurate segmentation. The 4x4 sampling mode (`renderSegmentationMaskFast`) helps keep it fast.

## Alternative: High-Quality Mode
For pixel-perfect entity segmentation (no sampling), use:
```java
BufferedImage segMask = SegmentationRenderer.renderSegmentationMask(width, height);
```
This checks every pixel but is slower (several seconds per frame).
