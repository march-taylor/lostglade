package com.lostglade.raceclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.Minecraft;
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
	private static final Component[] LABELS = {
			Component.translatable("key.lg2.race_attack"), Component.translatable("key.lg2.race_defense"),
			Component.translatable("key.lg2.race_ability"), Component.translatable("key.lg2.race_shnyaga")
	};

	public RaceAbilityScreen() {
		super(Component.translatable("key.lg2.race_menu"));
	}

	@Override
	protected void init() {
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

	private static final class RaceActionButton extends AbstractWidget {
		private final int slot;

		private RaceActionButton(int x, int y, int slot) {
			super(x, y, BUTTON_SIZE, BUTTON_SIZE, LABELS[slot]);
			this.slot = slot;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.pose().pushMatrix();
			try {
				graphics.pose().translate(this.getX(), this.getY());
				graphics.pose().scale(TEXTURE_SCALE, TEXTURE_SCALE);
				graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS[this.slot], 0, 0, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
			} finally {
				graphics.pose().popMatrix();
			}
			if (this.isHoveredOrFocused()) {
				// The frame replaces the texture's boundary pixels instead of growing outside the button.
				graphics.renderOutline(this.getX(), this.getY(), BUTTON_SIZE, BUTTON_SIZE, 0xFFFFFFFF);
				graphics.setTooltipForNextFrame(this.getMessage(), mouseX, mouseY);
			}
		}

		@Override
		public void onClick(MouseButtonEvent click, boolean doubleClick) {
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
