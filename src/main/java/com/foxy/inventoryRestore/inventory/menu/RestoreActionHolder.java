package com.foxy.inventoryRestore.inventory.menu;

public final class RestoreActionHolder extends AbstractRestoreInventoryHolder {

    private final InventoryRecordType type;
    private final long recordId;
    private final String targetName;
    private final int page;

    public RestoreActionHolder(int size, String title, InventoryRecordType type, long recordId, String targetName, int page) {
        super(size, title);
        this.type = type;
        this.recordId = recordId;
        this.targetName = targetName;
        this.page = page;
    }

    public InventoryRecordType getType() {
        return type;
    }

    public long getRecordId() {
        return recordId;
    }

    public String getTargetName() {
        return targetName;
    }

    public int getPage() {
        return page;
    }
}
