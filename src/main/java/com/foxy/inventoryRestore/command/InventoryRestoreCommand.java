package com.foxy.inventoryRestore.command;

import com.foxy.inventoryRestore.database.DeathInventoryRepository;
import com.foxy.inventoryRestore.database.PlayerSnapshotRepository;
import com.foxy.inventoryRestore.database.TeleportRepository;
import com.foxy.inventoryRestore.database.WorldChangeRepository;
import com.foxy.inventoryRestore.inventory.menu.InventoryRecordType;
import com.foxy.inventoryRestore.inventory.menu.RestoreMenuManager;
import com.foxy.inventoryRestore.inventory.view.InventoryViewManager;
import org.bukkit.Bukkit;
import com.foxy.inventoryRestore.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class InventoryRestoreCommand implements CommandExecutor, TabCompleter {

    private static final String BASE_PERMISSION = "inventoryrestore.use";
    private static final String RESTORE_PERMISSION = "inventoryrestore.restore";
    private static final String RELOAD_PERMISSION = "inventoryrestore.reload";
    private static final String VIEW_PERMISSION = "inventoryrestore.view";
    private static final String VIEW_MODIFY_PERMISSION = "inventoryrestore.view.use";
    private static final String RESTORE_SUBCOMMAND = "restore";
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final String VIEW_SUBCOMMAND = "view";
    private static final Duration NICKNAME_CACHE_TTL = Duration.ofSeconds(5);

    private final MessageService messageService;
    private final DeathInventoryRepository deathRepository;
    private final PlayerSnapshotRepository snapshotRepository;
    private final WorldChangeRepository worldRepository;
    private final TeleportRepository teleportRepository;
    private final RestoreMenuManager menuManager;
    private final InventoryViewManager viewManager;
    private final Runnable reloadAction;
    private volatile CachedNicknames nicknameCache = new CachedNicknames(Collections.emptyList(), Instant.EPOCH);

    public InventoryRestoreCommand(MessageService messageService,
                                   DeathInventoryRepository deathRepository,
                                   PlayerSnapshotRepository snapshotRepository,
                                   WorldChangeRepository worldRepository,
                                   TeleportRepository teleportRepository,
                                   RestoreMenuManager menuManager,
                                   InventoryViewManager viewManager,
                                   Runnable reloadAction) {
        this.messageService = messageService;
        this.deathRepository = deathRepository;
        this.snapshotRepository = snapshotRepository;
        this.worldRepository = worldRepository;
        this.teleportRepository = teleportRepository;
        this.menuManager = menuManager;
        this.viewManager = viewManager;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(BASE_PERMISSION)) {
            messageService.send(sender, "command.no-permission");
            return true;
        }

        if (args.length == 0) {
            messageService.sendList(sender, "command.help");
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ENGLISH);
        switch (subcommand) {
            case "help" -> messageService.sendList(sender, "command.help");
            case RESTORE_SUBCOMMAND -> handleRestore(sender, args);
            case VIEW_SUBCOMMAND -> handleView(sender, args);
            case RELOAD_SUBCOMMAND -> handleReload(sender);
            default -> messageService.send(sender, "command.unknown-subcommand");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            messageService.send(sender, "command.no-permission");
            return;
        }

        try {
            reloadAction.run();
            messageService.send(sender, "command.reload.success");
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private void handleRestore(CommandSender sender, String[] args) {
        if (!sender.hasPermission(RESTORE_PERMISSION)) {
            messageService.send(sender, "command.no-permission");
            return;
        }

        if (args.length != 2) {
            messageService.send(sender, "command.restore.usage");
            return;
        }

        String targetName = args[1];

        if (!(sender instanceof Player player)) {
            messageService.send(sender, "command.restore.only-players");
            return;
        }

        if (!hasAnyRecords(targetName)) {
            messageService.send(sender, "command.restore.no-record-any", Map.of("player", targetName));
            return;
        }

        menuManager.openMainMenu(player, targetName);
    }

    private void handleView(CommandSender sender, String[] args) {
        if (!sender.hasPermission(VIEW_PERMISSION)) {
            messageService.send(sender, "command.no-permission");
            return;
        }

        if (!(sender instanceof Player player)) {
            messageService.send(sender, "command.view.only-players");
            return;
        }

        if (args.length != 2) {
            messageService.send(sender, "command.view.usage");
            return;
        }

        String targetName = args[1];
        boolean canModify = sender.hasPermission(VIEW_MODIFY_PERMISSION);
        viewManager.openInventoryView(player, targetName, canModify);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(BASE_PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            List<String> subcommands = new ArrayList<>();
            subcommands.add("help");
            if (sender.hasPermission(RESTORE_PERMISSION)) {
                subcommands.add(RESTORE_SUBCOMMAND);
            }
            if (sender.hasPermission(VIEW_PERMISSION)) {
                subcommands.add(VIEW_SUBCOMMAND);
            }
            if (sender.hasPermission(RELOAD_PERMISSION)) {
                subcommands.add(RELOAD_SUBCOMMAND);
            }
            StringUtil.copyPartialMatches(args[0], subcommands, options);
            Collections.sort(options, String.CASE_INSENSITIVE_ORDER);
            return options;
        }

        String subcommand = args[0].toLowerCase(Locale.ENGLISH);
        if ((RESTORE_SUBCOMMAND.equals(subcommand) && sender.hasPermission(RESTORE_PERMISSION))
                || (VIEW_SUBCOMMAND.equals(subcommand) && sender.hasPermission(VIEW_PERMISSION))) {
            if (args.length == 2) {
                List<String> names = getAllKnownNicknames();
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], names, completions);
                completions.sort(String.CASE_INSENSITIVE_ORDER);
                return completions;
            }
        }

        return Collections.emptyList();
    }

    private List<String> getAllKnownNicknames() {
        Instant now = Instant.now();
        CachedNicknames cache = nicknameCache;
        if (cache != null && now.isBefore(cache.expiresAt())) {
            return cache.names();
        }

        Set<String> names = new HashSet<>(deathRepository.findAllNicknames());
        names.addAll(worldRepository.findAllNicknames());
        names.addAll(teleportRepository.findAllNicknames());
        names.addAll(snapshotRepository.findAllNicknames(InventoryRecordType.CONNECTION));
        names.addAll(snapshotRepository.findAllNicknames(InventoryRecordType.DISCONNECTION));
        Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));

        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> cached = Collections.unmodifiableList(sorted);
        nicknameCache = new CachedNicknames(cached, now.plus(NICKNAME_CACHE_TTL));
        return cached;
    }

    private record CachedNicknames(List<String> names, Instant expiresAt) {
    }

    private boolean hasAnyRecords(String nickname) {
        if (deathRepository.hasRecords(nickname)) {
            return true;
        }
        if (worldRepository.hasRecords(nickname)) {
            return true;
        }
        if (teleportRepository.hasRecords(nickname)) {
            return true;
        }
        if (snapshotRepository.hasRecords(nickname, InventoryRecordType.CONNECTION)) {
            return true;
        }
        return snapshotRepository.hasRecords(nickname, InventoryRecordType.DISCONNECTION);
    }

}
