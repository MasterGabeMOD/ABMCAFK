package server.alanbecker.net;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;

import java.util.ArrayList;
import java.util.List;

import java.util.HashMap;
import java.util.Map;

public class AFK extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final String IGNORE_PERMISSION = "abmc.afk.ignore";

    private final Map<Player, Long> lastMoveTimeMap = new HashMap<>();
    private final Map<Player, Long> joinTimeMap = new HashMap<>();
    private final Map<Player, Boolean> afkStatusMap = new HashMap<>();
    private final Map<Player, BukkitTask> afkTitleTasks = new HashMap<>();
    private final Map<Player, Long> afkStartTimeMap = new HashMap<>();
    private List<PotionEffect> afkPotionEffects;

    private int afkTimeThreshold;
    private String afkTitle;
    private String afkSubtitle;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        getLogger().info("AFKPlugin has been enabled!");
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("afkcheck").setExecutor(this);
        this.getCommand("afkcheck").setTabCompleter(this);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::checkAFKPlayersAsync, 20L, 20L);
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        afkTimeThreshold = config.getInt("afk-time-threshold", 120);
        afkTitle = ChatColor.translateAlternateColorCodes('&', config.getString("afk-title", "&cYou're AFK!"));
        afkSubtitle = ChatColor.translateAlternateColorCodes('&', config.getString("afk-subtitle", "&eMove to become visible again."));

        afkPotionEffects = new ArrayList<>();
        List<Map<?, ?>> effectsConfig = config.getMapList("afk-potion-effects");
        for (Map<?, ?> effectConfig : effectsConfig) {
            PotionEffectType type = PotionEffectType.getByName((String) effectConfig.get("type"));
            int duration = (Integer) effectConfig.get("duration");
            int amplifier = (Integer) effectConfig.get("amplifier");
            boolean visible = (Boolean) effectConfig.get("visible");
            afkPotionEffects.add(new PotionEffect(type, duration, amplifier, false, visible));
        }
    }
	    @Override
	    public void onDisable() {
	        getLogger().info("AFKPlugin has been disabled!");
	        afkTitleTasks.values().forEach(BukkitTask::cancel);
	        afkTitleTasks.clear();
	    }
    
	    @Override
	    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
	        if (command.getName().equalsIgnoreCase("afkcheck")) {
	            if (args.length == 0) {
	                sender.sendMessage(ChatColor.RED + "Usage: /afkcheck <player|reload>");
	                return true;
	            }
	            
	            if (args[0].equalsIgnoreCase("reload")) {
	                if (!sender.hasPermission("abmc.afk.reload")) {
	                    sender.sendMessage(ChatColor.RED + "You do not have permission to reload the configuration.");
	                    return true;
	                }
	                
	                reloadConfig();
	                loadConfig();
	                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
	                return true;
	            }
	            
	            if (!sender.hasPermission("abmc.afk.mod")) {
	                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
	                return true;
	            }
	            
	            Player target = getServer().getPlayer(args[0]);
	            if (target == null) {
	                sender.sendMessage(ChatColor.RED + "Player not found.");
	                return true;
	            }
	            
	            Long lastMoveTime = lastMoveTimeMap.getOrDefault(target, System.currentTimeMillis());
	            long timeSinceLastMove = (System.currentTimeMillis() - lastMoveTime) / 1000;
	            
	            Long afkStartTime = afkStartTimeMap.getOrDefault(target, 0L);
	            if (afkStartTime == 0L) {
	                sender.sendMessage(ChatColor.GOLD + target.getName() + " is not AFK. Last move: " + timeSinceLastMove + " seconds ago.");
	            } else {
	                long afkDurationInSeconds = (System.currentTimeMillis() - afkStartTime) / 1000;
	                sender.sendMessage(ChatColor.GOLD + target.getName() + " has been AFK for " + afkDurationInSeconds + " seconds. Last move: " + timeSinceLastMove + " seconds ago.");
	            }
	        }
	        return true;
	    }


	    @Override
	    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
	        if (command.getName().equalsIgnoreCase("afkcheck") && args.length == 1) {
	            List<String> playerNames = new ArrayList<>();
	            String partialPlayerName = args[0].toLowerCase();

	            for (Player player : getServer().getOnlinePlayers()) {
	                String playerName = player.getName();
	                if (playerName.toLowerCase().startsWith(partialPlayerName)) {
	                    playerNames.add(playerName);
	                }
	            }

	            return playerNames;
	        }
	        return null;
	    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            resetAFKStatus(player);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            resetAFKStatus(player);
        }
    }
    
    private void resetAFKStatus(Player player) {
        if (player.hasPermission(IGNORE_PERMISSION)) {
            return;
        }

        lastMoveTimeMap.put(player, System.currentTimeMillis());
        setAFKStatus(player, false);
    }
    
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        resetAFKStatus(player);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission(IGNORE_PERMISSION)) {
            return;
        }

        lastMoveTimeMap.put(player, System.currentTimeMillis());

        if (isAFK(player) && !hasRecentlyJoined(player)) {
            setAFKStatus(player, true);
        } else {
            setAFKStatus(player, false);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joinTimeMap.put(player, System.currentTimeMillis());
        applyAFKPotionEffects(player, false);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        setAFKStatus(player, false);
        afkTitleTasks.remove(player); 
        afkStatusMap.remove(player);
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        resetAFKStatus(player);
    }

    private void checkAFKPlayersAsync() {
        Bukkit.getScheduler().runTask(this, this::checkAFKPlayers);
    }

    private void checkAFKPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(IGNORE_PERMISSION) && isAFK(player) && !hasRecentlyJoined(player)) {
                setAFKStatus(player, true);
            }
        }
    }

    private boolean hasRecentlyJoined(Player player) {
        return System.currentTimeMillis() - joinTimeMap.getOrDefault(player, 0L) < 120000;
    }

    private boolean isAFK(Player player) {
        return System.currentTimeMillis() - lastMoveTimeMap.getOrDefault(player, 0L) > afkTimeThreshold * 1000;
    }

    private void setAFKStatus(Player player, boolean afk) {
        Long afkStartTime = afkStartTimeMap.getOrDefault(player, 0L);

        if (afk) {
            if (afkStartTime == 0L) { 
                afkStartTimeMap.put(player, System.currentTimeMillis());
                applyAFKPotionEffects(player, true);
                BukkitTask titleTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                    player.sendTitle(afkTitle, afkSubtitle, 10, 100, 20);
                }, 0L, 40L); 
                afkTitleTasks.put(player, titleTask);
            }
        } else {
            if (afkStartTime != 0L) { 
                applyAFKPotionEffects(player, false);
                player.sendTitle("", "", 0, 0, 0);
                BukkitTask titleTask = afkTitleTasks.remove(player);
                if (titleTask != null) {
                    titleTask.cancel();
                }
                afkStartTimeMap.remove(player);
            }
        }
    }

    private void applyAFKPotionEffects(Player player, boolean apply) {
        if (apply) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
            afkPotionEffects.forEach(player::addPotionEffect);
        } else {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            afkPotionEffects.forEach(effect -> player.removePotionEffect(effect.getType()));
        }
    }

}