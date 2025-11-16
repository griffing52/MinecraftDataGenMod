package com.ggalimi.segmod.render;

import com.ggalimi.segmod.util.BlockClassMap;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.image.BufferedImage;

/**
 * Renders the world with blocks colored by their segmentation class.
 * This creates the segmentation mask where each block type has a unique deterministic color.
 */
public class SegmentationRenderer {
    
    private static final MinecraftClient client = MinecraftClient.getInstance();
    
    /**
     * Renders a segmentation mask by ray-casting through each pixel and coloring by block type.
     * This is a CPU-based approach suitable for data generation.
     * 
     * For better performance, consider implementing a custom shader-based approach.
     * 
     * @param width Width of the output image
     * @param height Height of the output image
     * @return BufferedImage containing the segmentation mask
     */
    public static BufferedImage renderSegmentationMask(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return image;
        }
        
        // Get camera information
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        
        // Raycast for each pixel
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Get ray direction from camera for this pixel
                Vec3d rayDir = getRayFromCamera(x, y, width, height);
                
                // Perform raycast
                HitResult hit = raycast(world, cameraPos, rayDir, 100.0);
                
                int[] color;
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockState state = world.getBlockState(blockHit.getBlockPos());
                    Block block = state.getBlock();
                    color = BlockClassMap.getBlockColor(block);
                } else {
                    // Sky or miss - use black or a specific sky color
                    color = new int[]{0, 0, 0};
                }
                
                int rgb = (color[0] << 16) | (color[1] << 8) | color[2];
                image.setRGB(x, y, rgb);
            }
            
            // Progress indicator for large renders
            if (y % 50 == 0 && y > 0) {
                System.out.println("Segmentation render progress: " + (y * 100 / height) + "%");
            }
        }
        
        return image;
    }
    
    /**
     * Gets a ray from the camera through a specific pixel on screen.
     * Uses Minecraft's camera directly to match the rendered view exactly.
     */
    private static Vec3d getRayFromCamera(int pixelX, int pixelY, int screenWidth, int screenHeight) {
        // Use Minecraft's built-in method to get the ray for a pixel
        // This ensures FOV and projection match exactly
        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        
        // Convert pixel coordinates to normalized device coordinates [-1, 1]
        // Negate X to fix horizontal flip
        float x = 1.0f - (2.0f * pixelX) / screenWidth;
        float y = 1.0f - (2.0f * pixelY) / screenHeight;
        
        // Get FOV - use base FOV value (dynamic effects are minimal)
        double fov = client.options.getFov().getValue();
        double fovScale = Math.tan(Math.toRadians(fov / 2.0));
        double aspectRatio = (double) screenWidth / screenHeight;
        
        // Calculate view-space offsets
        double viewX = x * fovScale * aspectRatio;
        double viewY = y * fovScale;
        
        // Get camera rotation vectors
        org.joml.Vector3f forwardVec = camera.getHorizontalPlane();
        org.joml.Vector3f upVec = camera.getVerticalPlane();
        Vec3d forward = new Vec3d(forwardVec.x, forwardVec.y, forwardVec.z);
        Vec3d up = new Vec3d(upVec.x, upVec.y, upVec.z);
        Vec3d right = up.crossProduct(forward).normalize();
        
        // Build ray direction
        Vec3d rayDir = forward.add(right.multiply(viewX)).add(up.multiply(viewY));
        return rayDir.normalize();
    }
    
    /**
     * Rotates a vector around the X axis (pitch).
     */
    private static Vec3d rotateByPitch(Vec3d vec, float pitch) {
        float rad = (float) Math.toRadians(pitch);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        
        return new Vec3d(
            vec.x,
            vec.y * cos - vec.z * sin,
            vec.y * sin + vec.z * cos
        );
    }
    
    /**
     * Rotates a vector around the Y axis (yaw).
     */
    private static Vec3d rotateByYaw(Vec3d vec, float yaw) {
        float rad = (float) Math.toRadians(yaw);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        
        return new Vec3d(
            vec.x * cos + vec.z * sin,
            vec.y,
            -vec.x * sin + vec.z * cos
        );
    }
    
    /**
     * Performs a raycast in the world.
     */
    private static HitResult raycast(ClientWorld world, Vec3d origin, Vec3d direction, double maxDistance) {
        Vec3d end = origin.add(direction.multiply(maxDistance));
        return world.raycast(new net.minecraft.world.RaycastContext(
            origin,
            end,
            net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            client.player
        ));
    }
    
    /**
     * Alternative fast segmentation mask using block sampling (lower quality but faster).
     * Samples blocks at regular intervals instead of per-pixel raycasting.
     */
    public static BufferedImage renderSegmentationMaskFast(int width, int height, int sampleRate) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return image;
        }
        
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        
        // Sample at lower resolution and upscale
        for (int y = 0; y < height; y += sampleRate) {
            for (int x = 0; x < width; x += sampleRate) {
                Vec3d rayDir = getRayFromCamera(x, y, width, height);
                HitResult hit = raycast(world, cameraPos, rayDir, 100.0);
                
                int[] color;
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockState state = world.getBlockState(blockHit.getBlockPos());
                    Block block = state.getBlock();
                    color = BlockClassMap.getBlockColor(block);
                } else {
                    color = new int[]{0, 0, 0};
                }
                
                int rgb = (color[0] << 16) | (color[1] << 8) | color[2];
                
                // Fill block of pixels
                for (int dy = 0; dy < sampleRate && y + dy < height; dy++) {
                    for (int dx = 0; dx < sampleRate && x + dx < width; dx++) {
                        image.setRGB(x + dx, y + dy, rgb);
                    }
                }
            }
        }
        
        return image;
    }
}
