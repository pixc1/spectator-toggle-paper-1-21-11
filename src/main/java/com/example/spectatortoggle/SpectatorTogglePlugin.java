package com.example.spectatortoggle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpectatorTogglePlugin extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, SavedState> savedStates = new HashMap<>();
    private final Set<UUID> spectatorRoleMembers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSavedStates();
        loadRoleMembers();

        if (getCommand("spec") != null) {
            getCommand("spec").setExecutor(this);
        }
        if (getCommand("specadmin") != null) {
            getCommand("specadmin").setExecutor(this);
        }
        if (getCommand("specgoto") != null) {
            getCommand("specgoto").setExecutor(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
        removeHostileEntities();
        getLogger().info("SpectatorToggle enabled for Paper 1.21.11.");
    }

    @Override
    public void onDisable() {
        for (Player player : getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            SavedState state = savedStates.get(uuid);
            if (state == null) {
                continue;
            }

            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(state.location());
            }

            // Never leave a stale state behind when the player was already Survival.
            savedStates.remove(uuid);
            removePersistedState(uuid);
        }
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("specadmin")) {
            return handleAdminCommand(sender, args);
        }

        if (commandName.equals("specgoto")) {
            return handleSpectatorGoto(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("هذا الأمر مخصص للاعبين فقط.", NamedTextColor.RED));
            return true;
        }

        if (!canUseSpec(player)) {
            player.sendMessage(Component.text("أمر /spec متاح فقط لأعضاء رتبة Spectator حاليًا.", NamedTextColor.RED));
            return true;
        }

        if (savedStates.containsKey(player.getUniqueId())) {
            exitSpectator(player);
        } else {
            enterSpectator(player);
        }
        return true;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spectator.admin")) {
            sender.sendMessage(Component.text("ليس لديك صلاحية إدارة رتبة Spectator.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                getConfig().set("spectator-role.created", true);
                saveConfig();
                sender.sendMessage(Component.text("تم إنشاء رتبة Spectator داخل البلوقن.", NamedTextColor.GREEN));
            }
            case "mode" -> {
                if (args.length < 2 || (!args[1].equalsIgnoreCase("all") && !args[1].equalsIgnoreCase("role"))) {
                    sender.sendMessage(Component.text("الاستخدام: /specadmin mode all أو /specadmin mode role", NamedTextColor.YELLOW));
                    return true;
                }
                if (args[1].equalsIgnoreCase("role") && !isRoleCreated()) {
                    sender.sendMessage(Component.text("أنشئ الرتبة أولًا باستخدام /specadmin create.", NamedTextColor.RED));
                    return true;
                }
                getConfig().set("spec-access", args[1].toLowerCase());
                saveConfig();
                String message = args[1].equalsIgnoreCase("all")
                        ? "أصبح أمر /spec متاحًا لجميع اللاعبين."
                        : "أصبح أمر /spec متاحًا لأعضاء رتبة Spectator فقط.";
                sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
            }
            case "add", "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("الاستخدام: /specadmin add <اسم اللاعب>", NamedTextColor.YELLOW));
                    return true;
                }
                if (!isRoleCreated()) {
                    sender.sendMessage(Component.text("أنشئ الرتبة أولًا باستخدام /specadmin create.", NamedTextColor.RED));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                spectatorRoleMembers.add(target.getUniqueId());
                saveRoleMembers();
                sender.sendMessage(Component.text("تمت إضافة " + args[1] + " إلى رتبة Spectator.", NamedTextColor.GREEN));
            }
            case "remove", "take" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("الاستخدام: /specadmin remove <اسم اللاعب>", NamedTextColor.YELLOW));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                spectatorRoleMembers.remove(target.getUniqueId());
                saveRoleMembers();
                sender.sendMessage(Component.text("تمت إزالة " + args[1] + " من رتبة Spectator.", NamedTextColor.YELLOW));
            }
            case "list" -> {
                sender.sendMessage(Component.text("أعضاء رتبة Spectator: " + spectatorRoleMembers.size(), NamedTextColor.AQUA));
                for (UUID uuid : spectatorRoleMembers) {
                    OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                    sender.sendMessage(Component.text("- " + (member.getName() == null ? uuid : member.getName()), NamedTextColor.GRAY));
                }
            }
            default -> sendAdminHelp(sender);
        }
        return true;
    }

    private boolean handleSpectatorGoto(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("هذا الأمر مخصص للاعبين فقط.", NamedTextColor.RED));
            return true;
        }
        if (!isRoleMember(player)) {
            player.sendMessage(Component.text("هذا الأمر مخصص لأعضاء رتبة Spectator فقط.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("الاستخدام: /specgoto <اسم لاعب في وضع المشاهدة>", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("اللاعب غير متصل حاليًا.", NamedTextColor.RED));
            return true;
        }
        if (!savedStates.containsKey(target.getUniqueId()) || target.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage(Component.text("اللاعب المحدد ليس في وضع المشاهدة حاليًا.", NamedTextColor.RED));
            return true;
        }

        player.teleport(target.getLocation());
        player.sendMessage(Component.text("تم نقلك إلى مكان اللاعب " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Component.text("أوامر إدارة رتبة Spectator:", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/specadmin create - إنشاء الرتبة", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/specadmin mode all|role - تحديد من يستخدم /spec", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/specadmin add <player> - إعطاء الرتبة", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/specadmin remove <player> - إزالة الرتبة", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/specadmin list - عرض الأعضاء", NamedTextColor.GRAY));
    }

    private boolean canUseSpec(Player player) {
        String access = getConfig().getString("spec-access", "all");
        return !access.equalsIgnoreCase("role") || isRoleMember(player);
    }

    private boolean isRoleCreated() {
        return getConfig().getBoolean("spectator-role.created", false);
    }

    private boolean isRoleMember(Player player) {
        return isRoleCreated() && spectatorRoleMembers.contains(player.getUniqueId());
    }

    private void loadRoleMembers() {
        spectatorRoleMembers.clear();
        for (String value : getConfig().getStringList("spectator-role.members")) {
            try {
                spectatorRoleMembers.add(UUID.fromString(value));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("تعذر تحميل عضو رتبة Spectator: " + value);
            }
        }
    }

    private void saveRoleMembers() {
        List<String> members = new ArrayList<>();
        for (UUID uuid : spectatorRoleMembers) {
            members.add(uuid.toString());
        }
        getConfig().set("spectator-role.members", members);
        saveConfig();
    }

    private void enterSpectator(Player player) {
        SavedState state = new SavedState(player.getLocation().clone(), player.getGameMode());
        savedStates.put(player.getUniqueId(), state);
        persistState(player.getUniqueId(), state);
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(Component.text("تم تفعيل وضع المشاهدة. استخدم /spec للعودة.", NamedTextColor.GREEN));
    }

    private void exitSpectator(Player player) {
        SavedState state = savedStates.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        removePersistedState(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(state.location());
        player.sendMessage(Component.text("تم إيقاف وضع المشاهدة وإعادتك إلى مكانك السابق في Survival.", NamedTextColor.YELLOW));
    }

    private void loadSavedStates() {
        if (!getConfig().isConfigurationSection("players")) {
            return;
        }

        for (String key : getConfig().getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Location location = getConfig().getLocation("players." + key + ".location");
                String modeName = getConfig().getString("players." + key + ".game-mode", GameMode.SURVIVAL.name());
                if (location != null) {
                    savedStates.put(uuid, new SavedState(location, GameMode.valueOf(modeName)));
                }
            } catch (IllegalArgumentException exception) {
                getLogger().warning("تعذر تحميل حالة اللاعب: " + key);
            }
        }
    }

    private void persistState(UUID uuid, SavedState state) {
        String path = "players." + uuid;
        getConfig().set(path + ".location", state.location());
        getConfig().set(path + ".game-mode", state.gameMode().name());
        saveConfig();
    }

    private void removePersistedState(UUID uuid) {
        getConfig().set("players." + uuid, null);
        saveConfig();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Apply after the player has fully joined. Creative is the only mode preserved.
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (player.getGameMode() != GameMode.CREATIVE) {
                if (player.getGameMode() != GameMode.SURVIVAL) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage(Component.text("تم تحويلك تلقائيًا إلى Survival عند الدخول.", NamedTextColor.YELLOW));
                }
            }

            // Always clear old Spectator records on join so they cannot affect future logins.
            savedStates.remove(uuid);
            removePersistedState(uuid);
        });
    }

    @EventHandler
    public void onEnemySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Enemy) {
            event.setCancelled(true);
        }
    }

    private void removeHostileEntities() {
        getServer().getWorlds().forEach(world ->
                world.getEntities().stream()
                        .filter(entity -> entity instanceof Enemy)
                        .forEach(entity -> entity.remove())
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (savedStates.containsKey(event.getPlayer().getUniqueId())) {
            persistState(event.getPlayer().getUniqueId(), savedStates.get(event.getPlayer().getUniqueId()));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (savedStates.containsKey(player.getUniqueId())) {
            exitSpectator(player);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (savedStates.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("أنت ما زلت في وضع المشاهدة. استخدم /spec للعودة إلى مكان التفعيل.", NamedTextColor.AQUA));
        }
    }

    private record SavedState(Location location, GameMode gameMode) {
    }
}
