package server.alanbecker.net;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public final class Main extends JavaPlugin implements Listener, CommandExecutor {
    private static boolean loopBool = true;

    private static final Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");

    private final List<UUID> afkPlayers = new ArrayList<>();
    private final Map<UUID, Location> locations = new HashMap<>();

    private File customConfigFile;
    private FileConfiguration customConfig;
    private File datafile;
    private FileConfiguration data;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getConfig().options().copyDefaults(true);
        saveConfig();
        createCustomConfig();

        loadAfkPlayers();

        File loc = new File(getDataFolder(), "locations.yml");
        try {
            loc.createNewFile();
            loadLocations(loc);
        } catch (IOException e) {
            e.printStackTrace();
        }

        updateAfkPlayersAndLocations();

        checkLoop();
        getLogger().info(ChatColor.GREEN + "AFK Room Enabled");
    }

    @Override
    public void onDisable() {
        loopBool = false;
        saveConfig();
        getCustomConfig().set("afkPlayers", new ArrayList<>(afkPlayers));

        saveLocations();

        getLogger().info(ChatColor.RED + "AFK Room Disabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID playerId = player.getUniqueId();

        if (afkPlayers.contains(playerId) || locations.containsKey(playerId)) {
            synchronized (e) {
                player.teleport(locations.get(playerId));
            }
            afkPlayers.remove(playerId);
            locations.remove(playerId);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("afkroom")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players Only!");
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "Essentials AFK Room [Page 1/1]");
                sender.sendMessage(ChatColor.GREEN + "/afkroom set" + ChatColor.GOLD + " // Sets the AFKRoom location");
                sender.sendMessage(ChatColor.GREEN + "/afkroom tp" + ChatColor.GOLD + " // Teleports you to the AFKRoom (without setting you as AFK)");
                sender.sendMessage(ChatColor.GREEN + "/afkroom reload" + ChatColor.GOLD + " // Reloads the config");
                sender.sendMessage(ChatColor.GOLD + "------------------------------");
            } else if (args[0].equalsIgnoreCase("set")) {
                setLocation(player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Location saved!");
            } else if (args[0].equalsIgnoreCase("tp")) {
                teleportToAfkRoom(player);
                sender.sendMessage(ChatColor.GREEN + "Woosh! Teleported");
            } else if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage("Reloaded!");
            }
            return true;
        }
        return false;
    }

    public FileConfiguration getCustomConfig() {
        return this.customConfig;
    }

    private void createCustomConfig() {
        this.customConfigFile = new File(getDataFolder(), "afkUsers.yml");
        if (!this.customConfigFile.exists()) {
            this.customConfigFile.getParentFile().mkdirs();
            saveResource("afkUsers.yml", false);
        }
        this.customConfig = YamlConfiguration.loadConfiguration(this.customConfigFile);
    }

    private void loadAfkPlayers() {
        List<String> afkStrings = getCustomConfig().getStringList("afkPlayers");
        for (String afkString : afkStrings) {
            afkPlayers.add(UUID.fromString(afkString));
        }
    }

    private void loadLocations(File loc) {
        Properties pro = new Properties();
        try (FileInputStream fos = new FileInputStream(loc)) {
            pro.load(fos);
            pro.forEach((k, v) -> {
                String[] arg = v.toString().split(",");
                double[] parsed = new double[3];
                for (int a = 0; a < 3; a++)
                    parsed[a] = Double.parseDouble(arg[a + 1]);
                Location location = new Location(Bukkit.getWorld(arg[0]), parsed[0], parsed[1], parsed[2]);
                locations.put(UUID.fromString((String) k), location);
            });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveLocations() {
        Properties p = new Properties();
        locations.forEach((k, v) -> p.setProperty(k.toString(), v.getWorld().getName() + "," + v.getX() + "," + v.getY() + "," + v.getZ()));
        try (FileOutputStream fos = new FileOutputStream(getDataFolder() + "/locations.yml")) {
            p.store(fos, null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateAfkPlayersAndLocations() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            User u = ess.getUser(p);
            if (u != null && u.isAfk()) {
                if (!afkPlayers.contains(p.getUniqueId())) {
                    afkPlayers.add(p.getUniqueId());
                    if (!locations.containsKey(p.getUniqueId()))
                        locations.put(p.getUniqueId(), p.getLocation());
                }
                continue;
            }

            if (u != null) {
                if (afkPlayers.contains(p.getUniqueId()))
                    afkPlayers.remove(p.getUniqueId());
                locations.remove(p.getUniqueId());
            }
        }
    }

    private void checkLoop() {
        Thread t = new Thread(() -> Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (loopBool) {
                updateAfkPlayersAndLocations();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    User u = ess.getUser(p);
                    if (u != null && u.isAfk()) {
                        teleportToAfkRoom(p);
                    }
                }
            }
        }, 0L, 20L));
        t.start();
    }

    private void setLocation(Location location) {
        getConfig().set("tpX", location.getX());
        getConfig().set("tpY", location.getY());
        getConfig().set("tpZ", location.getZ());
        saveConfig();
        reloadConfig();
    }

    private void teleportToAfkRoom(Player player) {
        Optional<World> worldOptional = Optional.ofNullable(getServer().getWorld(Objects.requireNonNull(getConfig().getString("world"))));
        if (!worldOptional.isPresent()) {
            getLogger().warning("World: " + getConfig().getString("world") + " does not exist! Defaulting to world named world");
            worldOptional = Optional.ofNullable(getServer().getWorld(Objects.requireNonNull(getConfig().getString("world"))));
        }

        double tpX = getConfig().getDouble("tpX");
        double tpY = getConfig().getDouble("tpY");
        double tpZ = getConfig().getDouble("tpZ");

        Location cloc = new Location(worldOptional.get(), tpX, tpY, tpZ);
        synchronized (player) {
            player.teleport(cloc);
            sendAfkTitle(player);
        }
    }

    private void sendAfkTitle(Player player) {
        player.sendTitle(ChatColor.RED + "AFK", ChatColor.YELLOW + "You're in AFK Prison! Type /spawn to free yourself!", 10, 70, 20);
    }
}
