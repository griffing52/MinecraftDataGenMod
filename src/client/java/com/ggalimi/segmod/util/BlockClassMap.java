package com.ggalimi.segmod.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Minecraft blocks to unique RGB colors for segmentation masks.
 * Uses deterministic color generation based on block registry IDs to ensure
 * consistent colors across runs.
 */
public class BlockClassMap {
    
    private static final Map<Block, int[]> BLOCK_COLOR_CACHE = new HashMap<>();
    
    /**
     * Gets the RGB color for a given block for segmentation purposes.
     * Each unique block type gets a deterministic unique color.
     * 
     * @param block The block to get the color for
     * @return An RGB array [r, g, b] with values 0-255
     */
    public static int[] getBlockColor(Block block) {
        // Check cache first
        if (BLOCK_COLOR_CACHE.containsKey(block)) {
            return BLOCK_COLOR_CACHE.get(block);
        }
        
        // Generate deterministic color based on block ID
        Identifier blockId = Registries.BLOCK.getId(block);
        int hash = blockId.toString().hashCode();
        
        // Use hash to generate RGB values
        // Ensure colors are distinct and bright enough to be visible
        int r = ((hash & 0xFF0000) >> 16);
        int g = ((hash & 0x00FF00) >> 8);
        int b = (hash & 0x0000FF);
        
        // Ensure minimum brightness to avoid very dark colors
        r = Math.max(r, 30);
        g = Math.max(g, 30);
        b = Math.max(b, 30);
        
        int[] color = new int[]{r, g, b};
        BLOCK_COLOR_CACHE.put(block, color);
        
        return color;
    }
    
    /**
     * Gets the color as a packed integer (0xRRGGBB format).
     * 
     * @param block The block to get the color for
     * @return Packed RGB integer
     */
    public static int getBlockColorPacked(Block block) {
        int[] rgb = getBlockColor(block);
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }
    
    /**
     * Gets the class ID for a block (for backwards compatibility).
     * 
     * @param block The block to get the class for
     * @return Class ID integer
     */
    public static int getClass(Block block) {
        // Example simple mapping — replace with your actual classes
        if (block == Blocks.GRASS_BLOCK) return 1;
        if (block == Blocks.DIRT)        return 1;
        if (block == Blocks.STONE)       return 2;
        if (block == Blocks.WATER)       return 3;

        return 0; // default = background / unknown class
    }
}