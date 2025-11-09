package com.foxy.inventoryRestore.database.record;

import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;

public record StoredPlayerInventory(long id,
                                    InventoryRecordType type,
                                    String eventDate,
                                    String uuid,
                                    String nickname,
                                    String inventory,
                                    boolean returned,
                                    String location,
                                    String world) implements StoredInventoryRecord {

    @Override
    public String timestamp() {
        return eventDate;
    }

    @Override
    public String location() {
        return location;
    }

    @Override
    public String world() {
        return world;
    }
}
