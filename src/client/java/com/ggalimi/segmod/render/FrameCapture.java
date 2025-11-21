package com.ggalimi.segmod.render;

import com.ggalimi.segmod.util.DepthExtractor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Main frame capture system that generates three outputs:
 * 1. RGB color image - normal screenshot
 * 2. Segmentation mask - pixel-wise block type coloring
 * 3. Depth map - distance from camera (linear, normalized)
 */
public class FrameCapture {
    
    private static int frameCounter = 0;
    private static File outputDirectory = null;
    
    // Capture settings
    private static boolean autoCapture = false;
    private static int captureInterval = 150; // Capture every 150 ticks (1 second at 150 TPS)
    private static int tickCounter = 0;
    private static boolean captureRequested = false;
    private static int segmentationSampleRate = 2; // 1x1 = full res, 2x2 = half res, etc. (4x4 for speed)
    
    /**
     * Lazily initialize output directory.
     */
    public static File getOutputDirectory() {
        if (outputDirectory == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            File screenshotDir = new File(client.runDirectory, "screenshots");
            outputDirectory = new File(screenshotDir, "segmod");
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }
        }
        return outputDirectory;
    }
    
    /**
     * Request a capture on the next world render.
     */
    public static void requestCapture() {
        captureRequested = true;
    }
    
    /**
     * Enables or disables automatic frame capture.
     */
    public static void setAutoCapture(boolean enabled) {
        autoCapture = enabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (enabled) {
            client.inGameHud.getChatHud().addMessage(
                net.minecraft.text.Text.literal("§a[SegMod] Auto-capture enabled (every " + captureInterval + " ticks)")
            );
        } else {
            client.inGameHud.getChatHud().addMessage(
                net.minecraft.text.Text.literal("§c[SegMod] Auto-capture disabled")
            );
        }
    }
    
    /**
     * Toggles automatic frame capture on/off.
     */
    public static void toggleAutoCapture() {
        setAutoCapture(!autoCapture);
    }
    
    /**
     * Called every tick to handle automatic capture timing.
     */
    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (autoCapture && client != null && client.world != null && client.player != null) {
            tickCounter++;
            if (tickCounter >= captureInterval) {
                tickCounter = 0;
                requestCapture();
            }
        }
    }
    
    /**
     * Called after world entities are rendered (before HUD).
     */
    public static void onWorldRendered(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        if (!captureRequested) {
            return;
        }
        captureRequested = false;
        captureFrame(context);
    }
    
    /**
     * Main method to capture all three outputs: RGB, segmentation mask, and depth map.
     */
    private static void captureFrame(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        
        try {
            // We're called after world render but before HUD, so framebuffer has world-only content
            Framebuffer mainFramebuffer = client.getFramebuffer();
            int width = mainFramebuffer.textureWidth;
            int height = mainFramebuffer.textureHeight;
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
            String frameId = String.format("%s_frame%04d", timestamp, frameCounter);
            
            // === 1. CAPTURE RGB COLOR IMAGE (world-only, no HUD) ===
            captureRGBImage(mainFramebuffer, width, height, frameId);
            
            // === 3. CAPTURE DEPTH MAP ===
            captureDepthMap(mainFramebuffer, width, height, frameId);
            
            // === 2. CAPTURE SEGMENTATION MASK ===
            captureSegmentationMask(width, height, frameId, context);
            
            frameCounter++;
            
            // Notify user
            client.inGameHud.getChatHud().addMessage(
                net.minecraft.text.Text.literal("§b[SegMod] Captured frame " + frameCounter + " → " + frameId)
            );
            
        } catch (Exception e) {
            e.printStackTrace();
            client.inGameHud.getChatHud().addMessage(
                net.minecraft.text.Text.literal("§c[SegMod] Error capturing frame: " + e.getMessage())
            );
        }
    }
    
    /**
     * === PART 1: RGB COLOR IMAGE ===
     * Captures the current screen as a normal screenshot.
     */
    private static void captureRGBImage(Framebuffer framebuffer, int width, int height, String frameId) throws IOException {
        // Read pixels from the main framebuffer
        ByteBuffer buffer = readFramebufferPixels(framebuffer, width, height);
        
        // Convert to BufferedImage
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + y * width) * 3;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                
                // Flip vertically (OpenGL coordinates)
                image.setRGB(x, height - 1 - y, (r << 16) | (g << 8) | b);
            }
        }
        
        // Save RGB image
        File outputFile = new File(getOutputDirectory(), frameId + "_rgb.png");
        ImageIO.write(image, "PNG", outputFile);
    }
    
    /**
     * === PART 2: SEGMENTATION MASK ===
     * Renders the scene with each block type and entity colored by its unique deterministic color.
     * Uses GPU framebuffer rendering with custom shaders for maximum performance and accuracy.
     * Includes all entities (mobs, players, dropped items, etc.) with actual model geometry.
     */
    private static void captureSegmentationMask(int width, int height, String frameId, 
                                                net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) throws IOException {
        // Use GpuSegmentationRenderer with shader-based rendering
        long startTime = System.currentTimeMillis();
        BufferedImage segMask = GpuSegmentationRenderer.renderSegmentationMask(context);
        long elapsed = System.currentTimeMillis() - startTime;
        
        System.out.println("[SegMod] Segmentation rendered in " + elapsed + "ms using GPU");
        
        // Save segmentation mask
        File outputFile = new File(getOutputDirectory(), frameId + "_seg.png");
        ImageIO.write(segMask, "PNG", outputFile);
    }
    
    /**
     * === PART 3: DEPTH MAP ===
     * Extracts depth information from the depth buffer and normalizes to linear space.
     * Near objects = black (0), far objects = white (255).
     */
    private static void captureDepthMap(Framebuffer framebuffer, int width, int height, String frameId) throws IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        // Get camera parameters
        float nearPlane = 0.05f; // Minecraft's near plane
        float farPlane = client.options.getViewDistance().getValue() * 16.0f; // Render distance in blocks
        
        // Read depth buffer directly
        float[] rawDepth = DepthExtractor.extractRawDepth(framebuffer, width, height);
        
        // Debug: Check raw depth statistics
        float minDepth = Float.MAX_VALUE;
        float maxDepth = Float.MIN_VALUE;
        int whitePixels = 0;
        int blackPixels = 0;
        int midPixels = 0;
        for (float d : rawDepth) {
            if (d < minDepth) minDepth = d;
            if (d > maxDepth) maxDepth = d;
            if (d > 0.999f) whitePixels++;
            else if (d < 0.001f) blackPixels++;
            else midPixels++;
        }
        System.out.println("[SegMod Depth] Min: " + minDepth + ", Max: " + maxDepth + 
                         ", White: " + whitePixels + ", Black: " + blackPixels + 
                         ", Mid: " + midPixels + "/" + rawDepth.length);
        
        if (midPixels > 0) {
            System.out.println("[SegMod] Depth data available: " + midPixels + " pixels with valid depth");
        }
        
        // Apply contrast enhancement to make details more visible
        float[] enhancedDepth = DepthExtractor.enhanceDepthContrast(rawDepth, 0.3f);
        
        // Convert to grayscale
        byte[] grayscaleData = DepthExtractor.depthToGrayscale(enhancedDepth);
        
        // Create BufferedImage from grayscale data
        BufferedImage depthImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + y * width) * 3;
                int r = grayscaleData[i] & 0xFF;
                int g = grayscaleData[i + 1] & 0xFF;
                int b = grayscaleData[i + 2] & 0xFF;
                
                // Flip vertically (OpenGL coordinates)
                depthImage.setRGB(x, height - 1 - y, (r << 16) | (g << 8) | b);
            }
        }
        
        // Save depth map
        // File outputFile = new File(outputDirectory, frameId + "_depth.png");
        // ImageIO.write(depthImage, "PNG", outputFile);
        
        // Also save linearized depth for comparison
        saveLinearizedDepth(framebuffer, width, height, frameId, nearPlane, farPlane);
    }
    
    /**
     * Helper method to save linearized depth for debugging.
     */
    @SuppressWarnings("unused")
    private static void saveLinearizedDepth(Framebuffer framebuffer, int width, int height, 
                                           String frameId, float nearPlane, float farPlane) throws IOException {
        float[] linearDepth = DepthExtractor.extractLinearDepth(framebuffer, width, height, nearPlane, farPlane);
        byte[] linearGrayscale = DepthExtractor.depthToGrayscale(linearDepth);
        
        BufferedImage linearImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + y * width) * 3;
                int r = linearGrayscale[i] & 0xFF;
                int g = linearGrayscale[i + 1] & 0xFF;
                int b = linearGrayscale[i + 2] & 0xFF;
                linearImage.setRGB(x, height - 1 - y, (r << 16) | (g << 8) | b);
            }
        }
        
        File outputFile = new File(getOutputDirectory(), frameId + "_depth.png");
        ImageIO.write(linearImage, "PNG", outputFile);
    }
    
    /**
     * Reads pixel data from a framebuffer.
     */
    private static ByteBuffer readFramebufferPixels(Framebuffer framebuffer, int width, int height) {
        RenderSystem.assertOnRenderThread();
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3);
        
        framebuffer.beginRead();
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buffer);
        framebuffer.endRead();
        
        return buffer;
    }
    
    /**
     * Sets the capture interval in ticks.
     */
    public static void setCaptureInterval(int ticks) {
        captureInterval = Math.max(1, ticks);
    }
    

}
