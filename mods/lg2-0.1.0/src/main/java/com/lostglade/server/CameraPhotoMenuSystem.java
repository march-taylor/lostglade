package com.lostglade.server;

import com.lostglade.item.CameraPhotoSettings;
import com.lostglade.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public final class CameraPhotoMenuSystem {
	private static final int MENU_ROWS = 4;
	private static final int MENU_COLUMNS = 9;
	private static final int GRID_COLUMNS = CameraPhotoSettings.MAX_MAPS_WIDE;
	private static final int GRID_ROWS = CameraPhotoSettings.MAX_MAPS_HIGH;
	private static final int CLOSE_SLOT = 8;
	private static final int INFO_SLOT = 7;
	private static final int TITLE_SLOT = 6;
	private static final int HELP_SLOT = 15;
	private static final int TOTAL_SLOT = 16;

	private CameraPhotoMenuSystem() {
	}

	public static void open(ServerPlayer player) {
		if (player == null) {
			return;
		}
		int selectedSlot = player.getInventory().getSelectedSlot();
		ItemStack stack = player.getInventory().getItem(selectedSlot);
		if (stack == null || stack.isEmpty() || !stack.is(ModItems.CAMERA)) {
			return;
		}
		player.openMenu(new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> new CameraPhotoMenu(syncId, inventory, selectedSlot, player),
				menuTitle(player)
		));
	}

	private static Component menuTitle(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Размеръ снимка");
		}
		if (locale.startsWith("uk")) {
			return literal("Розмір знімка");
		}
		if (locale.startsWith("ja")) {
			return literal("写真サイズ");
		}
		if (locale.startsWith("ru")) {
			return literal("Размер снимка");
		}
		return literal("Photo Size");
	}

	private static Component currentSizeLabel(ServerPlayer player, CameraPhotoSettings settings) {
		String size = settings.mapsWide() + " x " + settings.mapsHigh();
		String total = Integer.toString(settings.totalMaps());
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Текущiй размеръ: " + size + " (" + total + ")");
		}
		if (locale.startsWith("uk")) {
			return literal("Поточний розмір: " + size + " (" + total + ")");
		}
		if (locale.startsWith("ja")) {
			return literal("現在: " + size + " (" + total + "枚)");
		}
		if (locale.startsWith("ru")) {
			return literal("Текущий размер: " + size + " (" + total + ")");
		}
		return literal("Current size: " + size + " (" + total + ")");
	}

	private static Component helpLabel(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Нажми ячейку сетки");
		}
		if (locale.startsWith("uk")) {
			return literal("Натисни клітинку сітки");
		}
		if (locale.startsWith("ja")) {
			return literal("グリッドをクリック");
		}
		if (locale.startsWith("ru")) {
			return literal("Нажми ячейку сетки");
		}
		return literal("Click a grid cell");
	}

	private static Component totalMapsLabel(ServerPlayer player, CameraPhotoSettings settings) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Картъ въ снимке: " + settings.totalMaps());
		}
		if (locale.startsWith("uk")) {
			return literal("Мап у знімку: " + settings.totalMaps());
		}
		if (locale.startsWith("ja")) {
			return literal("写真の地図枚数: " + settings.totalMaps());
		}
		if (locale.startsWith("ru")) {
			return literal("Карт в снимке: " + settings.totalMaps());
		}
		return literal("Maps in photo: " + settings.totalMaps());
	}

	private static Component closeLabel(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Закрыть");
		}
		if (locale.startsWith("uk")) {
			return literal("Закрити");
		}
		if (locale.startsWith("ja")) {
			return literal("閉じる");
		}
		if (locale.startsWith("ru")) {
			return literal("Закрыть");
		}
		return literal("Close");
	}

	private static String locale(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return "en_us";
		}
		return player.clientInformation().language().toLowerCase(Locale.ROOT);
	}

	private static Component literal(String value) {
		return Component.literal(value).withStyle(style -> style.withItalic(false));
	}

	private static ItemStack named(ItemStack stack, Component name) {
		stack.set(DataComponents.CUSTOM_NAME, name);
		return stack;
	}

	private static final class CameraPhotoMenu extends ChestMenu {
		private final SimpleContainer container;
		private final int cameraInventorySlot;
		private final ServerPlayer viewer;

		private CameraPhotoMenu(int syncId, Inventory inventory, int cameraInventorySlot, ServerPlayer viewer) {
			this(syncId, inventory, new SimpleContainer(MENU_ROWS * MENU_COLUMNS), cameraInventorySlot, viewer);
		}

		private CameraPhotoMenu(int syncId, Inventory inventory, SimpleContainer container, int cameraInventorySlot, ServerPlayer viewer) {
			super(MenuType.GENERIC_9x4, syncId, inventory, container, MENU_ROWS);
			this.container = container;
			this.cameraInventorySlot = cameraInventorySlot;
			this.viewer = viewer;
			this.refreshContents();
		}

		@Override
		public void clicked(int slotId, int button, ClickType clickType, Player player) {
			if (!(clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP)) {
				return;
			}
			if (slotId < 0 || slotId >= this.container.getContainerSize()) {
				return;
			}
			ItemStack cameraStack = currentCamera();
			if (cameraStack.isEmpty()) {
				this.viewer.closeContainer();
				return;
			}
			if (slotId == CLOSE_SLOT) {
				this.viewer.closeContainer();
				return;
			}
			int row = slotId / MENU_COLUMNS;
			int column = slotId % MENU_COLUMNS;
			if (column < GRID_COLUMNS && row < GRID_ROWS) {
				CameraPhotoSettings.write(cameraStack, new CameraPhotoSettings(column + 1, row + 1));
				ServerMechanicsGateSystem.syncPlayerInventory(this.viewer);
				this.refreshContents();
				this.broadcastFullState();
			}
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return player.isAlive() && !currentCamera().isEmpty();
		}

		private void refreshContents() {
			CameraPhotoSettings settings = CameraPhotoSettings.read(currentCamera());
			for (int slot = 0; slot < this.container.getContainerSize(); slot++) {
				this.container.setItem(slot, ItemStack.EMPTY);
			}

			for (int row = 0; row < GRID_ROWS; row++) {
				for (int column = 0; column < GRID_COLUMNS; column++) {
					boolean selected = column < settings.mapsWide() && row < settings.mapsHigh();
					ItemStack visual = named(
							new ItemStack(selected ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE),
							literal((column + 1) + " x " + (row + 1))
					);
					this.container.setItem(row * MENU_COLUMNS + column, visual);
				}
			}

			this.container.setItem(TITLE_SLOT, named(new ItemStack(ModItems.CAMERA), menuTitle(this.viewer)));
			this.container.setItem(INFO_SLOT, named(new ItemStack(Items.FILLED_MAP), currentSizeLabel(this.viewer, settings)));
			this.container.setItem(CLOSE_SLOT, named(new ItemStack(Items.BARRIER), closeLabel(this.viewer)));
			this.container.setItem(HELP_SLOT, named(new ItemStack(Items.PAPER), helpLabel(this.viewer)));
			this.container.setItem(TOTAL_SLOT, named(new ItemStack(Items.MAP), totalMapsLabel(this.viewer, settings)));
		}

		private ItemStack currentCamera() {
			if (this.cameraInventorySlot < 0 || this.cameraInventorySlot >= this.viewer.getInventory().getContainerSize()) {
				return ItemStack.EMPTY;
			}
			ItemStack stack = this.viewer.getInventory().getItem(this.cameraInventorySlot);
			return stack != null && stack.is(ModItems.CAMERA) ? stack : ItemStack.EMPTY;
		}
	}
}
