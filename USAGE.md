# Segmentation Mod - Usage Guide

## Quick Start

1. Load Minecraft 1.21.1 with Fabric Loader and Fabric API
2. Place the mod JAR in `.minecraft/mods/`
3. Launch the game
4. Press **F9** to enable automatic capture
5. Play the game - frames will be captured every second
6. Find your data in `.minecraft/screenshots/segmod/`

## Understanding the Outputs

### RGB Images (`*_rgb.png`)
These are standard screenshots showing exactly what you see in-game, including:
- Textures and colors
- Lighting and shadows
- Particles and effects
- Sky and weather

**Use cases:**
- Training image-to-image models
- Visual reference for other outputs
- Quality assurance

### Segmentation Masks (`*_seg.png`)
Pixel-wise classification where each block type has a unique color:

```
Stone: RGB(123, 45, 67)
Grass: RGB(89, 234, 12)
Water: RGB(45, 167, 223)
Sky/Air: RGB(0, 0, 0)
```

**Important notes:**
- Colors are deterministic (same block = same color always)
- Generated via hash of block registry ID
- No two block types will have the same color
- Sky/air/void is always black

**Use cases:**
- Training semantic segmentation models
- Block type classification
- Scene understanding
- Material recognition

### Depth Maps (`*_depth.png`)
Grayscale images representing distance from camera:

```
Black (0)     = Near plane (0.05 blocks)
Gray (128)    = Middle distance
White (255)   = Far plane (render distance)
```

**Important notes:**
- Depth is **linear** in world space (not exponential)
- Normalized to render distance
- Sky is typically max depth (white)
- Formula accounts for perspective projection

**Use cases:**
- Training depth estimation models
- 3D reconstruction
- Distance-based segmentation
- Monocular depth prediction

## Advanced Configuration

### Changing Capture Interval

Edit `FrameCapture.java`:
```java
private static int captureInterval = 20; // Change this value
```
- 20 ticks = 1 second (default)
- 10 ticks = 0.5 seconds (faster)
- 40 ticks = 2 seconds (slower)

### Improving Segmentation Quality

In `FrameCapture.java`, change:
```java
// Fast mode (4x4 sampling) - default
BufferedImage segMask = SegmentationRenderer.renderSegmentationMaskFast(width, height, 4);

// High quality mode (per-pixel) - slower but more accurate
BufferedImage segMask = SegmentationRenderer.renderSegmentationMask(width, height);

// Medium quality (2x2 sampling)
BufferedImage segMask = SegmentationRenderer.renderSegmentationMaskFast(width, height, 2);
```

### Custom Block Colors

If you want specific colors for certain blocks, edit `BlockClassMap.java`:

```java
public static int[] getBlockColor(Block block) {
    // Custom colors for specific blocks
    if (block == Blocks.GRASS_BLOCK) return new int[]{0, 255, 0}; // Green
    if (block == Blocks.STONE) return new int[]{128, 128, 128}; // Gray
    if (block == Blocks.WATER) return new int[]{0, 0, 255}; // Blue
    
    // Fall back to automatic color generation
    // ... existing code ...
}
```

## Data Collection Tips

### For Machine Learning Datasets

1. **Diversity**: Explore different biomes, structures, and times of day
2. **Movement**: Walk, fly, and look around to capture various angles
3. **Scale**: Capture both close-up and distant views
4. **Weather**: Capture in rain, clear, and night conditions
5. **Structures**: Include villages, caves, mountains, oceans

### Recommended Settings

For best results:
- Set render distance to 12-16 chunks for consistent depth range
- Disable motion blur if present in shaders
- Use consistent FOV across captures
- Consider disabling dynamic lighting for consistency

### Performance Optimization

If experiencing lag:
1. Reduce capture interval (capture less frequently)
2. Use fast segmentation mode with larger sample rate (4 or 8)
3. Lower render distance
4. Close other applications

## File Organization

Example output structure:
```
screenshots/segmod/
├── 2025-11-15_14.30.45_frame0001_rgb.png
├── 2025-11-15_14.30.45_frame0001_seg.png
├── 2025-11-15_14.30.45_frame0001_depth.png
├── 2025-11-15_14.30.46_frame0002_rgb.png
├── 2025-11-15_14.30.46_frame0002_seg.png
├── 2025-11-15_14.30.46_frame0002_depth.png
└── ...
```

### Processing the Data

Python example for loading:
```python
import cv2
import numpy as np
from pathlib import Path

# Load a frame
frame_id = "2025-11-15_14.30.45_frame0001"
rgb = cv2.imread(f"screenshots/segmod/{frame_id}_rgb.png")
seg = cv2.imread(f"screenshots/segmod/{frame_id}_seg.png")
depth = cv2.imread(f"screenshots/segmod/{frame_id}_depth.png", cv2.IMREAD_GRAYSCALE)

# Convert depth to actual distance (if render distance = 16 chunks = 256 blocks)
depth_normalized = depth / 255.0  # [0, 1]
depth_blocks = depth_normalized * 256.0  # [0, 256] blocks

# Extract unique block colors from segmentation
unique_colors = np.unique(seg.reshape(-1, 3), axis=0)
print(f"Found {len(unique_colors)} unique block types")
```

## Troubleshooting

### No images are being saved
- Check that you pressed F9 to enable auto-capture
- Verify you're in a world (not main menu)
- Check `.minecraft/screenshots/segmod/` directory exists
- Look for error messages in chat or console

### Segmentation mask is all black
- This means the scene is mostly sky/air
- Try looking at terrain or blocks
- Verify BlockClassMap is working correctly

### Depth map looks wrong
- Check your render distance setting
- Verify near/far plane values in DepthExtractor
- Make sure you're using the correct OpenGL context

### Performance is poor
- Reduce capture interval
- Use faster segmentation mode
- Lower Minecraft graphics settings
- Reduce render distance

## API for Developers

### Programmatic Frame Capture

```java
import com.ggalimi.segmod.render.FrameCapture;

// Capture a single frame
FrameCapture.captureFrame();

// Enable/disable auto-capture
FrameCapture.setAutoCapture(true);

// Change capture interval
FrameCapture.setCaptureInterval(40); // Capture every 2 seconds

// Get output directory
File outputDir = FrameCapture.getOutputDirectory();
```

### Custom Segmentation Rendering

```java
import com.ggalimi.segmod.render.SegmentationRenderer;

// Render high-quality segmentation mask
BufferedImage mask = SegmentationRenderer.renderSegmentationMask(width, height);

// Render fast segmentation mask with 8x8 sampling
BufferedImage fastMask = SegmentationRenderer.renderSegmentationMaskFast(width, height, 8);
```

### Block Color Mapping

```java
import com.ggalimi.segmod.util.BlockClassMap;
import net.minecraft.block.Blocks;

// Get RGB color for a block
int[] color = BlockClassMap.getBlockColor(Blocks.STONE);
// Returns: [r, g, b] where each value is 0-255

// Get packed color (0xRRGGBB)
int packedColor = BlockClassMap.getBlockColorPacked(Blocks.GRASS_BLOCK);
```

### Depth Extraction

```java
import com.ggalimi.segmod.util.DepthExtractor;

// Extract linear depth
float[] linearDepth = DepthExtractor.extractLinearDepth(width, height, nearPlane, farPlane);

// Convert to grayscale image data
byte[] grayscale = DepthExtractor.depthToGrayscale(linearDepth);
```

## Contact & Support

For issues, questions, or contributions, please visit the GitHub repository.
