package com.ggalimi.segmod.util;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class SegmentationIdMap {
    private static final Map<Identifier, Integer> BLOCK_IDS = new HashMap<>();
    private static final Map<Identifier, Integer> ENTITY_TYPE_IDS = new HashMap<>();
    private static int nextBlockId = 1;
    private static int nextEntityId = 1;

    public static void init() {
        // Initialize Block IDs deterministically
        for (Block block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            BLOCK_IDS.put(id, nextBlockId++);
        }

        // Initialize EntityType IDs deterministically
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            ENTITY_TYPE_IDS.put(id, nextEntityId++);
        }
    }

    public static int getBlockId(Block block) {
        return BLOCK_IDS.getOrDefault(Registries.BLOCK.getId(block), 0);
    }

    public static int getEntityTypeId(EntityType<?> type) {
        return ENTITY_TYPE_IDS.getOrDefault(Registries.ENTITY_TYPE.getId(type), 0);
    }
    
    // Helper to encode integer ID to RGB color
    public static int[] idToRgb(int id) {
        int r = (id & 0xFF0000) >> 16;
        int g = (id & 0xFF00) >> 8;
        int b = (id & 0xFF);
        return new int[]{r, g, b};
    }
}
