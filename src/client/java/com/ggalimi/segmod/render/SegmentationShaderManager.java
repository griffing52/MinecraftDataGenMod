package com.ggalimi.segmod.render;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;

import java.io.IOException;

/**
 * Manages the segmentation shader program.
 */
public class SegmentationShaderManager {
    private static SegmentationShaderManager instance;
    private ShaderProgram program;

    public static SegmentationShaderManager getInstance() {
        if (instance == null) {
            instance = new SegmentationShaderManager();
        }
        return instance;
    }

    public void load(ResourceFactory factory) throws IOException {
        System.out.println("[SEGMOD SHADER] Loading segmentation shader");
        // Load "segmod_segmentation" from assets/minecraft/shaders/core/segmod_segmentation.json
        this.program = new ShaderProgram(factory, "segmod_segmentation", VertexFormats.POSITION_TEXTURE_COLOR);
        System.out.println("[SEGMOD SHADER] Segmentation shader loaded successfully: " + this.program);
    }

    public ShaderProgram getProgram() {
        return program;
    }
}
