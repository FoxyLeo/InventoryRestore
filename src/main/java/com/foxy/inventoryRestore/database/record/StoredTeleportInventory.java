package com.foxy.inventoryRestore.database.record;

import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;

public record StoredTeleportInventory(long id,
                                      String fromLocation,
                                      String toLocation,
                                      String eventDate,
                                      String uuid,
                                      String nickname,
                                      String inventory,
                                      boolean returned) implements StoredInventoryRecord {

    @Override
    public String timestamp() {
        return eventDate;
    }

    @Override
    public InventoryRecordType type() {
        return InventoryRecordType.TELEPORT;
    }
}
