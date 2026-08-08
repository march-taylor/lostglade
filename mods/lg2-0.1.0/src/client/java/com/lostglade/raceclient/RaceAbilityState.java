package com.lostglade.raceclient;

final class RaceAbilityState {
	private static volatile int unlockedMask;

	private RaceAbilityState() {
	}

	static boolean isUnlocked(int slot) {
		return slot >= 0 && slot < 4 && (unlockedMask & (1 << slot)) != 0;
	}

	static void update(int newUnlockedMask) {
		unlockedMask = newUnlockedMask & 0xF;
	}

	static void clear() {
		unlockedMask = 0;
	}
}
