package com.foxy.inventoryRestore.database.record;

public record DeathRecord(String deathType,
                         String deathDate,
                         String uuid,
                         String nickname,
                         String inventory,
                         String location,
                         String world) {
}
