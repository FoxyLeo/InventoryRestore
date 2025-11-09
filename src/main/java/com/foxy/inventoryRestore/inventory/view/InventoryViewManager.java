package com.foxy.inventoryRestore.inventory.view;

import com.foxy.inventoryRestore.InventoryRestore;
import com.foxy.inventoryRestore.database.PendingInventoryRepository;
import com.foxy.inventoryRestore.database.PlayerSnapshotRepository;
import com.foxy.inventoryRestore.database.record.StoredPendingInventory;
import com.foxy.inventoryRestore.database.record.StoredPlayerInventory;
import com.foxy.inventoryRestore.inventory.InventorySerializer;
import com.foxy.inventoryRestore.inventory.SerializedInventory;
import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;
import com.foxy.inventoryRestore.inventory.menu.MenuConfiguration;
import com.foxy.inventoryRestore.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InventoryViewManager implements Listener {

    private static final long UPDATE_INTERVAL_TICKS = 5L;

    private final InventoryRestore plugin;
    private final MessageService messageService;
    private final PlayerSnapshotRepository snapshotRepository;
    private final PendingInventoryRepository pendingRepository;
    private final int inventorySize;
    private final List<Integer> contentSlots;
    private final int offhandSlot;
    private final int bootsSlot;
    private final int leggingsSlot;
    private final int chestplateSlot;
    private final int helmetSlot;

    private final Map<UUID, ViewSession> sessions = new HashMap<>();

    public InventoryViewManager(InventoryRestore plugin,
                                MessageService messageService,
                                MenuConfiguration menuConfiguration,
                                PlayerSnapshotRepository snapshotRepository,
                                PendingInventoryRepository pendingRepository) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.snapshotRepository = snapshotRepository;
        this.pendingRepository = pendingRepository;
        this.inventorySize = menuConfiguration.getDeathDetailSize();
        this.contentSlots = menuConfiguration.getContentSlots();
        this.offhandSlot = menuConfiguration.getEquipmentSlot("offhand", 47);
        this.bootsSlot = menuConfiguration.getEquipmentSlot("boots", 48);
        this.leggingsSlot = menuConfiguration.getEquipmentSlot("leggings", 49);
        this.chestplateSlot = menuConfiguration.getEquipmentSlot("chestplate", 50);
        this.helmetSlot = menuConfiguration.getEquipmentSlot("helmet", 51);
    }

    public void openInventoryView(Player viewer, String targetName, boolean canModify) {
        Player onlineTarget = findOnlinePlayer(targetName);
        if (onlineTarget != null) {
            openOnlineInventory(viewer, onlineTarget, canModify);
            return;
        }

        Optional<StoredPendingInventory> pending = pendingRepository.findByNickname(targetName);
        if (pending.isPresent()) {
            openOfflineInventory(viewer, pending.get(), canModify);
            return;
        }

        Optional<StoredPlayerInventory> disconnection = snapshotRepository.findLatest(targetName, InventoryRecordType.DISCONNECTION);
        if (disconnection.isPresent()) {
            openOfflineInventory(viewer, disconnection.get(), canModify);
            return;
        }

        Optional<StoredPlayerInventory> connection = snapshotRepository.findLatest(targetName, InventoryRecordType.CONNECTION);
        if (connection.isPresent()) {
            openOfflineInventory(viewer, connection.get(), canModify);
            return;
        }

        messageService.send(viewer, "command.view.not-found", Map.of("player", targetName));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ViewInventoryHolder holder)) {
            return;
        }

        ViewSession session = sessions.get(holder.getViewerId());
        if (session == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.getViewerId())) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        boolean clickedTop = event.getClickedInventory().equals(event.getView().getTopInventory());
        boolean clickedBottom = event.getClickedInventory().equals(event.getView().getBottomInventory());

        if (clickedTop) {
            if (!session.canModify()) {
                event.setCancelled(true);
                return;
            }
            requestSync(session);
            return;
        }

        if (clickedBottom) {
            if (!session.canModify()) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
                return;
            }

            scheduleSelfInventoryUpdate(player, holder, session, event);

            if (event.isShiftClick()) {
                requestSync(session);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ViewInventoryHolder holder)) {
            return;
        }

        ViewSession session = sessions.get(holder.getViewerId());
        if (session == null) {
            return;
        }

        if (!session.canModify()) {
            event.setCancelled(true);
            return;
        }

        requestSync(session);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ViewInventoryHolder holder)) {
            return;
        }

        endSession(holder.getViewerId(), true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        endSession(event.getPlayer().getUniqueId(), true);
    }

    private void openOnlineInventory(Player viewer, Player target, boolean canModify) {
        SerializedInventory snapshot = InventorySerializer.capture(target.getInventory());
        ViewSession session = createSession(viewer, target.getUniqueId(), target.getName(), canModify, true);
        renderInventory(session.holder.getInventory(), snapshot);
        viewer.openInventory(session.holder.getInventory());
        startUpdates(session);
    }

    private void openOfflineInventory(Player viewer, StoredPendingInventory pending, boolean canModify) {
        SerializedInventory inventory = deserialize(pending.inventory());
        if (inventory == null) {
            messageService.send(viewer, "command.view.invalid", Map.of("player", pending.nickname()));
            return;
        }

        ViewSession session = createSession(viewer, parseUuid(pending.uuid()), pending.nickname(), canModify, false);
        session.offlineInventory = inventory;
        renderInventory(session.holder.getInventory(), inventory);
        viewer.openInventory(session.holder.getInventory());
    }

    private void openOfflineInventory(Player viewer, StoredPlayerInventory record, boolean canModify) {
        SerializedInventory inventory = deserialize(record.inventory());
        if (inventory == null) {
            messageService.send(viewer, "command.view.invalid", Map.of("player", record.nickname()));
            return;
        }

        ViewSession session = createSession(viewer, parseUuid(record.uuid()), record.nickname(), canModify, false);
        session.offlineInventory = inventory;
        renderInventory(session.holder.getInventory(), inventory);
        viewer.openInventory(session.holder.getInventory());
    }

    private ViewSession createSession(Player viewer, UUID targetUuid, String targetName, boolean canModify, boolean online) {
        endSession(viewer.getUniqueId(), true);

        String title = messageService.formatMessage("inventory.view.title", Map.of("player", targetName), false);
        ViewInventoryHolder holder = new ViewInventoryHolder(inventorySize, title, viewer.getUniqueId(), targetName, canModify);
        ViewSession session = new ViewSession(viewer.getUniqueId(), targetUuid, targetName, canModify, online, holder);
        sessions.put(viewer.getUniqueId(), session);
        return session;
    }

    private void startUpdates(ViewSession session) {
        session.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player viewer = Bukkit.getPlayer(session.viewerId);
            if (viewer == null || !viewer.isOnline()) {
                endSession(session.viewerId, true);
                return;
            }
            if (viewer.getOpenInventory().getTopInventory().getHolder() != session.holder) {
                return;
            }

            Player target = getOnlineTarget(session);
            if (target == null) {
                notifyTargetOffline(session);
                return;
            }

            if (session.skipNextUpdate) {
                session.skipNextUpdate = false;
                return;
            }

            SerializedInventory snapshot = InventorySerializer.capture(target.getInventory());
            renderInventory(session.holder.getInventory(), snapshot);
        }, 1L, UPDATE_INTERVAL_TICKS);
    }

    private void requestSync(ViewSession session) {
        session.skipNextUpdate = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!sessions.containsKey(session.viewerId)) {
                return;
            }
            if (session.online) {
                syncOnlineSession(session);
            } else {
                syncOfflineSession(session);
            }
        });
    }

    private void syncOnlineSession(ViewSession session) {
        Player target = getOnlineTarget(session);
        if (target == null) {
            notifyTargetOffline(session);
            return;
        }

        SerializedInventory captured = captureFromView(session.holder.getInventory());
        applyToPlayer(target, captured);
    }

    private void syncOfflineSession(ViewSession session) {
        SerializedInventory captured = captureFromView(session.holder.getInventory());
        session.offlineInventory = captured;
        if (session.targetUuid != null) {
            String serialized = InventorySerializer.serialize(captured);
            pendingRepository.save(session.targetUuid.toString(), session.targetName, serialized);
        }
    }

    private void applyToPlayer(Player player, SerializedInventory inventory) {
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] contents = inventory.contents();
        for (int i = 0; i < storage.length; i++) {
            storage[i] = cloneItem(i < contents.length ? contents[i] : null);
        }
        player.getInventory().setStorageContents(storage);

        ItemStack[] armor = new ItemStack[4];
        ItemStack[] sourceArmor = inventory.armor();
        for (int i = 0; i < armor.length; i++) {
            armor[i] = cloneItem(i < sourceArmor.length ? sourceArmor[i] : null);
        }
        player.getInventory().setArmorContents(armor);

        ItemStack[] extra = inventory.extra();
        ItemStack offhand = extra.length > 0 ? cloneItem(extra[0]) : null;
        player.getInventory().setItemInOffHand(offhand);
        player.updateInventory();
    }

    private void renderInventory(Inventory inventory, SerializedInventory data) {
        clearTrackedSlots(inventory);
        ItemStack[] contents = data.contents();
        int limit = Math.min(contents.length, contentSlots.size());
        for (int i = 0; i < limit; i++) {
            int slot = contentSlots.get(i);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            ItemStack item = cloneItem(contents[i]);
            inventory.setItem(slot, item);
        }

        ItemStack[] armor = data.armor();
        if (armor.length > 0) {
            inventory.setItem(bootsSlot, cloneItem(armor[0]));
        }
        if (armor.length > 1) {
            inventory.setItem(leggingsSlot, cloneItem(armor[1]));
        }
        if (armor.length > 2) {
            inventory.setItem(chestplateSlot, cloneItem(armor[2]));
        }
        if (armor.length > 3) {
            inventory.setItem(helmetSlot, cloneItem(armor[3]));
        }

        ItemStack[] extra = data.extra();
        if (extra.length > 0) {
            inventory.setItem(offhandSlot, cloneItem(extra[0]));
        }
    }

    private void clearTrackedSlots(Inventory inventory) {
        for (int slot : contentSlots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, null);
            }
        }
        if (bootsSlot >= 0 && bootsSlot < inventory.getSize()) {
            inventory.setItem(bootsSlot, null);
        }
        if (leggingsSlot >= 0 && leggingsSlot < inventory.getSize()) {
            inventory.setItem(leggingsSlot, null);
        }
        if (chestplateSlot >= 0 && chestplateSlot < inventory.getSize()) {
            inventory.setItem(chestplateSlot, null);
        }
        if (helmetSlot >= 0 && helmetSlot < inventory.getSize()) {
            inventory.setItem(helmetSlot, null);
        }
        if (offhandSlot >= 0 && offhandSlot < inventory.getSize()) {
            inventory.setItem(offhandSlot, null);
        }
    }

    private SerializedInventory captureFromView(Inventory inventory) {
        ItemStack[] contents = new ItemStack[36];
        int limit = Math.min(contents.length, contentSlots.size());
        for (int i = 0; i < limit; i++) {
            int slot = contentSlots.get(i);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            contents[i] = normalizeItem(inventory.getItem(slot));
        }

        ItemStack[] armor = new ItemStack[4];
        armor[0] = normalizeItem(inventory.getItem(bootsSlot));
        armor[1] = normalizeItem(inventory.getItem(leggingsSlot));
        armor[2] = normalizeItem(inventory.getItem(chestplateSlot));
        armor[3] = normalizeItem(inventory.getItem(helmetSlot));

        ItemStack[] extra = new ItemStack[1];
        extra[0] = normalizeItem(inventory.getItem(offhandSlot));

        return new SerializedInventory(contents, armor, extra);
    }

    private void scheduleSelfInventoryUpdate(Player player,
                                             ViewInventoryHolder holder,
                                             ViewSession session,
                                             InventoryClickEvent event) {
        if (!session.online || session.targetUuid == null || !session.targetUuid.equals(player.getUniqueId())) {
            return;
        }

        if (event.getAction() == InventoryAction.NOTHING) {
            return;
        }

        int playerSlot = mapPlayerInventorySlot(event);
        if (playerSlot < 0) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            ViewSession current = sessions.get(player.getUniqueId());
            if (current == null || current.holder != holder) {
                return;
            }

            if (player.getOpenInventory().getTopInventory().getHolder() != holder) {
                return;
            }

            SerializedInventory snapshot = InventorySerializer.capture(player.getInventory());
            Inventory view = holder.getInventory();
            updateViewSlotFromSnapshot(view, snapshot, playerSlot);
        });
    }

    private int mapPlayerInventorySlot(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            return -1;
        }

        int slot = event.getSlot();
        if (slot >= 0 && slot < 36) {
            return slot;
        }
        if (slot >= 36 && slot <= 39) {
            return slot;
        }
        if (slot == 40) {
            return slot;
        }
        return -1;
    }

    private void updateViewSlotFromSnapshot(Inventory view, SerializedInventory snapshot, int playerSlot) {
        if (playerSlot >= 0 && playerSlot < 36) {
            if (playerSlot < contentSlots.size()) {
                int targetSlot = contentSlots.get(playerSlot);
                setViewSlot(view, targetSlot, snapshot.contents()[playerSlot]);
            }
            return;
        }

        ItemStack[] armor = snapshot.armor();
        switch (playerSlot) {
            case 36 -> setViewSlot(view, bootsSlot, armor.length > 0 ? armor[0] : null);
            case 37 -> setViewSlot(view, leggingsSlot, armor.length > 1 ? armor[1] : null);
            case 38 -> setViewSlot(view, chestplateSlot, armor.length > 2 ? armor[2] : null);
            case 39 -> setViewSlot(view, helmetSlot, armor.length > 3 ? armor[3] : null);
            case 40 -> {
                ItemStack[] extra = snapshot.extra();
                setViewSlot(view, offhandSlot, extra.length > 0 ? extra[0] : null);
            }
            default -> {
            }
        }
    }

    private void setViewSlot(Inventory view, int slot, ItemStack item) {
        if (slot < 0 || slot >= view.getSize()) {
            return;
        }
        view.setItem(slot, cloneItem(item));
    }

    private SerializedInventory deserialize(String serialized) {
        try {
            return InventorySerializer.deserialize(serialized);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return null;
        }
    }

    private Player getOnlineTarget(ViewSession session) {
        if (session.targetUuid != null) {
            Player byId = Bukkit.getPlayer(session.targetUuid);
            if (byId != null) {
                return byId;
            }
        }
        return findOnlinePlayer(session.targetName);
    }

    private Player findOnlinePlayer(String targetName) {
        Player exact = Bukkit.getPlayerExact(targetName);
        if (exact != null) {
            return exact;
        }
        String lowercase = targetName.toLowerCase(Locale.ENGLISH);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ENGLISH).equals(lowercase)) {
                return online;
            }
        }
        return null;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void notifyTargetOffline(ViewSession session) {
        Player viewer = Bukkit.getPlayer(session.viewerId);
        if (viewer != null) {
            messageService.send(viewer, "inventory.view.target-offline", Map.of("player", session.targetName), true);
            viewer.closeInventory();
        }
        endSession(session.viewerId, true);
    }

    private void endSession(UUID viewerId, boolean silent) {
        ViewSession session = sessions.remove(viewerId);
        if (session == null) {
            return;
        }
        if (session.updateTask != null) {
            session.updateTask.cancel();
            session.updateTask = null;
        }
        if (!session.online && session.offlineInventory != null && session.targetUuid != null) {
            String serialized = InventorySerializer.serialize(session.offlineInventory);
            pendingRepository.save(session.targetUuid.toString(), session.targetName, serialized);
        }
        if (!silent) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.getOpenInventory().getTopInventory().getHolder() == session.holder) {
                viewer.closeInventory();
            }
        }
    }

    private ItemStack cloneItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return item.clone();
    }

    private ItemStack normalizeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return item.clone();
    }

    private static final class ViewSession {
        private final UUID viewerId;
        private final UUID targetUuid;
        private final String targetName;
        private final boolean canModify;
        private final ViewInventoryHolder holder;
        private final boolean online;
        private SerializedInventory offlineInventory;
        private org.bukkit.scheduler.BukkitTask updateTask;
        private boolean skipNextUpdate;

        private ViewSession(UUID viewerId,
                            UUID targetUuid,
                            String targetName,
                            boolean canModify,
                            boolean online,
                            ViewInventoryHolder holder) {
            this.viewerId = viewerId;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.canModify = canModify;
            this.online = online;
            this.holder = holder;
        }

        private boolean canModify() {
            return canModify;
        }
    }
}

