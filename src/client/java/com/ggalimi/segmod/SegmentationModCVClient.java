package com.ggalimi.segmod;

import com.ggalimi.segmod.render.FrameCapture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side initialization for the Segmentation Mod.
 * Sets up automatic frame capture system with keybindings.
 */
public class SegmentationModCVClient implements ClientModInitializer {
	
	// Keybindings
	private static KeyBinding captureFrameKey;
	private static KeyBinding toggleAutoCaptureKey;
	
	@Override
	public void onInitializeClient() {
		System.out.println("[SegMod] Initializing segmentation pipeline...");
		
		// Register keybindings
		captureFrameKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.segmod.capture_frame",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_F8, // F8 to capture single frame
			"category.segmod"
		));
		
		toggleAutoCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.segmod.toggle_auto_capture",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_F9, // F9 to toggle automatic capture
			"category.segmod"
		));
		
		// Register tick event for keybindings and auto-capture timing
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Handle keybindings
			while (captureFrameKey.wasPressed()) {
				FrameCapture.requestCapture();
			}
			
			while (toggleAutoCaptureKey.wasPressed()) {
				FrameCapture.toggleAutoCapture();
			}
			
			// Process automatic capture timing
			FrameCapture.tick();
		});
		
		// Register world render event to capture after world but before HUD
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			FrameCapture.onWorldRendered(context);
		});
		
		System.out.println("[SegMod] Frame capture initialized!");
		System.out.println("[SegMod] Press F8 to capture a single frame");
		System.out.println("[SegMod] Press F9 to toggle automatic capture");
		System.out.println("[SegMod] Output directory: " + FrameCapture.getOutputDirectory().getAbsolutePath());
	}
}