package com.ggalimi.segmod.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Minecraft entities to unique RGB colors for segmentation masks.
 * Uses deterministic color generation based on entity type registry IDs to ensure
 * consistent colors across runs.
 * 
 * Entity colors are generated differently from blocks to avoid collisions.
 */
public class EntityClassMap {
    
    private static final Map<EntityType<?>, int[]> ENTITY_COLOR_CACHE = new HashMap<>();
    
    /**
     * Gets the RGB color for a given entity for segmentation purposes.
     * Each unique entity type gets a deterministic unique color.
     * 
     * Uses a different color generation algorithm than blocks to minimize collision risk.
     * 
     * @param entity The entity to get the color for
     * @return An RGB array [r, g, b] with values 0-255
     */
    public static int[] getEntityColor(Entity entity) {
        return getEntityTypeColor(entity.getType());
    }
    
    /**
     * Gets the RGB color for a given entity type for segmentation purposes.
     * Each unique entity type gets a deterministic unique color.
     * 
     * @param entityType The entity type to get the color for
     * @return An RGB array [r, g, b] with values 0-255
     */
    public static int[] getEntityTypeColor(EntityType<?> entityType) {
        // Check cache first
        if (ENTITY_COLOR_CACHE.containsKey(entityType)) {
            return ENTITY_COLOR_CACHE.get(entityType);
        }
        
        // Generate deterministic color based on entity type ID
        Identifier entityId = Registries.ENTITY_TYPE.getId(entityType);
        int hash = entityId.toString().hashCode();
        
        // Use a different mixing strategy than blocks to avoid color collisions
        // XOR with a salt to differentiate from block colors
        int salt = 0x7F3A92C1;
        hash = hash ^ salt;
        
        // Extract RGB with different bit patterns than blocks
        int r = ((hash & 0x00FF00) >> 8);    // Different bit position
        int g = ((hash & 0xFF0000) >> 16);   // Different bit position
        int b = (hash & 0x0000FF);           // Same position but different source due to XOR
        
        // Ensure colors are bright and distinct
        // Use higher minimum than blocks to further differentiate
        r = Math.max(r, 50);
        g = Math.max(g, 50);
        b = Math.max(b, 50);
        
        // Boost saturation to make entities stand out
        int maxChannel = Math.max(r, Math.max(g, b));
        if (maxChannel < 150) {
            float boost = 150.0f / maxChannel;
            r = Math.min(255, (int)(r * boost));
            g = Math.min(255, (int)(g * boost));
            b = Math.min(255, (int)(b * boost));
        }
        
        int[] color = new int[]{r, g, b};
        ENTITY_COLOR_CACHE.put(entityType, color);
        
        return color;
    }
    
    /**
     * Gets the color as a packed integer (0xRRGGBB format).
     * 
     * @param entity The entity to get the color for
     * @return Packed RGB integer
     */
    public static int getEntityColorPacked(Entity entity) {
        int[] rgb = getEntityColor(entity);
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }
    
    /**
     * Gets the color as a packed integer (0xRRGGBB format) for an entity type.
     * 
     * @param entityType The entity type to get the color for
     * @return Packed RGB integer
     */
    public static int getEntityTypeColorPacked(EntityType<?> entityType) {
        int[] rgb = getEntityTypeColor(entityType);
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }
    
    /**
     * Clears the color cache. Useful if you want to regenerate colors.
     */
    public static void clearCache() {
        ENTITY_COLOR_CACHE.clear();
    }
}
