package com.foxy.inventoryRestore.inventory.menu;

public final class RestoreMainHolder extends AbstractRestoreInventoryHolder {

    private final String targetName;

    public RestoreMainHolder(int size, String title, String targetName) {
        super(size, title);
        this.targetName = targetName;
    }

    public String getTargetName() {
        return targetName;
    }
}

