package com.foxy.inventoryRestore.inventory;

import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class InventorySerializer {

    private static final int STORAGE_SIZE = 36;

    private InventorySerializer() {
    }

    public static String serialize(PlayerInventory inventory) {
        return serialize(capture(inventory));
    }

    public static SerializedInventory capture(PlayerInventory inventory) {
        ItemStack[] contents = cloneItems(inventory.getStorageContents());
        ItemStack[] armor = cloneItems(inventory.getArmorContents());
        ItemStack[] extra = cloneItems(inventory.getExtraContents());
        return new SerializedInventory(contents, armor, extra);
    }

    public static String serialize(SerializedInventory inventory) {
        try {
            ItemStack[] contents = safeItems(inventory.contents());
            ItemStack[] armor = safeItems(inventory.armor());
            ItemStack[] extra = safeItems(inventory.extra());

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                writeItems(dataOutput, contents);
                writeItems(dataOutput, armor);
                writeItems(dataOutput, extra);
                dataOutput.flush();
                return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize player inventory", exception);
        }
    }

    public static SerializedInventory deserialize(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            throw new IllegalArgumentException("Serialized inventory data cannot be null or empty.");
        }

        byte[] raw = Base64.getDecoder().decode(serialized);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(raw);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            ItemStack[] contents = trimStorage(readItems(dataInput));
            ItemStack[] armor = readItems(dataInput);
            ItemStack[] extra = readItems(dataInput);
            return new SerializedInventory(contents, armor, extra);
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to deserialize player inventory", exception);
        }
    }

    public static boolean isEmpty(PlayerInventory inventory) {
        ItemStack[] contents = safeItems(inventory.getStorageContents());
        ItemStack[] armor = safeItems(inventory.getArmorContents());
        ItemStack[] extra = safeItems(inventory.getExtraContents());

        if (!isAir(inventory.getItemInOffHand())) {
            return false;
        }

        return isEmpty(contents) && isEmpty(armor) && isEmpty(extra);
    }

    public static List<ItemStack> collectItems(SerializedInventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        addItems(items, inventory.contents());
        addItems(items, inventory.armor());
        addItems(items, inventory.extra());
        return items;
    }

    public static ItemStack createShulkerWithContents(List<ItemStack> items) {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        if (!(shulker.getItemMeta() instanceof BlockStateMeta meta)) {
            return shulker;
        }

        if (!(meta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return shulker;
        }

        Inventory inventory = shulkerBox.getInventory();
        inventory.clear();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            Map<Integer, ItemStack> overflow = inventory.addItem(item.clone());
            if (!overflow.isEmpty()) {
                throw new IllegalStateException("Not enough space to store all items inside a single shulker box.");
            }
        }

        shulkerBox.update();
        meta.setBlockState(shulkerBox);
        shulker.setItemMeta(meta);
        return shulker;
    }

    public static List<ItemStack> restoreInventory(Player player, SerializedInventory serialized) {
        PlayerInventory inventory = player.getInventory();

        List<ItemStack> overflow = new ArrayList<>();
        overflow.addAll(applyContents(inventory, serialized.contents()));
        overflow.addAll(applyArmor(inventory, serialized.armor()));
        overflow.addAll(applyExtra(inventory, serialized.extra()));

        if (!overflow.isEmpty()) {
            Map<Integer, ItemStack> remaining = inventory.addItem(overflow.toArray(new ItemStack[0]));
            return new ArrayList<>(remaining.values());
        }

        return Collections.emptyList();
    }

    public static int countItems(SerializedInventory inventory) {
        return countItems(inventory.contents())
                + countItems(inventory.armor())
                + countItems(inventory.extra());
    }

    private static void writeItems(BukkitObjectOutputStream outputStream, ItemStack[] items) throws IOException {
        outputStream.writeInt(items.length);
        for (ItemStack item : items) {
            outputStream.writeObject(item);
        }
    }

    private static ItemStack[] readItems(BukkitObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        int length = inputStream.readInt();
        ItemStack[] items = new ItemStack[length];
        for (int i = 0; i < length; i++) {
            items[i] = (ItemStack) inputStream.readObject();
        }
        return items;
    }

    private static ItemStack[] safeItems(ItemStack[] items) {
        return items == null ? new ItemStack[0] : items;
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] safe = safeItems(items);
        ItemStack[] clone = new ItemStack[safe.length];
        for (int i = 0; i < safe.length; i++) {
            ItemStack item = safe[i];
            clone[i] = item == null ? null : item.clone();
        }
        return clone;
    }

    private static ItemStack[] trimStorage(ItemStack[] items) {
        if (items.length <= STORAGE_SIZE) {
            return items;
        }

        ItemStack[] trimmed = new ItemStack[STORAGE_SIZE];
        System.arraycopy(items, 0, trimmed, 0, STORAGE_SIZE);
        return trimmed;
    }

    private static boolean isEmpty(ItemStack[] items) {
        for (ItemStack item : items) {
            if (!isAir(item)) {
                return false;
            }
        }
        return true;
    }

    private static void addItems(List<ItemStack> target, ItemStack[] items) {
        for (ItemStack item : items) {
            if (!isAir(item)) {
                target.add(item);
            }
        }
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static List<ItemStack> applyContents(PlayerInventory inventory, ItemStack[] contents) {
        if (contents.length == 0) {
            return Collections.emptyList();
        }

        List<ItemStack> filtered = new ArrayList<>();
        addItems(filtered, contents);
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, ItemStack> remaining = inventory.addItem(filtered.toArray(new ItemStack[0]));
        return new ArrayList<>(remaining.values());
    }

    private static List<ItemStack> applyArmor(PlayerInventory inventory, ItemStack[] armor) {
        List<ItemStack> leftovers = new ArrayList<>();
        if (armor.length > 0 && !isAir(armor[0])) {
            leftovers.addAll(applyArmorPiece(inventory::getBoots, inventory::setBoots, armor[0]));
        }
        if (armor.length > 1 && !isAir(armor[1])) {
            leftovers.addAll(applyArmorPiece(inventory::getLeggings, inventory::setLeggings, armor[1]));
        }
        if (armor.length > 2 && !isAir(armor[2])) {
            leftovers.addAll(applyArmorPiece(inventory::getChestplate, inventory::setChestplate, armor[2]));
        }
        if (armor.length > 3 && !isAir(armor[3])) {
            leftovers.addAll(applyArmorPiece(inventory::getHelmet, inventory::setHelmet, armor[3]));
        }
        return leftovers;
    }

    private static List<ItemStack> applyExtra(PlayerInventory inventory, ItemStack[] extra) {
        if (extra.length == 0) {
            return Collections.emptyList();
        }

        List<ItemStack> leftovers = new ArrayList<>();

        if (extra.length > 0) {
            ItemStack offHand = extra[0];
            if (!isAir(offHand)) {
                ItemStack current = inventory.getItemInOffHand();
                if (isAir(current)) {
                    inventory.setItemInOffHand(offHand.clone());
                } else {
                    leftovers.add(offHand.clone());
                }
            }
        }

        for (int i = 1; i < extra.length; i++) {
            ItemStack item = extra[i];
            if (!isAir(item)) {
                leftovers.add(item.clone());
            }
        }

        return leftovers;
    }

    private static List<ItemStack> applyArmorPiece(Supplier<ItemStack> getter, Consumer<ItemStack> setter, ItemStack item) {
        ItemStack current = getter.get();
        if (isAir(current)) {
            setter.accept(item.clone());
            return Collections.emptyList();
        }

        return List.of(item.clone());
    }

    private static int countItems(ItemStack[] items) {
        int count = 0;
        for (ItemStack item : items) {
            if (!isAir(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }
}
