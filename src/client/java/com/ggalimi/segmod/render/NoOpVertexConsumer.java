package com.ggalimi.segmod.render;

import net.minecraft.client.render.VertexConsumer;

/**
 * A VertexConsumer that does nothing. Used to suppress rendering of shadows etc.
 */
public class NoOpVertexConsumer implements VertexConsumer {
    public static final NoOpVertexConsumer INSTANCE = new NoOpVertexConsumer();

    private NoOpVertexConsumer() {}

    // @Override
    public VertexConsumer vertex(double x, double y, double z) { return this; }

    @Override
    public VertexConsumer vertex(float x, float y, float z) { return this; }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) { return this; }

    @Override
    public VertexConsumer texture(float u, float v) { return this; }

    @Override
    public VertexConsumer overlay(int u, int v) { return this; }

    @Override
    public VertexConsumer light(int u, int v) { return this; }

    @Override
    public VertexConsumer normal(float x, float y, float z) { return this; }

    // @Override - Removed Override annotation as these methods might not exist in all versions or mappings
    public void next() {}

    // @Override
    public void fixedColor(int red, int green, int blue, int alpha) {}

    // @Override
    public void unfixColor() {}
}
