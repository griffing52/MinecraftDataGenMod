# Minecraft Segmentation & Depth Capture Mod

A client-side Minecraft Fabric mod for generating computer vision training data. This mod automatically captures three synchronized outputs during gameplay:

1. **RGB Color Images** - Normal screenshots of the current view
2. **Segmentation Masks** - Pixel-wise images where each block type is mapped to a unique deterministic color
3. **Depth Maps** - Grayscale images representing normalized linear distance from camera (near = black, far = white)

## Features

- ✅ **Client-side only** - No server-side installation required
- ✅ **Automatic frame capture** - Continuously captures frames while playing
- ✅ **Manual capture mode** - Capture single frames on demand
- ✅ **Entity segmentation** - Players, mobs, and items are included with unique colors
- ✅ **Deterministic block colors** - Each block type gets a unique, consistent RGB color for segmentation
- ✅ **Deterministic entity colors** - Each entity type gets a unique, consistent RGB color for segmentation
- ✅ **Linear depth normalization** - Depth maps use linear space for accurate distance representation
- ✅ **Synchronized outputs** - All three images captured from the same frame

## Requirements

- **Minecraft**: 1.21.1
- **Fabric Loader**: 0.17.3+
- **Fabric API**: 0.102.0+
- **Yarn Mappings**: 1.21.1+build.1
- **Java**: 21+

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) version 0.102.0+
3. Build this mod: `./gradlew build`
4. Place the built JAR from `build/libs/` and Fabric API into your `.minecraft/mods` folder
5. Launch Minecraft with the Fabric profile

## Usage

### Keybindings

- **F8** - Capture a single frame (RGB + Segmentation + Depth)
- **F9** - Toggle automatic frame capture on/off

### Output Location

All captured frames are saved to:
```
.minecraft/screenshots/segmod/
```

Each frame produces three files:
- `{timestamp}_frame{number}_rgb.png` - RGB color image
- `{timestamp}_frame{number}_seg.png` - Segmentation mask
- `{timestamp}_frame{number}_depth.png` - Depth map

### Automatic Capture

When automatic capture is enabled (press F9):
- Frames are captured every 20 ticks (1 second at normal game speed)
- Capture continues until you toggle it off
- Great for generating large datasets during gameplay

## Technical Details

### RGB Color Images
Standard screenshots captured from the main framebuffer showing exactly what you see in-game.

### Segmentation Masks
Each block type and entity type is assigned a unique RGB color based on its registry ID:
- Colors are **deterministic** - same block/entity type always gets the same color
- Uses hash-based color generation to ensure color uniqueness
- **Entities** (players, mobs, items) are included and take priority over blocks
- Entity colors use a different algorithm than blocks to minimize collision risk
- Sky/air is rendered as black (0, 0, 0)
- Implemented using ray-casting through each pixel

### Depth Maps
Extracted from OpenGL depth buffer and normalized to linear space:
- **Near plane** (0.05 blocks) → Black (0)
- **Far plane** (render distance) → White (255)
- Non-linear depth buffer values are converted to linear world-space depth
- Formula: `linearDepth = (2 * near * far) / (far + near - ndc * (far - near))`

### Performance

The mod uses a fast segmentation renderer with configurable sampling:
- Default: 4x4 pixel sampling for balanced quality/performance
- For highest quality: Use `renderSegmentationMask()` instead of `renderSegmentationMaskFast()`
- Depth extraction is efficient using GPU depth buffer readback

## Code Structure

```
src/client/java/com/ggalimi/segmod/
├── SegmentationModCVClient.java      # Main client initialization
├── render/
│   ├── FrameCapture.java             # Core capture system (RGB + Depth)
│   └── SegmentationRenderer.java     # Segmentation mask generation
└── util/
    ├── BlockClassMap.java            # Block → Color mapping
    ├── EntityClassMap.java           # Entity → Color mapping
    └── DepthExtractor.java           # Depth buffer processing
```

### Key Components

**FrameCapture.java**
- Coordinates all three capture types
- Manages automatic capture timing
- Handles file I/O and naming

**SegmentationRenderer.java**
- Ray-casts through each pixel to determine block type
- Colors blocks using deterministic RGB mapping
- Offers both high-quality and fast rendering modes

**DepthExtractor.java**
- Reads OpenGL depth buffer
- Converts non-linear depth to linear space
- Normalizes depth to [0, 1] range
- Outputs grayscale images

**BlockClassMap.java**
- Maps blocks to unique RGB colors
- Uses block registry ID hashing for determinism
- Caches colors for performance

**EntityClassMap.java**
- Maps entities to unique RGB colors
- Uses different hashing algorithm than blocks to avoid collisions
- Includes all entity types: mobs, players, items, projectiles, etc.
- Caches colors for performance

## Building from Source

```bash
# Clone the repository
git clone <repo-url>
cd MinecraftDataGenMod

# Build the mod
./gradlew build

# Output JAR will be in build/libs/
```

## License

See LICENSE file for details.

## Contributing

Contributions welcome! Areas for enhancement:
- GPU-accelerated segmentation using custom shaders
- Semantic segmentation classes (e.g., "natural", "artificial", "liquid")
- Instance segmentation for individual entities (same entity type, different IDs)
- Entity pose/skeleton information
- Normal map generation
- Optical flow between frames 
