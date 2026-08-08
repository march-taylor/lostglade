package com.lostglade.raceclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class RaceAbilityScreen extends Screen {
	private static final int BUTTON_SIZE = 96;
	private static final int TEXTURE_SIZE = 32;
	private static final float TEXTURE_SCALE = BUTTON_SIZE / (float) TEXTURE_SIZE;
	private static final int GAP = 4;
	private static final Identifier[] ICONS = {
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/attack.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/defense.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/ability.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/shnyaga.png")
	};
	private static final Identifier[] DISABLED_ICONS = {
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/attack_disabled.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/defense_disabled.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/ability_disabled.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/shnyaga_disabled.png")
	};
	private static final Identifier[] HOVER_FRAMES = {
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/attack_hover_frame.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/defense_hover_frame.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/ability_hover_frame.png"),
			Identifier.fromNamespaceAndPath("lg2_race_client", "textures/gui/race/shnyaga_hover_frame.png")
	};
	private static final Component[] LABELS = {
			Component.translatable("key.lg2.race_attack"), Component.translatable("key.lg2.race_defense"),
			Component.translatable("key.lg2.race_ability"), Component.translatable("key.lg2.race_shnyaga")
	};

	public RaceAbilityScreen() {
		super(Component.translatable("key.lg2.race_menu"));
	}

	@Override
	protected void init() {
		// Minecraft calls releaseAll() after constructing the screen and before
		// init(). Refresh here, after that reset, so held movement is preserved.
		KeyMapping.setAll();
		int gridSize = BUTTON_SIZE * 2 + GAP;
		int startX = (this.width - gridSize) / 2;
		int startY = (this.height - gridSize) / 2;
		for (int slot = 0; slot < ICONS.length; slot++) {
			int x = startX + (slot % 2) * (BUTTON_SIZE + GAP);
			int y = startY + (slot / 2) * (BUTTON_SIZE + GAP);
			this.addRenderableWidget(new RaceActionButton(x, y, slot));
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * The vanilla keyboard handler intentionally stops updating game key mappings
	 * whenever any screen is open.  This screen only consumes mouse input, so
	 * forward/back/strafe/jump are mirrored into those mappings explicitly.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isMovementKey(event)) {
			KeyMapping.set(InputConstants.getKey(event), true);
			return false;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (isMovementKey(event)) {
			KeyMapping.set(InputConstants.getKey(event), false);
			return false;
		}
		return super.keyReleased(event);
	}

	private static boolean isMovementKey(KeyEvent event) {
		var options = Minecraft.getInstance().options;
		return options.keyUp.matches(event)
				|| options.keyDown.matches(event)
				|| options.keyLeft.matches(event)
				|| options.keyRight.matches(event)
				|| options.keyJump.matches(event)
				|| options.keySprint.matches(event);
	}

	private static final class RaceActionButton extends AbstractWidget {
		private final int slot;

		private RaceActionButton(int x, int y, int slot) {
			super(x, y, BUTTON_SIZE, BUTTON_SIZE, LABELS[slot]);
			this.slot = slot;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			boolean unlocked = RaceAbilityState.isUnlocked(this.slot);
			this.active = unlocked;
			drawTexture(graphics, unlocked ? ICONS[this.slot] : DISABLED_ICONS[this.slot]);
			if (!unlocked) {
				return;
			}
			if (this.isHoveredOrFocused()) {
				drawTexture(graphics, HOVER_FRAMES[this.slot]);
			}
		}

		private void drawTexture(GuiGraphics graphics, Identifier texture) {
			graphics.pose().pushMatrix();
			try {
				graphics.pose().translate(this.getX(), this.getY());
				graphics.pose().scale(TEXTURE_SCALE, TEXTURE_SCALE);
				graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
			} finally {
				graphics.pose().popMatrix();
			}
		}

		@Override
		public void onClick(MouseButtonEvent click, boolean doubleClick) {
			if (!RaceAbilityState.isUnlocked(this.slot)) {
				return;
			}
			RaceClientControls.useAbility(this.slot);
			Minecraft.getInstance().setScreen(null);
		}

		@Override
		public void playDownSound(SoundManager soundManager) {
			soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput narrationOutput) {
			this.defaultButtonNarrationText(narrationOutput);
		}
	}
}
