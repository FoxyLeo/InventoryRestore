package com.foxy.inventoryRestore.inventory.menu;

public enum InventoryRecordType {
    DEATH("death", true),
    WORLD("world", true),
    TELEPORT("teleport", true),
    CONNECTION("connection", true),
    DISCONNECTION("disconnection", true);

    private final String key;
    private final boolean usesReturnedFlag;

    InventoryRecordType(String key, boolean usesReturnedFlag) {
        this.key = key;
        this.usesReturnedFlag = usesReturnedFlag;
    }

    public String key() {
        return key;
    }

    public String tableName() {
        return key;
    }

    public boolean usesReturnedFlag() {
        return usesReturnedFlag;
    }

    public String listMessageKey(String path) {
        return "inventory.restore.list." + key + '.' + path;
    }

    public String detailMessageKey(String path) {
        return "inventory.restore.detail." + key + '.' + path;
    }
}
