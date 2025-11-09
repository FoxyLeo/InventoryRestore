package com.foxy.inventoryRestore.database.record;

import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;

public record StoredDeathInventory(long id,
                                   String deathType,
                                   String deathDate,
                                   String uuid,
                                   String nickname,
                                   String inventory,
                                   boolean returned,
                                   String location,
                                   String world) implements StoredInventoryRecord {

    @Override
    public String timestamp() {
        return deathDate;
    }

    @Override
    public String location() {
        return location;
    }

    @Override
    public String world() {
        return world;
    }

    @Override
    public InventoryRecordType type() {
        return InventoryRecordType.DEATH;
    }
}
