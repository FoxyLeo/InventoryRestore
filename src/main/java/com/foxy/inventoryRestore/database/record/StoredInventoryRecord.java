package com.foxy.inventoryRestore.database.record;

import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;

public interface StoredInventoryRecord {

    long id();

    String timestamp();

    String uuid();

    String nickname();

    String inventory();

    InventoryRecordType type();

    boolean returned();

    default String location() {
        return "";
    }

    default String world() {
        return "";
    }
}
