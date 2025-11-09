package com.foxy.inventoryRestore.database.record;

public record TeleportRecord(String fromLocation,
                             String toLocation,
                             String eventDate,
                             String uuid,
                             String nickname,
                             String inventory) {
}
