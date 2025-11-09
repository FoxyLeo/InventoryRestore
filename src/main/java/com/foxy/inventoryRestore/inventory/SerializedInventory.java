package com.foxy.inventoryRestore.inventory;

import org.bukkit.inventory.ItemStack;

public record SerializedInventory(ItemStack[] contents, ItemStack[] armor, ItemStack[] extra) {
}
