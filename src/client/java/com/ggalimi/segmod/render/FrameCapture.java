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
    
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static int frameCounter = 0;
    private static File outputDirectory;
    
    // Capture settings
    private static boolean autoCapture = false;
    private static int captureInterval = 20; // Capture every 20 ticks (1 second at 20 TPS)
    private static int tickCounter = 0;
    
    static {
        // Initialize output directory
        File screenshotDir = new File(client.runDirectory, "screenshots");
        outputDirectory = new File(screenshotDir, "segmod");
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
    }
    
    /**
     * Enables or disables automatic frame capture.
     */
    public static void setAutoCapture(boolean enabled) {
        autoCapture = enabled;
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
     * Called every tick to handle automatic capture.
     */
    public static void tick() {
        if (autoCapture && client.world != null && client.player != null) {
            tickCounter++;
            if (tickCounter >= captureInterval) {
                tickCounter = 0;
                captureFrame();
            }
        }
    }
    
    /**
     * Main method to capture all three outputs: RGB, segmentation mask, and depth map.
     */
    public static void captureFrame() {
        if (client.world == null || client.player == null) {
            return;
        }
        
        try {
            Framebuffer mainFramebuffer = client.getFramebuffer();
            int width = mainFramebuffer.textureWidth;
            int height = mainFramebuffer.textureHeight;
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
            String frameId = String.format("%s_frame%04d", timestamp, frameCounter);
            
            // === 1. CAPTURE RGB COLOR IMAGE (normal screenshot) ===
            captureRGBImage(mainFramebuffer, width, height, frameId);
            
            // === 2. CAPTURE SEGMENTATION MASK ===
            captureSegmentationMask(width, height, frameId);
            
            // === 3. CAPTURE DEPTH MAP ===
            captureDepthMap(width, height, frameId);
            
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
        File outputFile = new File(outputDirectory, frameId + "_rgb.png");
        ImageIO.write(image, "PNG", outputFile);
    }
    
    /**
     * === PART 2: SEGMENTATION MASK ===
     * Renders the scene with each block type colored by its unique deterministic color.
     * Uses ray-casting to determine the block type at each pixel and colors it accordingly.
     */
    private static void captureSegmentationMask(int width, int height, String frameId) throws IOException {
        // Use SegmentationRenderer to create the mask
        // Using fast mode with 4x4 sampling for better performance
        // For highest quality, use renderSegmentationMask(width, height) instead
        BufferedImage segMask = SegmentationRenderer.renderSegmentationMaskFast(width, height, 4);
        
        // Save segmentation mask
        File outputFile = new File(outputDirectory, frameId + "_seg.png");
        ImageIO.write(segMask, "PNG", outputFile);
    }
    
    /**
     * === PART 3: DEPTH MAP ===
     * Extracts depth information from the depth buffer and normalizes to linear space.
     * Near objects = black (0), far objects = white (255).
     */
    private static void captureDepthMap(int width, int height, String frameId) throws IOException {
        // Get camera parameters
        float nearPlane = 0.05f; // Minecraft's near plane
        float farPlane = client.options.getViewDistance().getValue() * 16.0f; // Render distance in blocks
        
        // Extract linear depth from OpenGL depth buffer
        float[] linearDepth = DepthExtractor.extractLinearDepth(width, height, nearPlane, farPlane);
        
        // Convert to grayscale image
        byte[] grayscaleData = DepthExtractor.depthToGrayscale(linearDepth);
        
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
        File outputFile = new File(outputDirectory, frameId + "_depth.png");
        ImageIO.write(depthImage, "PNG", outputFile);
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
    
    /**
     * Gets the output directory.
     */
    public static File getOutputDirectory() {
        return outputDirectory;
    }
}
