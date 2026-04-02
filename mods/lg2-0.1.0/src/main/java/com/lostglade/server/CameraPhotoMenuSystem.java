package com.lostglade.server;

import com.lostglade.item.CameraPhotoSettings;
import com.lostglade.item.ModItems;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;

public final class CameraPhotoMenuSystem {
	private static final int MENU_ROWS = 4;
	private static final int MENU_COLUMNS = 9;
	private static final int GRID_COLUMNS = CameraPhotoSettings.MAX_MAPS_WIDE;
	private static final int GRID_ROWS = CameraPhotoSettings.MAX_MAPS_HIGH;
	private static final int CAMERA_SLOT = 6;
	private static final int INFO_SLOT = 7;
	private static final int CLOSE_SLOT = 8;
	private static final int PHOTO_MODE_SLOT = 15;
	private static final int VIDEO_MODE_SLOT = 16;
	private static final int TOTAL_SLOT = 17;
	private static final String TITLE_SHIFT = "\ue905";
	private static final String TITLE_RESET = "\ue940\ue940\ue941\ue943";
	private static final String CAMERA_PANEL_GLYPH = "\uebf0";
	private static final int CAMERA_SIZE_X = 142;
	private static final int CAMERA_TOTAL_X = 151;
	private static final Identifier INVISIBLE_BUTTON_MODEL = Objects.requireNonNull(Identifier.tryParse("lg2:gui/button/invisible"));
	private static final FontDescription CAMERA_PANEL_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:camera_menu_panel"))
	);
	private static final FontDescription CAMERA_SIZE_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:camera_menu_size"))
	);
	private static final FontDescription CAMERA_TOTAL_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:camera_menu_count"))
	);

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
		CameraPhotoSettings settings = CameraPhotoSettings.read(stack);
		OptionalInt containerId = player.openMenu(new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> new CameraPhotoMenu(syncId, inventory, selectedSlot, player),
				menuTitle(player, settings)
		));
		if (containerId.isPresent() && player.containerMenu instanceof CameraPhotoMenu menu) {
			menu.resyncVisuals();
		}
	}

	private static Component menuTitle(ServerPlayer player, CameraPhotoSettings settings) {
		Component plainTitle = plainMenuTitle(player);
		if (player == null || !PolymerResourcePackUtils.hasMainPack(player) || settings == null) {
			return plainTitle;
		}

		String sizeText = settings.mapsWide() + "x" + settings.mapsHigh();
		String totalText = Integer.toString(settings.totalMaps());
		int titleWidth = plainTitle.getString().length() * 6;
		int sizeWidth = sizeText.length() * 6;

		MutableComponent title = Component.empty();
		title.append(defaultStyled(TITLE_SHIFT));
		title.append(Component.literal(CAMERA_PANEL_GLYPH).withStyle(style -> style.withColor(0xFFFFFF).withItalic(false).withFont(CAMERA_PANEL_FONT)));
		title.append(defaultStyled(TITLE_RESET));
		title.append(plainTitle.copy().withStyle(style -> style.withColor(0xD0C4A7).withItalic(false)));
		title.append(defaultStyled(buildHorizontalAdvance(CAMERA_SIZE_X - titleWidth)));
		title.append(Component.literal(sizeText)
				.withStyle(style -> style.withColor(0xE1D4B1).withItalic(false).withFont(CAMERA_SIZE_FONT)));
		title.append(defaultStyled(buildHorizontalAdvance(CAMERA_TOTAL_X - CAMERA_SIZE_X - sizeWidth)));
		title.append(Component.literal(totalText)
				.withStyle(style -> style.withColor(0xD2C39A).withItalic(false).withFont(CAMERA_TOTAL_FONT)));
		return title;
	}

	private static Component plainMenuTitle(ServerPlayer player) {
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

	private static Component photoModeLabel(ServerPlayer player, boolean selected) {
		String prefix = selected ? "[Фото] " : "Фото";
		String locale = locale(player);
		if (locale.startsWith("ja")) {
			return literal(selected ? "[写真] 写真" : "写真");
		}
		if (locale.startsWith("uk")) {
			return literal(selected ? "[Фото] Фото" : "Фото");
		}
		return literal(prefix);
	}

	private static Component videoModeLabel(ServerPlayer player, boolean selected) {
		String prefix = selected ? "[Видео] " : "Видео";
		String locale = locale(player);
		if (locale.startsWith("ja")) {
			return literal(selected ? "[動画] 動画" : "動画");
		}
		if (locale.startsWith("uk")) {
			return literal(selected ? "[Відео] Відео" : "Відео");
		}
		return literal(prefix);
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

	private static MutableComponent defaultStyled(String value) {
		return Component.literal(value).withStyle(style -> style.withColor(0xFFFFFF).withItalic(false));
	}

	private static ItemStack named(ItemStack stack, Component name) {
		stack.set(DataComponents.CUSTOM_NAME, name);
		return stack;
	}

	private static ItemStack invisibleGuiStack() {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, INVISIBLE_BUTTON_MODEL);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return stack;
	}

	private static String buildHorizontalAdvance(int pixels) {
		if (pixels == 0) {
			return "";
		}

		int remaining = pixels;
		StringBuilder result = new StringBuilder();
		int[] values = remaining > 0
				? new int[]{64, 32, 16, 8, 4, 2, 1}
				: new int[]{-64, -32, -16, -8, -4, -2, -1};
		String[] glyphs = remaining > 0
				? new String[]{"\ue94d", "\ue94c", "\ue94b", "\ue94a", "\ue949", "\ue948", "\ue947"}
				: new String[]{"\ue940", "\ue941", "\ue942", "\ue943", "\ue944", "\ue945", "\ue946"};

		for (int index = 0; index < values.length; index++) {
			int step = values[index];
			while ((remaining > 0 && remaining >= step) || (remaining < 0 && remaining <= step)) {
				result.append(glyphs[index]);
				remaining -= step;
			}
		}
		return result.toString();
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
			CameraPhotoSettings currentSettings = CameraPhotoSettings.read(cameraStack);
			if (slotId == PHOTO_MODE_SLOT) {
				CameraPhotoSettings.write(cameraStack, new CameraPhotoSettings(currentSettings.mapsWide(), currentSettings.mapsHigh(), CameraPhotoSettings.CaptureMode.PHOTO));
				ServerMechanicsGateSystem.syncPlayerInventory(this.viewer);
				this.refreshContents();
				this.broadcastFullState();
				return;
			}
			if (slotId == VIDEO_MODE_SLOT) {
				CameraPhotoSettings.write(cameraStack, new CameraPhotoSettings(currentSettings.mapsWide(), currentSettings.mapsHigh(), CameraPhotoSettings.CaptureMode.VIDEO));
				ServerMechanicsGateSystem.syncPlayerInventory(this.viewer);
				this.refreshContents();
				this.broadcastFullState();
				return;
			}
			int row = slotId / MENU_COLUMNS;
			int column = slotId % MENU_COLUMNS;
			if (column < GRID_COLUMNS && row < GRID_ROWS) {
				CameraPhotoSettings.write(cameraStack, new CameraPhotoSettings(column + 1, row + 1, currentSettings.captureMode()));
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
		public void broadcastChanges() {
			super.broadcastChanges();
			this.hideLowerInventoryVisuals();
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			this.restoreLowerInventoryVisuals();
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

			this.container.setItem(CAMERA_SLOT, invisibleGuiStack());
			this.container.setItem(INFO_SLOT, invisibleGuiStack());
			this.container.setItem(CLOSE_SLOT, invisibleGuiStack());
			this.container.setItem(
					PHOTO_MODE_SLOT,
					named(
							new ItemStack(settings.captureMode() == CameraPhotoSettings.CaptureMode.PHOTO ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE),
							photoModeLabel(this.viewer, settings.captureMode() == CameraPhotoSettings.CaptureMode.PHOTO)
					)
			);
			this.container.setItem(
					VIDEO_MODE_SLOT,
					named(
							new ItemStack(settings.captureMode() == CameraPhotoSettings.CaptureMode.VIDEO ? Items.RED_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE),
							videoModeLabel(this.viewer, settings.captureMode() == CameraPhotoSettings.CaptureMode.VIDEO)
					)
			);
			this.container.setItem(TOTAL_SLOT, named(new ItemStack(Items.PAPER), totalMapsLabel(this.viewer, settings)));
			this.refreshTitle(settings);
		}

		private void refreshTitle(CameraPhotoSettings settings) {
			if (this.viewer == null || this.viewer.containerMenu != this) {
				return;
			}
			this.viewer.connection.send(new ClientboundOpenScreenPacket(this.containerId, this.getType(), menuTitle(this.viewer, settings)));
			this.resyncVisuals();
		}

		private void resyncVisuals() {
			this.sendTopMenuVisuals();
			this.hideLowerInventoryVisuals();
		}

		private void sendTopMenuVisuals() {
			if (this.viewer == null) {
				return;
			}
			Inventory inventory = this.viewer.getInventory();
			PacketContext.NotNullWithPlayer context = PacketContext.create(this.viewer);
			int stateId = this.incrementStateId();
			for (int menuSlot = 0; menuSlot < this.slots.size(); menuSlot++) {
				Slot slot = this.getSlot(menuSlot);
				if (slot.container == inventory) {
					continue;
				}
				this.viewer.connection.send(new ClientboundContainerSetSlotPacket(
						this.containerId,
						stateId,
						menuSlot,
						toClientVisualStack(slot.getItem().copy(), context)
				));
			}
		}

		private void hideLowerInventoryVisuals() {
			if (this.viewer == null) {
				return;
			}
			Inventory inventory = this.viewer.getInventory();
			int stateId = this.incrementStateId();
			for (int menuSlot = 0; menuSlot < this.slots.size(); menuSlot++) {
				Slot slot = this.getSlot(menuSlot);
				if (slot.container != inventory) {
					continue;
				}
				this.viewer.connection.send(new ClientboundContainerSetSlotPacket(
						this.containerId,
						stateId,
						menuSlot,
						ItemStack.EMPTY
				));
			}
			this.syncHeldEquipmentVisuals(true);
		}

		private void restoreLowerInventoryVisuals() {
			if (this.viewer == null) {
				return;
			}
			AbstractContainerMenu targetMenu = this.viewer.containerMenu;
			if (targetMenu == null || targetMenu == this) {
				targetMenu = this.viewer.inventoryMenu;
			}
			if (targetMenu != null) {
				Inventory inventory = this.viewer.getInventory();
				PacketContext.NotNullWithPlayer context = PacketContext.create(this.viewer);
				int stateId = targetMenu.incrementStateId();
				for (int menuSlot = 0; menuSlot < targetMenu.slots.size(); menuSlot++) {
					Slot slot = targetMenu.getSlot(menuSlot);
					if (slot.container != inventory) {
						continue;
					}
					int inventorySlot = slot.getContainerSlot();
					this.viewer.connection.send(new ClientboundContainerSetSlotPacket(
							targetMenu.containerId,
							stateId,
							menuSlot,
							toClientVisualStack(inventory.getItem(inventorySlot).copy(), context)
					));
				}
			}
			this.syncHeldEquipmentVisuals(false);
		}

		private void syncHeldEquipmentVisuals(boolean hide) {
			if (this.viewer == null) {
				return;
			}
			PacketContext.NotNullWithPlayer context = PacketContext.create(this.viewer);
			ItemStack mainHand = hide ? ItemStack.EMPTY : toClientVisualStack(this.viewer.getMainHandItem().copy(), context);
			ItemStack offHand = hide ? ItemStack.EMPTY : toClientVisualStack(this.viewer.getOffhandItem().copy(), context);
			this.viewer.connection.send(new ClientboundSetEquipmentPacket(
					this.viewer.getId(),
					List.of(
							com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND, mainHand),
							com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND, offHand)
					)
			));
		}

		private ItemStack toClientVisualStack(ItemStack stack, PacketContext.NotNullWithPlayer context) {
			if (stack == null || stack.isEmpty()) {
				return ItemStack.EMPTY;
			}
			ItemStack clientStack = PolymerItemUtils.getClientItemStack(stack, context);
			return clientStack.isEmpty() ? stack.copy() : clientStack.copy();
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
