package com.foxy.inventoryRestore.inventory.view;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class ViewInventoryHolder implements InventoryHolder {

    private final Inventory inventory;
    private final UUID viewerId;
    private final String targetName;
    private final boolean canModify;

    public ViewInventoryHolder(int size, String title, UUID viewerId, String targetName, boolean canModify) {
        this.inventory = Bukkit.createInventory(this, size, title);
        this.viewerId = viewerId;
        this.targetName = targetName;
        this.canModify = canModify;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean canModify() {
        return canModify;
    }
}

