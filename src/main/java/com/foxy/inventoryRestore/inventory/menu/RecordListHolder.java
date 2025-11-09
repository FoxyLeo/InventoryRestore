package com.foxy.inventoryRestore.inventory.menu;

public final class RecordListHolder extends AbstractRestoreInventoryHolder {

    private final InventoryRecordType type;
    private final String targetName;
    private final int page;

    public RecordListHolder(int size, String title, InventoryRecordType type, String targetName, int page) {
        super(size, title);
        this.type = type;
        this.targetName = targetName;
        this.page = page;
    }

    public InventoryRecordType getType() {
        return type;
    }

    public String getTargetName() {
        return targetName;
    }

    public int getPage() {
        return page;
    }
}
