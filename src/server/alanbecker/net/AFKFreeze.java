package server.alanbecker.net;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class AFKFreeze implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private final Set<Player> frozenPlayers = new HashSet<>();

    public AFKFreeze(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin.getCommand("afkfreeze").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /afkfreeze <playername>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        if (frozenPlayers.contains(target)) {
            frozenPlayers.remove(target);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " has been unfrozen.");
        } else {
            frozenPlayers.add(target);
            sender.sendMessage(ChatColor.RED + target.getName() + " has been frozen.");
        }

        return true;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.contains(player)) {
            event.setCancelled(true);
        }
    }
}
