package com.example.spectatortoggle;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
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

    private final Map<UUID, SavedState> savedStates = new HashMap<UUID, SavedState>();
    private final Set<UUID> spectatorRoleMembers = new HashSet<UUID>();
    private static final Set<String> HOSTILE_ENTITY_TYPES = new HashSet<String>();

    static {
        String[] hostileTypes = {
                "GHAST", "PHANTOM", "SHULKER", "ENDER_DRAGON", "WITHER",
                "RAVAGER", "HOGLIN", "ZOGLIN", "ZOMBIFIED_PIGLIN", "PIGLIN_BRUTE",
                "WARDEN", "BREEZE", "BOGGED", "VEX", "EVOKER", "VINDICATOR",
                "ILLUSIONER", "GUARDIAN", "ELDER_GUARDIAN", "ENDERMITE", "SILVERFISH",
                "CAVE_SPIDER", "SPIDER"
        };
        for (String hostileType : hostileTypes) {
            HOSTILE_ENTITY_TYPES.add(hostileType);
        }
    }

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
        getLogger().info("SpectatorToggle enabled for Paper 1.16.5 and newer.");
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
                player.teleport(state.location);
            }

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

        if (!(sender instanceof Player)) {
            sender.sendMessage("هذا الأمر مخصص للاعبين فقط.");
            return true;
        }

        Player player = (Player) sender;
        if (!canUseSpec(player)) {
            player.sendMessage("أمر /spec متاح فقط لأعضاء رتبة Spectator حاليًا.");
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
            sender.sendMessage("ليس لديك صلاحية إدارة رتبة Spectator.");
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        if (subcommand.equals("create")) {
            getConfig().set("spectator-role.created", true);
            saveConfig();
            sender.sendMessage("تم إنشاء رتبة Spectator داخل البلوقن.");
        } else if (subcommand.equals("mode")) {
            if (args.length < 2 || (!args[1].equalsIgnoreCase("all") && !args[1].equalsIgnoreCase("role"))) {
                sender.sendMessage("الاستخدام: /specadmin mode all أو /specadmin mode role");
                return true;
            }
            if (args[1].equalsIgnoreCase("role") && !isRoleCreated()) {
                sender.sendMessage("أنشئ الرتبة أولًا باستخدام /specadmin create.");
                return true;
            }
            getConfig().set("spec-access", args[1].toLowerCase());
            saveConfig();
            if (args[1].equalsIgnoreCase("all")) {
                sender.sendMessage("أصبح أمر /spec متاحًا لجميع اللاعبين.");
            } else {
                sender.sendMessage("أصبح أمر /spec متاحًا لأعضاء رتبة Spectator فقط.");
            }
        } else if (subcommand.equals("add") || subcommand.equals("give")) {
            if (args.length < 2) {
                sender.sendMessage("الاستخدام: /specadmin add <اسم اللاعب>");
                return true;
            }
            if (!isRoleCreated()) {
                sender.sendMessage("أنشئ الرتبة أولًا باستخدام /specadmin create.");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            spectatorRoleMembers.add(target.getUniqueId());
            saveRoleMembers();
            sender.sendMessage("تمت إضافة " + args[1] + " إلى رتبة Spectator.");
        } else if (subcommand.equals("remove") || subcommand.equals("take")) {
            if (args.length < 2) {
                sender.sendMessage("الاستخدام: /specadmin remove <اسم اللاعب>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            spectatorRoleMembers.remove(target.getUniqueId());
            saveRoleMembers();
            sender.sendMessage("تمت إزالة " + args[1] + " من رتبة Spectator.");
        } else if (subcommand.equals("list")) {
            sender.sendMessage("أعضاء رتبة Spectator: " + spectatorRoleMembers.size());
            for (UUID uuid : spectatorRoleMembers) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                sender.sendMessage("- " + (member.getName() == null ? uuid.toString() : member.getName()));
            }
        } else {
            sendAdminHelp(sender);
        }
        return true;
    }

    private boolean handleSpectatorGoto(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("هذا الأمر مخصص للاعبين فقط.");
            return true;
        }
        Player player = (Player) sender;
        if (!isRoleMember(player)) {
            player.sendMessage("هذا الأمر مخصص لأعضاء رتبة Spectator فقط.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("الاستخدام: /specgoto <اسم لاعب في وضع المشاهدة>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("اللاعب غير متصل حاليًا.");
            return true;
        }
        if (!savedStates.containsKey(target.getUniqueId()) || target.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage("اللاعب المحدد ليس في وضع المشاهدة حاليًا.");
            return true;
        }

        player.teleport(target.getLocation());
        player.sendMessage("تم نقلك إلى مكان اللاعب " + target.getName() + ".");
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("أوامر إدارة رتبة Spectator:");
        sender.sendMessage("/specadmin create - إنشاء الرتبة");
        sender.sendMessage("/specadmin mode all|role - تحديد من يستخدم /spec");
        sender.sendMessage("/specadmin add <player> - إعطاء الرتبة");
        sender.sendMessage("/specadmin remove <player> - إزالة الرتبة");
        sender.sendMessage("/specadmin list - عرض الأعضاء");
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
        List<String> members = new ArrayList<String>();
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
        player.sendMessage("تم تفعيل وضع المشاهدة. استخدم /spec للعودة.");
    }

    private void exitSpectator(Player player) {
        SavedState state = savedStates.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        removePersistedState(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(state.location);
        player.sendMessage("تم إيقاف وضع المشاهدة وإعادتك إلى مكانك السابق في Survival.");
    }

    private void loadSavedStates() {
        if (!getConfig().isConfigurationSection("players")) {
            return;
        }

        for (String key : getConfig().getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Location location = getConfig().getLocation("players." + key + ".location");
                if (location != null) {
                    savedStates.put(uuid, new SavedState(location, GameMode.SURVIVAL));
                }
            } catch (IllegalArgumentException exception) {
                getLogger().warning("تعذر تحميل حالة اللاعب: " + key);
            }
        }
    }

    private void persistState(UUID uuid, SavedState state) {
        String path = "players." + uuid;
        getConfig().set(path + ".location", state.location);
        getConfig().set(path + ".game-mode", state.gameMode.name());
        saveConfig();
    }

    private void removePersistedState(UUID uuid) {
        getConfig().set("players." + uuid, null);
        saveConfig();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        getServer().getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }

                if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SURVIVAL) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage("تم تحويلك تلقائيًا إلى Survival عند الدخول.");
                }

                savedStates.remove(uuid);
                removePersistedState(uuid);
            }
        });
    }

    @EventHandler
    public void onEnemySpawn(EntitySpawnEvent event) {
        if (isHostile(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void removeHostileEntities() {
        getServer().getWorlds().forEach(world ->
                world.getEntities().stream()
                        .filter(this::isHostile)
                        .forEach(Entity::remove)
        );
    }

    private boolean isHostile(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return entity instanceof Monster
                || entity instanceof Slime
                || HOSTILE_ENTITY_TYPES.contains(entity.getType().name());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (savedStates.containsKey(uuid)) {
            persistState(uuid, savedStates.get(uuid));
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
            player.sendMessage("أنت ما زلت في وضع المشاهدة. استخدم /spec للعودة إلى مكان التفعيل.");
        }
    }

    private static final class SavedState {
        private final Location location;
        private final GameMode gameMode;

        private SavedState(Location location, GameMode gameMode) {
            this.location = location;
            this.gameMode = gameMode;
        }
    }
}
