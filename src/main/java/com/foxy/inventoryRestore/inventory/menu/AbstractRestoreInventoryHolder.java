package com.foxy.inventoryRestore.inventory.menu;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class AbstractRestoreInventoryHolder implements InventoryHolder {

    private final Inventory inventory;

    protected AbstractRestoreInventoryHolder(int size, String title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

