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
    private ShaderProgram entityProgram;

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
        
        // Load "segmod_segmentation_entity"
        this.entityProgram = new ShaderProgram(factory, "segmod_segmentation_entity", VertexFormats.POSITION_COLOR);
        
        System.out.println("[SEGMOD SHADER] Segmentation shaders loaded successfully");
    }

    public ShaderProgram getProgram() {
        return program;
    }
    
    public ShaderProgram getEntityProgram() {
        return entityProgram;
    }
}
