# GPU Segmentation Rendering Implementation

This document explains the technical implementation of the GPU-accelerated segmentation rendering pipeline used in this mod.

## Overview

The core idea is to re-render the visible world into a separate framebuffer using custom shaders. Instead of outputting "visual" colors (textures, lighting, shadows), we output "semantic" colors (unique IDs for each block/entity type).

This approach is significantly faster than CPU-based raytracing or block iteration because it leverages the GPU's rasterization hardware, which is optimized for exactly this task.

## Output Formats & Assumptions

The mod generates five synchronized image files for each captured frame. Below are the details for processing each format.

### 1. RGB Image (`_rgb.png`)
*   **Format**: Standard 24-bit RGB PNG.
*   **Content**: The visual game world as seen by the player.
*   **Usage**: Input image for the neural network.

### 2. Semantic Segmentation (`_seg.png`)
*   **Format**: 24-bit RGB PNG (Lossless).
*   **Content**: Pixel-wise classification of object types (e.g., "Grass Block", "Zombie", "Sky").
*   **Encoding**: 
    *   The **Class ID** is encoded directly into the RGB channels.
    *   `ID = (R << 16) | (G << 8) | B`
    *   Example: ID 1 (Stone) = RGB(0, 0, 1). ID 255 = RGB(0, 0, 255).
    *   **Note**: Images will appear as "shades of blue" because most IDs are < 255.
*   **Processing**: Read the image as a raw byte array. Combine the 3 bytes to recover the integer Class ID.

### 3. Instance Segmentation (`_instance.png`)
*   **Format**: 24-bit RGB PNG (Lossless).
*   **Content**: Pixel-wise identification of unique object instances.
*   **Encoding**:
    *   The **Entity ID** is encoded directly into the RGB channels.
    *   `ID = (R << 16) | (G << 8) | B`
    *   Each specific entity (e.g., "Zombie #42") has a unique ID that persists across frames.
    *   Blocks generally share a common ID (0 or 1) unless specifically tracked as tile entities.
*   **Processing**: Same as Semantic Segmentation. Recover the integer Instance ID from RGB.

### 4. Optical Flow (`_flow.png`)
*   **Format**: 24-bit RGB PNG.
*   **Content**: Screen-space motion vectors (velocity) for each pixel.
*   **Encoding**:
    *   **Red Channel**: X-axis motion (Horizontal).
    *   **Green Channel**: Y-axis motion (Vertical).
    *   **Blue Channel**: Unused (0).
    *   **Zero Motion**: RGB(127, 127, 0) (approx. 0.5, 0.5 in float).
    *   **Formula**: `PixelValue = (Velocity * 5.0 + 0.5) * 255`
    *   **Decoding**: `Velocity = ((PixelValue / 255.0) - 0.5) / 5.0`
*   **Visuals**:
    *   **Mustard Yellow**: No motion.
    *   **Red/Green Gradients**: Movement.

### 5. Depth Map (`_depth.png`)
*   **Format**: 24-bit RGB PNG (Grayscale).
*   **Content**: Linear distance from the camera plane.
*   **Encoding**:
    *   **Grayscale**: R=G=B.
    *   **Range**: Normalized linear depth [0.0, 1.0].
    *   **0 (Black)**: Near Plane (0.05 blocks).
    *   **255 (White)**: Far Plane (Render Distance).
*   **Processing**: `Depth = PixelValue / 255.0 * (Far - Near) + Near`.

## The Pipeline

1.  **Initialization**: `GpuSegmentationRenderer.renderSegmentationMask()` is called.
2.  **Framebuffer Setup**: A custom `Framebuffer` is bound. The depth buffer is cleared to ensure correct occlusion.
3.  **Render Pass**: The world is re-rendered.
    *   **Blocks**: Rendered using the "Mega Optimization" (see below).
    *   **Entities**: Rendered manually by iterating the entity list.
4.  **Readback**: `glReadPixels` is used to transfer the final image from the GPU to a CPU `BufferedImage`.

## Key Technologies

### 1. The "Mega Optimization" (Chunk Re-use)
Standard rendering involves complex "Frustum Culling" to decide which chunks are visible. Doing this twice (once for the game, once for us) is wasteful.

*   **Concept**: We reuse the visibility calculations performed by the vanilla `WorldRenderer`.
*   **Implementation**: 
    *   We use a Mixin (`WorldRendererAccessor`) to access the private `chunkInfos` list.
    *   This list contains every chunk the player can currently see.
*   **Dynamic Reflection**: The `ChunkInfo` class is internal and obfuscated. To make the mod robust across versions:
    *   We inspect the `ChunkInfo` object at runtime.
    *   We scan its fields to find the `BuiltChunk` object (which contains the actual render data).
    *   This allows the mod to work even if field names change (e.g., `field_1234` vs `field_5678`).

### 2. Vertex Consumer Interception
To change *what* is drawn without rewriting the rendering engine, we wrap the `VertexConsumer` interface.

*   **`SegmentationVertexConsumerProvider`**: Acts as a factory for vertex consumers.
    *   **Layer Filtering**: It analyzes the `RenderLayer` string.
        *   **"shadow"**: Returns `NoOpVertexConsumer`. This completely discards shadow rendering, fixing "black artifact" noise.
        *   **"entity"**: Forces the use of `SegmentationRenderLayer.getEntityLayer()`.
        *   **"solid/cutout"**: Forces the use of `SegmentationRenderLayer.getLayer()` (for blocks).
*   **`SegmentationVertexConsumer`**: The actual buffer writer.
    *   **Color Injection**: It overrides the `color()` method. Instead of writing the texture's color, it writes the **Segmentation Color** of the current object (retrieved via `GpuSegmentationRenderer.getCurrentBlockColor()` or `getCurrentEntityColor()`).

### 3. Custom Shaders
We use two specialized shaders to handle different object types:

#### A. Block Shader (`segmod_segmentation`)
*   **Purpose**: Renders blocks like grass, stone, and leaves.
*   **Behavior**: 
    *   Samples the **Block Texture Atlas**.
    *   Performs **Alpha Testing**: If a pixel is transparent (e.g., between leaves or in glass), it `discard`s the fragment.
    *   **Why?**: Without this, leaves would render as solid black squares because the segmentation color would fill the transparent areas.

#### B. Entity Shader (`segmod_segmentation_entity`)
*   **Purpose**: Renders mobs, players, and items.
*   **Behavior**:
    *   **Ignores Textures**: Entities use complex UV maps that don't match the block atlas.
    *   **Solid Color**: It simply outputs the color passed from the vertex attributes.
    *   **Why?**: Using the block shader on entities caused "erosion" (holes) because the shader was sampling transparent void space in the block atlas.

## Artifact Solutions

| Issue | Cause | Fix |
|-------|-------|-----|
| **Black Noise on Blocks** | Shadow rendering (which draws dark transparent quads) was being captured by the segmentation. | **Shadow Suppression**: We detect "shadow" layers and route them to `NoOpVertexConsumer`, effectively deleting them. |
| **Eroded/Holey Entities** | Entities were being rendered with the Block Shader, which discarded pixels based on incorrect texture sampling. | **Layer Logic**: We updated `SegmentationVertexConsumerProvider` to explicitly detect "entity" layers and force the non-textured Entity Shader. |
| **Flickering Entities** | Z-fighting between front and back faces when culling was disabled. | **Enable Culling**: We re-enabled back-face culling in `SegmentationRenderLayer` to ensure entities look solid. |

## Fallback Mechanism
If the "Mega Optimization" fails (e.g., reflection cannot find the chunk field), the system automatically falls back to a **Radius-Based Renderer**.
*   It manually iterates chunks around the player.
*   It performs its own frustum culling.
*   This is slower but ensures the mod never crashes or shows a black screen due to optimization failures.
