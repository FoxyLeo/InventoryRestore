# 💾 InventoryRestore — Minecraft Plugin

A **Spigot/Paper** plugin designed for staff management and player recovery, providing a full inventory backup and restoration system.  
It automatically saves players’ inventories during critical events (death, teleportation, disconnection, and world changes), and allows staff to **view and restore** them easily through a **modern GUI system**.

---

## ✨ Features
- **Automatic Inventory Snapshots**: captures player inventories during key events:
    - Death
    - World change
    - Teleportation
    - Disconnection/reconnection
- **Restore GUI System**: interactive menu to browse and restore inventories from different categories.
- **Live Inventory View**: inspect online players’ current inventory in real-time (like `/invsee`), with optional edit permissions.
- **Database Integration**: stores inventory snapshots in a local database for persistence across restarts.
- **Customizable Layouts**: all GUI menus are defined via YAML, with editable slots, materials, and names.
- **Configurable Messages**: supports English and Spanish localization, stored in `/messages/en.yml` and `/messages/es.yml`.
- **Auto Cleanup**: automatically deletes old stored inventories after a configurable number of days.
- **Async-safe Operations**: uses an internal task queue to prevent lag from database operations.

---

## 🔧 Commands

All commands are under `/invrestore`:

| Command | Description |
|----------|--------------|
| `/invrestore restore <player>` | Opens the restore GUI for that player’s stored inventories. |
| `/invrestore view <player>` | Opens a live view of the target player’s current inventory. |
| `/invrestore reload` | Reloads all configuration and message files. |
| `/invrestore help` | Displays the available commands and their permissions. |

> Only players or console users with the correct permissions can execute each command.  
> Offline player data can still be accessed for restore menus.

---

## 🔑 Permissions

| Permission | Description |
|-------------|--------------|
| `inventoryrestore.use` | Base permission required for `/invrestore`. |
| `inventoryrestore.restore` | Allows opening the restore GUI and restoring inventories. |
| `inventoryrestore.view` | Allows viewing players’ inventories live. |
| `inventoryrestore.view.use` | Allows editing the target inventory while viewing it. |
| `inventoryrestore.reload` | Allows reloading configuration files. |

> All permissions default to **OP**. Grant them selectively to trusted staff.

---

## 🗃️ Data / Config Files

| File | Purpose |
|-------|----------|
| `config.yml` | Defines general settings (language, auto-erase time, database). |
| `invconfig.yml` | Controls the layout of menus and the positions of GUI items. |
| `inventories.yml` | Defines how stored inventories appear in menus (name, lore, colors). |
| `messages/en.yml` | English message set. |
| `messages/es.yml` | Spanish message set. |
| `database.db` | Stores all serialized inventories. |

---

## 🕹️ Usage Flow

1. **Installation**
    - Drop `InventoryRestore.jar` into your `/plugins/` folder.
    - Start the server once to generate configuration files.
    - Adjust `invconfig.yml`, `inventories.yml`, and `config.yml` to fit your style.

2. **Automatic Tracking**
    - The plugin listens for:
        - **Player death**
        - **World teleportation**
        - **World change**
        - **Player disconnection**
    - Each event automatically saves the player’s inventory snapshot into the database.

3. **Restoration Process**
    - Run `/invrestore restore <player>`.
    - The main GUI displays categories:
        - 💀 Death
        - 🌍 World change
        - 🌀 Teleport
        - 🔌 Connection
    - Click on one to view all available snapshots for that type.
    - From there, open a record to:
        - View its contents.
        - **Restore** the inventory to the player.
        - **Erase** the snapshot from the database.

4. **Live View**
    - `/invrestore view <player>` lets staff inspect a player’s inventory in real time.
    - If you have `inventoryrestore.view.use`, you can modify it directly (move/add/remove items).
---