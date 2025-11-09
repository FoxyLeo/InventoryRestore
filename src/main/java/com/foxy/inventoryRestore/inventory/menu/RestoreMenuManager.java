package com.foxy.inventoryRestore.inventory.menu;

import com.foxy.inventoryRestore.InventoryRestore;
import com.foxy.inventoryRestore.database.DeathInventoryRepository;
import com.foxy.inventoryRestore.database.PlayerSnapshotRepository;
import com.foxy.inventoryRestore.database.TeleportRepository;
import com.foxy.inventoryRestore.database.WorldChangeRepository;
import com.foxy.inventoryRestore.database.record.StoredDeathInventory;
import com.foxy.inventoryRestore.database.record.StoredInventoryRecord;
import com.foxy.inventoryRestore.database.record.StoredTeleportInventory;
import com.foxy.inventoryRestore.database.record.StoredWorldInventory;
import com.foxy.inventoryRestore.inventory.InventoryLayoutService;
import com.foxy.inventoryRestore.inventory.InventorySerializer;
import com.foxy.inventoryRestore.inventory.SerializedInventory;
import com.foxy.inventoryRestore.message.MessageService;
import com.foxy.inventoryRestore.util.LocationFormats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RestoreMenuManager implements Listener {

    private final InventoryRestore plugin;
    private final MessageService messageService;
    private final InventoryLayoutService layoutService;
    private final DeathInventoryRepository deathRepository;
    private final PlayerSnapshotRepository snapshotRepository;
    private final WorldChangeRepository worldRepository;
    private final TeleportRepository teleportRepository;
    private final NamespacedKey recordIdKey;

    private final int mainMenuSize;
    private final int listMenuSize;
    private final int detailMenuSize;
    private final MenuConfiguration.MenuItem mainDeathItem;
    private final MenuConfiguration.MenuItem mainWorldItem;
    private final MenuConfiguration.MenuItem mainTeleportItem;
    private final MenuConfiguration.MenuItem mainConnectionItem;
    private final MenuConfiguration.MenuItem mainDisconnectionItem;
    private final MenuConfiguration.MenuItem restoreActionItem;
    private final MenuConfiguration.MenuItem eraseActionItem;
    private final MenuConfiguration.MenuItem previousPageItem;
    private final MenuConfiguration.MenuItem nextPageItem;
    private final int offhandSlot;
    private final int bootsSlot;
    private final int leggingsSlot;
    private final int chestplateSlot;
    private final int helmetSlot;
    private final List<Integer> listSlots;
    private final Material recordMaterial;
    private final List<Integer> contentSlots;

    public RestoreMenuManager(InventoryRestore plugin,
                              MessageService messageService,
                              InventoryLayoutService layoutService,
                              MenuConfiguration menuConfiguration,
                              DeathInventoryRepository deathRepository,
                              PlayerSnapshotRepository snapshotRepository,
                              WorldChangeRepository worldRepository,
                              TeleportRepository teleportRepository) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.layoutService = layoutService;
        this.deathRepository = deathRepository;
        this.snapshotRepository = snapshotRepository;
        this.worldRepository = worldRepository;
        this.teleportRepository = teleportRepository;
        this.recordIdKey = new NamespacedKey(plugin, "inventory-record-id");

        this.mainMenuSize = menuConfiguration.getMainMenuSize();
        this.listMenuSize = menuConfiguration.getDeathListSize();
        this.detailMenuSize = menuConfiguration.getDeathDetailSize();

        this.mainDeathItem = menuConfiguration.getMainMenuItem("death", 1, Material.SKELETON_SKULL);
        this.mainWorldItem = menuConfiguration.getMainMenuItem("world", 3, Material.GRASS_BLOCK);
        this.mainTeleportItem = menuConfiguration.getMainMenuItem("teleport", 5, Material.ENDER_PEARL);
        this.mainConnectionItem = menuConfiguration.getMainMenuItem("connection", 11, Material.LIME_WOOL);
        this.mainDisconnectionItem = menuConfiguration.getMainMenuItem("disconnection", 13, Material.PINK_WOOL);

        this.restoreActionItem = menuConfiguration.getDeathDetailAction("restore", 45, Material.LIME_WOOL);
        this.eraseActionItem = menuConfiguration.getDeathDetailAction("erase", 53, Material.PINK_WOOL);
        this.previousPageItem = menuConfiguration.getDeathListNavigationItem("previous", 45, Material.ARROW);
        this.nextPageItem = menuConfiguration.getDeathListNavigationItem("next", 53, Material.ARROW);

        this.offhandSlot = menuConfiguration.getEquipmentSlot("offhand", 47);
        this.bootsSlot = menuConfiguration.getEquipmentSlot("boots", 48);
        this.leggingsSlot = menuConfiguration.getEquipmentSlot("leggings", 49);
        this.chestplateSlot = menuConfiguration.getEquipmentSlot("chestplate", 50);
        this.helmetSlot = menuConfiguration.getEquipmentSlot("helmet", 51);

        this.listSlots = menuConfiguration.getDeathListSlots();
        this.recordMaterial = menuConfiguration.getDeathListRecordMaterial(Material.BOOK);
        this.contentSlots = menuConfiguration.getContentSlots();
    }

    public void openMainMenu(Player viewer, String targetName) {
        String title = messageService.formatMessage("inventory.restore.main.title", Map.of("player", targetName), false);
        RestoreMainHolder holder = new RestoreMainHolder(mainMenuSize, title, targetName);
        Inventory inventory = holder.getInventory();

        inventory.setItem(mainDeathItem.slot(), buildMenuItem(
                mainDeathItem.material(),
                messageService.formatMessage("inventory.restore.main.items.death.name", Map.of(), false),
                messageService.formatList("inventory.restore.main.items.death.lore", Map.of(), false)
        ));

        inventory.setItem(mainWorldItem.slot(), buildMenuItem(
                mainWorldItem.material(),
                messageService.formatMessage("inventory.restore.main.items.world.name", Map.of(), false),
                messageService.formatList("inventory.restore.main.items.world.lore", Map.of(), false)
        ));

        inventory.setItem(mainTeleportItem.slot(), buildMenuItem(
                mainTeleportItem.material(),
                messageService.formatMessage("inventory.restore.main.items.teleport.name", Map.of(), false),
                messageService.formatList("inventory.restore.main.items.teleport.lore", Map.of(), false)
        ));

        inventory.setItem(mainConnectionItem.slot(), buildMenuItem(
                mainConnectionItem.material(),
                messageService.formatMessage("inventory.restore.main.items.connection.name", Map.of(), false),
                messageService.formatList("inventory.restore.main.items.connection.lore", Map.of(), false)
        ));

        inventory.setItem(mainDisconnectionItem.slot(), buildMenuItem(
                mainDisconnectionItem.material(),
                messageService.formatMessage("inventory.restore.main.items.disconnection.name", Map.of(), false),
                messageService.formatList("inventory.restore.main.items.disconnection.lore", Map.of(), false)
        ));

        viewer.openInventory(inventory);
    }

    public void openRecordListMenu(Player viewer, String targetName, InventoryRecordType type, int page) {
        int itemsPerPage = listSlots.size();
        if (itemsPerPage <= 0) {
            messageService.send(viewer, "inventory.restore.main.unavailable", Map.of(), true);
            return;
        }

        int totalRecords = countRecords(targetName, type);
        if (totalRecords <= 0) {
            messageService.send(viewer, type.listMessageKey("empty"), Map.of("player", targetName), true);
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(totalRecords / (double) itemsPerPage));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int offset = currentPage * itemsPerPage;
        List<? extends StoredInventoryRecord> records = fetchRecords(targetName, type, itemsPerPage, offset);

        String title = messageService.formatMessage(type.listMessageKey("title"), Map.of("player", targetName), false);
        RecordListHolder holder = new RecordListHolder(listMenuSize, title, type, targetName, currentPage);
        Inventory inventory = holder.getInventory();

        int startIndex = currentPage * itemsPerPage;
        for (int slotIndex = 0; slotIndex < itemsPerPage; slotIndex++) {
            int recordIndex = startIndex + slotIndex;
            if (recordIndex >= records.size()) {
                break;
            }

            int slot = listSlots.get(slotIndex);
            if (slot < 0 || slot >= listMenuSize) {
                continue;
            }

            StoredInventoryRecord record = records.get(recordIndex);
            ItemStack item = buildRecordItem(record, type);
            inventory.setItem(slot, item);
        }

        if (currentPage > 0) {
            inventory.setItem(previousPageItem.slot(), buildNavigationItem(previousPageItem, "previous"));
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(nextPageItem.slot(), buildNavigationItem(nextPageItem, "next"));
        }

        viewer.openInventory(inventory);
    }

    public void openRecordDetailMenu(Player viewer, InventoryRecordType type, long recordId, String targetName, int page) {
        Optional<? extends StoredInventoryRecord> optionalRecord = findRecordById(type, recordId);
        if (optionalRecord.isEmpty()) {
            messageService.send(viewer, type.detailMessageKey("missing"), Map.of("player", targetName), true);
            openRecordListMenu(viewer, targetName, type, Math.max(0, page));
            return;
        }

        StoredInventoryRecord record = optionalRecord.get();
        Optional<SerializedInventory> serialized = deserializeInventory(record.inventory());
        if (serialized.isEmpty()) {
            messageService.send(viewer, type.detailMessageKey("invalid"), Map.of("player", record.nickname()), true);
            return;
        }

        Map<String, String> titlePlaceholders = Map.of(
                "player", record.nickname(),
                "timestamp", record.timestamp()
        );
        String title = messageService.formatMessage(type.detailMessageKey("title"), titlePlaceholders, false);
        RecordDetailHolder holder = new RecordDetailHolder(detailMenuSize, title, type, record.id(), record.nickname(), page);
        Inventory inventory = holder.getInventory();

        SerializedInventory stored = serialized.get();
        fillContents(inventory, stored.contents());
        fillArmor(inventory, stored.armor());
        fillOffhand(inventory, stored.extra());

        inventory.setItem(restoreActionItem.slot(), buildMenuItem(
                restoreActionItem.material(),
                messageService.formatMessage("inventory.restore.detail.restore.name", Map.of(), false),
                messageService.formatList("inventory.restore.detail.restore.lore", Map.of(), false)
        ));

        inventory.setItem(eraseActionItem.slot(), buildMenuItem(
                eraseActionItem.material(),
                messageService.formatMessage("inventory.restore.detail.erase.name", Map.of(), false),
                messageService.formatList("inventory.restore.detail.erase.lore", Map.of(), false)
        ));

        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof AbstractRestoreInventoryHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory() != topInventory) {
            return;
        }

        int slot = event.getRawSlot();
        if (holder instanceof RestoreMainHolder mainHolder) {
            handleMainMenuClick(player, mainHolder, slot);
        } else if (holder instanceof RecordListHolder listHolder) {
            handleRecordListClick(player, listHolder, slot, event.getCurrentItem());
        } else if (holder instanceof RecordDetailHolder detailHolder) {
            handleRecordDetailClick(player, detailHolder, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AbstractRestoreInventoryHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMainMenuClick(Player player, RestoreMainHolder holder, int slot) {
        if (slot == mainDeathItem.slot()) {
            openRecordListMenu(player, holder.getTargetName(), InventoryRecordType.DEATH, 0);
            return;
        }
        if (slot == mainWorldItem.slot()) {
            openRecordListMenu(player, holder.getTargetName(), InventoryRecordType.WORLD, 0);
            return;
        }
        if (slot == mainTeleportItem.slot()) {
            openRecordListMenu(player, holder.getTargetName(), InventoryRecordType.TELEPORT, 0);
            return;
        }
        if (slot == mainConnectionItem.slot()) {
            openRecordListMenu(player, holder.getTargetName(), InventoryRecordType.CONNECTION, 0);
            return;
        }
        if (slot == mainDisconnectionItem.slot()) {
            openRecordListMenu(player, holder.getTargetName(), InventoryRecordType.DISCONNECTION, 0);
            return;
        }

        messageService.send(player, "inventory.restore.main.unavailable", Map.of(), true);
    }

    private void handleRecordListClick(Player player, RecordListHolder holder, int slot, ItemStack clicked) {
        if (slot == previousPageItem.slot()) {
            openRecordListMenu(player, holder.getTargetName(), holder.getType(), Math.max(0, holder.getPage() - 1));
            return;
        }
        if (slot == nextPageItem.slot()) {
            openRecordListMenu(player, holder.getTargetName(), holder.getType(), holder.getPage() + 1);
            return;
        }

        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Long recordId = container.get(recordIdKey, PersistentDataType.LONG);
        if (recordId == null) {
            return;
        }

        openRecordDetailMenu(player, holder.getType(), recordId, holder.getTargetName(), holder.getPage());
    }

    private void handleRecordDetailClick(Player player, RecordDetailHolder holder, int slot) {
        if (slot == restoreActionItem.slot()) {
            restoreInventory(player, holder);
        } else if (slot == eraseActionItem.slot()) {
            eraseInventory(player, holder);
        }
    }

    private void restoreInventory(Player staff, RecordDetailHolder holder) {
        Optional<? extends StoredInventoryRecord> optionalRecord = findRecordById(holder.getType(), holder.getRecordId());
        if (optionalRecord.isEmpty()) {
            messageService.send(staff, holder.getType().detailMessageKey("missing"), Map.of("player", holder.getTargetName()), true);
            openRecordListMenu(staff, holder.getTargetName(), holder.getType(), holder.getPage());
            return;
        }

        StoredInventoryRecord record = optionalRecord.get();
        if (record.type().usesReturnedFlag() && record.returned()) {
            messageService.send(staff, holder.getType().detailMessageKey("already-returned"), Map.of("player", record.nickname()), true);
            return;
        }

        Optional<SerializedInventory> serialized = deserializeInventory(record.inventory());
        if (serialized.isEmpty()) {
            messageService.send(staff, holder.getType().detailMessageKey("invalid"), Map.of("player", record.nickname()), true);
            return;
        }

        Player target = findTarget(record);
        if (target == null) {
            messageService.send(staff, holder.getType().detailMessageKey("offline"), Map.of("player", record.nickname()), true);
            return;
        }

        List<ItemStack> leftovers = InventorySerializer.restoreInventory(target, serialized.get());
        if (!leftovers.isEmpty()) {
            leftovers.forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        }

        markRecordReturned(record);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", record.nickname());
        placeholders.put("sender", staff.getName());

        messageService.send(staff, holder.getType().detailMessageKey("restored"), placeholders, true);
        messageService.send(target, "command.restore.notify-player", placeholders);

        Bukkit.getScheduler().runTask(plugin, () -> openRecordListMenu(staff, record.nickname(), holder.getType(), holder.getPage()));
    }

    private void eraseInventory(Player staff, RecordDetailHolder holder) {
        Optional<? extends StoredInventoryRecord> optionalRecord = findRecordById(holder.getType(), holder.getRecordId());
        if (optionalRecord.isEmpty()) {
            messageService.send(staff, holder.getType().detailMessageKey("missing"), Map.of("player", holder.getTargetName()), true);
            openRecordListMenu(staff, holder.getTargetName(), holder.getType(), holder.getPage());
            return;
        }

        StoredInventoryRecord record = optionalRecord.get();
        deleteRecord(holder.getType(), record.id());

        messageService.send(staff, holder.getType().detailMessageKey("erased"), Map.of("player", record.nickname()), true);
        Bukkit.getScheduler().runTask(plugin, () -> staff.closeInventory());
    }

    private List<? extends StoredInventoryRecord> fetchRecords(String nickname, InventoryRecordType type, int limit, int offset) {
        return switch (type) {
            case DEATH -> deathRepository.findByNickname(nickname, limit, offset);
            case WORLD -> worldRepository.findByNickname(nickname, limit, offset);
            case TELEPORT -> teleportRepository.findByNickname(nickname, limit, offset);
            case CONNECTION, DISCONNECTION -> snapshotRepository.findByNickname(nickname, type, limit, offset);
        };
    }

    private int countRecords(String nickname, InventoryRecordType type) {
        return switch (type) {
            case DEATH -> deathRepository.countByNickname(nickname);
            case WORLD -> worldRepository.countByNickname(nickname);
            case TELEPORT -> teleportRepository.countByNickname(nickname);
            case CONNECTION, DISCONNECTION -> snapshotRepository.countByNickname(nickname, type);
        };
    }

    private Optional<? extends StoredInventoryRecord> findRecordById(InventoryRecordType type, long id) {
        return switch (type) {
            case DEATH -> deathRepository.findById(id);
            case WORLD -> worldRepository.findById(id);
            case TELEPORT -> teleportRepository.findById(id);
            case CONNECTION, DISCONNECTION -> snapshotRepository.findById(id, type);
        };
    }

    private void deleteRecord(InventoryRecordType type, long id) {
        switch (type) {
            case DEATH -> deathRepository.delete(id);
            case WORLD -> worldRepository.delete(id);
            case TELEPORT -> teleportRepository.delete(id);
            case CONNECTION, DISCONNECTION -> snapshotRepository.delete(id, type);
        }
    }

    private ItemStack buildRecordItem(StoredInventoryRecord record, InventoryRecordType type) {
        Optional<SerializedInventory> serialized = deserializeInventory(record.inventory());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("timestamp", record.timestamp());
        placeholders.put("user", record.nickname());
        placeholders.put("items", Integer.toString(serialized.map(InventorySerializer::countItems).orElse(0)));

        if (record instanceof StoredDeathInventory deathRecord) {
            placeholders.put("death", deathRecord.deathType());
        }
        if (record instanceof StoredWorldInventory worldRecord) {
            placeholders.put("origin", worldRecord.fromWorld());
            placeholders.put("destination", worldRecord.toWorld());
        }
        if (record instanceof StoredTeleportInventory teleportRecord) {
            placeholders.put("origin", teleportRecord.fromLocation());
            placeholders.put("destination", teleportRecord.toLocation());
        }

        if (type == InventoryRecordType.DEATH
                || type == InventoryRecordType.CONNECTION
                || type == InventoryRecordType.DISCONNECTION) {
            placeholders.put("coords", LocationFormats.sanitize(record.location()));
            placeholders.put("world", LocationFormats.sanitize(record.world()));
        }

        if (type.usesReturnedFlag()) {
            applyStatusPlaceholders(placeholders, type, record.returned());
        }

        ItemStack item = new ItemStack(recordMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(layoutService.format(type.key() + ".name", placeholders));
            List<String> lore = layoutService.formatList(type.key() + ".lore", placeholders);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.getPersistentDataContainer().set(recordIdKey, PersistentDataType.LONG, record.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildNavigationItem(MenuConfiguration.MenuItem item, String key) {
        ItemStack navigation = new ItemStack(item.material());
        ItemMeta meta = navigation.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messageService.formatMessage("inventory.restore.list.navigation." + key + ".name", Map.of(), false));
            List<String> lore = messageService.formatList("inventory.restore.list.navigation." + key + ".lore", Map.of(), false);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            navigation.setItemMeta(meta);
        }
        return navigation;
    }

    private Optional<SerializedInventory> deserializeInventory(String serialized) {
        try {
            return Optional.of(InventorySerializer.deserialize(serialized));
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Player findTarget(StoredInventoryRecord record) {
        try {
            UUID uuid = UUID.fromString(record.uuid());
            Player byId = Bukkit.getPlayer(uuid);
            if (byId != null) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
        }

        return Bukkit.getPlayerExact(record.nickname());
    }

    private void markRecordReturned(StoredInventoryRecord record) {
        switch (record.type()) {
            case DEATH -> deathRepository.markInventoryReturned(record.id());
            case WORLD -> worldRepository.markInventoryReturned(record.id());
            case TELEPORT -> teleportRepository.markInventoryReturned(record.id());
            case CONNECTION, DISCONNECTION -> snapshotRepository.markInventoryReturned(record.id(), record.type());
        }
    }

    private void applyStatusPlaceholders(Map<String, String> placeholders, InventoryRecordType type, boolean returned) {
        String returnedStatus = layoutService.format(type.key() + ".status.returned", Collections.emptyMap());
        String pendingStatus = layoutService.format(type.key() + ".status.without-returning", Collections.emptyMap());
        placeholders.put("status", returned ? returnedStatus : pendingStatus);
        placeholders.put("returned", returnedStatus);
        placeholders.put("without returning", pendingStatus);
    }

    private ItemStack buildMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillContents(Inventory inventory, ItemStack[] contents) {
        int limit = Math.min(contents.length, contentSlots.size());
        for (int i = 0; i < limit; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int slot = contentSlots.get(i);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item.clone());
            }
        }
    }

    private void fillArmor(Inventory inventory, ItemStack[] armor) {
        if (armor.length > 0 && armor[0] != null && !armor[0].getType().isAir()) {
            inventory.setItem(bootsSlot, armor[0].clone());
        }
        if (armor.length > 1 && armor[1] != null && !armor[1].getType().isAir()) {
            inventory.setItem(leggingsSlot, armor[1].clone());
        }
        if (armor.length > 2 && armor[2] != null && !armor[2].getType().isAir()) {
            inventory.setItem(chestplateSlot, armor[2].clone());
        }
        if (armor.length > 3 && armor[3] != null && !armor[3].getType().isAir()) {
            inventory.setItem(helmetSlot, armor[3].clone());
        }
    }

    private void fillOffhand(Inventory inventory, ItemStack[] extra) {
        if (extra.length == 0) {
            return;
        }

        ItemStack offhand = extra[0];
        if (offhand == null || offhand.getType().isAir()) {
            return;
        }

        inventory.setItem(offhandSlot, offhand.clone());
    }
}
