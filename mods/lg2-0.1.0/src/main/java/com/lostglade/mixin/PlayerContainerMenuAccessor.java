package com.lostglade.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerContainerMenuAccessor {
	@Accessor("containerMenu")
	void lg2$setContainerMenu(AbstractContainerMenu menu);
}
