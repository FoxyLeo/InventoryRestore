package com.foxy.inventoryRestore.database.record;

public record WorldChangeRecord(String fromWorld,
                                String toWorld,
                                String eventDate,
                                String uuid,
                                String nickname,
                                String inventory) {
}
