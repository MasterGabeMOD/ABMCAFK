package server.alanbecker.net;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AFKRotate implements CommandExecutor {

    private final JavaPlugin plugin;
    private final Map<Player, BukkitTask> rotatingPlayers = new HashMap<>();
    private final Random random = new Random();

    public AFKRotate(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getCommand("afkrotate").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("abmc.afk.rotate")) { 
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /afkrotate <playername>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        if (rotatingPlayers.containsKey(target)) {
            rotatingPlayers.remove(target).cancel();
            sender.sendMessage(ChatColor.GREEN + target.getName() + " has stopped rotating.");
        } else {
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    Location loc = target.getLocation();
                    float yaw = random.nextFloat() * 360 - 180; 
                    float pitch = random.nextFloat() * 180 - 90; 
                    loc.setYaw(yaw);
                    loc.setPitch(pitch);
                    target.teleport(loc);
                }
            }.runTaskTimer(plugin, 0L, 60L);
            rotatingPlayers.put(target, task);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " is now rotating.");
        }

        return true;
    }
}
