package com.ggalimi.segmod.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * Utility class for extracting and normalizing depth information from the OpenGL depth buffer.
 * Depth values are normalized to linear space where near = 0.0 (black) and far = 1.0 (white).
 */
public class DepthExtractor {
    
    /**
     * Extracts the depth buffer from the given framebuffer and converts it to linear depth.
     * 
     * @param framebuffer The framebuffer to read depth from
     * @param width The width of the framebuffer
     * @param height The height of the framebuffer
     * @param nearPlane The near clipping plane distance (typically 0.05f in Minecraft)
     * @param farPlane The far clipping plane distance (render distance)
     * @return A float array containing linear depth values from 0.0 (near) to 1.0 (far)
     */
    public static float[] extractLinearDepth(Framebuffer framebuffer, int width, int height, float nearPlane, float farPlane) {
        // Read the depth buffer from OpenGL
        FloatBuffer depthBuffer = BufferUtils.createFloatBuffer(width * height);
        
        // Ensure we're reading from the correct buffer
        RenderSystem.assertOnRenderThread();
        
        // Bind the framebuffer to read from it
        framebuffer.beginRead();
        GL11.glReadPixels(0, 0, width, height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depthBuffer);
        framebuffer.endRead();
        
        float[] depthData = new float[width * height];
        depthBuffer.get(depthData);
        
        // Debug: Check if we're getting valid depth values
        float minDepth = Float.MAX_VALUE;
        float maxDepth = Float.MIN_VALUE;
        for (float d : depthData) {
            if (d < minDepth) minDepth = d;
            if (d > maxDepth) maxDepth = d;
        }
        System.out.println("[SegMod Debug] Raw depth range: " + minDepth + " to " + maxDepth);
        
        // Convert from non-linear depth buffer values to linear depth
        for (int i = 0; i < depthData.length; i++) {
            depthData[i] = linearizeDepth(depthData[i], nearPlane, farPlane);
        }
        
        // Debug: Check linearized depth values
        minDepth = Float.MAX_VALUE;
        maxDepth = Float.MIN_VALUE;
        for (float d : depthData) {
            if (d < minDepth) minDepth = d;
            if (d > maxDepth) maxDepth = d;
        }
        System.out.println("[SegMod Debug] Linear depth range: " + minDepth + " to " + maxDepth);
        System.out.println("[SegMod Debug] Near: " + nearPlane + ", Far: " + farPlane);
        
        return depthData;
    }
    
    /**
     * Converts a non-linear depth buffer value to linear depth in [0, 1] range.
     * 
     * The depth buffer stores values in a non-linear fashion (more precision near the camera).
     * This function converts them back to linear world-space depth.
     * 
     * @param depthBufferValue The raw depth value from the depth buffer [0, 1]
     * @param near The near clipping plane
     * @param far The far clipping plane
     * @return Linear depth value from 0.0 (near plane) to 1.0 (far plane)
     */
    private static float linearizeDepth(float depthBufferValue, float near, float far) {
        // Handle edge cases
        if (depthBufferValue >= 0.9999f) {
            return 1.0f; // Far plane or sky
        }
        if (depthBufferValue <= 0.0001f) {
            return 0.0f; // Near plane
        }
        
        // Convert from [0, 1] depth buffer range to NDC (Normalized Device Coordinates) [-1, 1]
        float ndc = depthBufferValue * 2.0f - 1.0f;
        
        // Convert from NDC to linear depth using perspective projection matrix formula
        float linearDepth = (2.0f * near * far) / (far + near - ndc * (far - near));
        
        // Normalize to [0, 1] range where 0 = near plane, 1 = far plane
        float normalized = (linearDepth - near) / (far - near);
        
        // Clamp to valid range
        return Math.max(0.0f, Math.min(1.0f, normalized));
    }
    
    /**
     * Converts linear depth values to grayscale pixel data (0-255).
     * Near objects will be darker (approaching black) and far objects will be lighter (approaching white).
     * 
     * @param linearDepth Array of linear depth values [0, 1]
     * @return Byte array containing grayscale pixel values [0, 255]
     */
    public static byte[] depthToGrayscale(float[] linearDepth) {
        byte[] grayscale = new byte[linearDepth.length * 3]; // RGB format
        
        for (int i = 0; i < linearDepth.length; i++) {
            // Clamp to [0, 1] and convert to [0, 255]
            int value = (int) Math.min(255, Math.max(0, linearDepth[i] * 255));
            byte byteValue = (byte) value;
            
            // Set R, G, B to the same value for grayscale
            grayscale[i * 3] = byteValue;     // R
            grayscale[i * 3 + 1] = byteValue; // G
            grayscale[i * 3 + 2] = byteValue; // B
        }
        
        return grayscale;
    }
    
    /**
     * Alternative method: Extract raw depth values without linearization.
     * Useful for debugging - raw depth buffer values are already in [0, 1] range.
     * 
     * @param framebuffer The framebuffer to read depth from
     * @param width The width of the framebuffer
     * @param height The height of the framebuffer
     * @return A float array containing raw depth buffer values from 0.0 to 1.0
     */
    public static float[] extractRawDepth(Framebuffer framebuffer, int width, int height) {
        FloatBuffer depthBuffer = BufferUtils.createFloatBuffer(width * height);
        
        RenderSystem.assertOnRenderThread();
        
        framebuffer.beginRead();
        GL11.glReadPixels(0, 0, width, height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depthBuffer);
        framebuffer.endRead();
        
        float[] depthData = new float[width * height];
        depthBuffer.get(depthData);
        
        return depthData;
    }
    
    /**
     * Applies contrast enhancement to depth values to make details more visible.
     * Uses a power curve to enhance near-field detail.
     * 
     * @param depthData Array of depth values [0, 1]
     * @param gamma Gamma value for contrast (try 0.5 for more near detail, 2.0 for more far detail)
     * @return Enhanced depth array
     */
    public static float[] enhanceDepthContrast(float[] depthData, float gamma) {
        float[] enhanced = new float[depthData.length];
        for (int i = 0; i < depthData.length; i++) {
            enhanced[i] = (float) Math.pow(depthData[i], gamma);
        }
        return enhanced;
    }
}
