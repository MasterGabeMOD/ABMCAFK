package server.alanbecker.net;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class Main extends JavaPlugin implements Listener {

    private static final int AFK_TIME_THRESHOLD = 120; 
    private static final String IGNORE_PERMISSION = "abmc.afk.ignore";

    private final Map<Player, Long> lastMoveTimeMap = new HashMap<>();
    private final Map<Player, Long> joinTimeMap = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("AFKPlugin has been enabled!");
        getServer().getPluginManager().registerEvents(this, this);


        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::checkAFKPlayersAsync, 20L, 20L);
    }

    @Override
    public void onDisable() {
        getLogger().info("AFKPlugin has been disabled!");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission(IGNORE_PERMISSION)) {
            return;
        }

 
        lastMoveTimeMap.put(player, System.currentTimeMillis());


        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendTitle("", "", 0, 0, 0);
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
        return System.currentTimeMillis() - lastMoveTimeMap.getOrDefault(player, 0L) > AFK_TIME_THRESHOLD * 1000;
    }

    private void setAFKStatus(Player player, boolean afk) {
        if (afk) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendTitle(
                    ChatColor.RED + "You're AFK!",
                    ChatColor.YELLOW + "Move to get out of spectator!",
                    10, 100, 20
            );
        } else {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendTitle("", "", 0, 0, 0);
        }
    }
}
