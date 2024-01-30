package server.alanbecker.net;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.HashMap;
import java.util.Map;

public class Main extends JavaPlugin implements Listener {

	 private static final String IGNORE_PERMISSION = "abmc.afk.ignore";

	    private final Map<Player, Long> lastMoveTimeMap = new HashMap<>();
	    private final Map<Player, Long> joinTimeMap = new HashMap<>();
	    private final Map<Player, Boolean> afkStatusMap = new HashMap<>();
	    private final Map<Player, BukkitTask> afkTitleTasks = new HashMap<>();

	    private int afkTimeThreshold; 
	    private String afkTitle; 
	    private String afkSubtitle;

	    @Override
	    public void onEnable() {
	        saveDefaultConfig(); 
	        loadConfig();

	        getLogger().info("AFKPlugin has been enabled!");
	        getServer().getPluginManager().registerEvents(this, this);

	        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::checkAFKPlayersAsync, 20L, 20L);
	    }

	    private void loadConfig() {
	        FileConfiguration config = getConfig();
	        afkTimeThreshold = config.getInt("afk-time-threshold", 120);
	        afkTitle = ChatColor.translateAlternateColorCodes('&', config.getString("afk-title", "&cYou're AFK!"));
	        afkSubtitle = ChatColor.translateAlternateColorCodes('&', config.getString("afk-subtitle", "&eMove to become visible again."));
	    }
    @Override
    public void onDisable() {
        getLogger().info("AFKPlugin has been disabled!");
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
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        setAFKStatus(player, false);
        afkTitleTasks.remove(player); 
        afkStatusMap.remove(player);
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
        Boolean wasAfk = afkStatusMap.getOrDefault(player, false);

        if (afk) {
            if (!wasAfk) {
                applyAFKPotionEffects(player, true);
                BukkitTask titleTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                    player.sendTitle(afkTitle, afkSubtitle, 10, 100, 20);
                }, 0L, 40L); 
                afkTitleTasks.put(player, titleTask);
                afkStatusMap.put(player, true);
            }
        } else {
            if (wasAfk) {
                applyAFKPotionEffects(player, false);
                player.sendTitle("", "", 0, 0, 0);
                BukkitTask titleTask = afkTitleTasks.remove(player);
                if (titleTask != null) {
                    titleTask.cancel();
                }
                afkStatusMap.put(player, false);
            }
        }
    }

    private void applyAFKPotionEffects(Player player, boolean apply) {
        if (apply) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

}