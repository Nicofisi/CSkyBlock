package us.tastybento.bskyblock;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import us.tastybento.bskyblock.commands.AdminCommand;
import us.tastybento.bskyblock.commands.IslandCommand;
import us.tastybento.bskyblock.config.BSBLocale;
import us.tastybento.bskyblock.config.LocaleManager;
import us.tastybento.bskyblock.config.PluginConfig;
import us.tastybento.bskyblock.config.Settings;
import us.tastybento.bskyblock.database.BSBDatabase;
import us.tastybento.bskyblock.database.managers.OfflineHistoryMessages;
import us.tastybento.bskyblock.database.managers.PlayersManager;
import us.tastybento.bskyblock.database.managers.island.IslandsManager;
import us.tastybento.bskyblock.generators.IslandWorld;
import us.tastybento.bskyblock.listeners.JoinLeaveListener;
import us.tastybento.bskyblock.listeners.NetherPortals;
import us.tastybento.bskyblock.listeners.PanelListener;
import us.tastybento.bskyblock.listeners.protection.IslandGuard;
import us.tastybento.bskyblock.listeners.protection.IslandGuard1_8;
import us.tastybento.bskyblock.listeners.protection.IslandGuard1_9;
import us.tastybento.bskyblock.listeners.protection.NetherEvents;
import us.tastybento.bskyblock.util.Util;
import us.tastybento.bskyblock.util.VaultHelper;
import us.tastybento.bskyblock.util.nms.NMSAbstraction;

/**
 * Main BSkyBlock class - provides an island minigame in the sky
 * @author Tastybento
 * @author Poslovitch
 */
public class BSkyBlock extends JavaPlugin {

    private static BSkyBlock plugin;

    // Supported Minecraft versions
    private final String[] supportedVersions = {"v_1_12"};
    private boolean incompatible;

    // Databases
    private PlayersManager playersManager;
    private IslandsManager islandsManager;
    private OfflineHistoryMessages offlineHistoryMessages;

    // Metrics
    private Metrics metrics;

    // Commands
    private IslandCommand islandCommand;
    private AdminCommand adminCommand;

    protected LocaleManager localeManager;

    @Override
    public void onEnable(){
        plugin = this;

        // Check if the server version is supported by BSkyBlock
        String currentVersion = Util.getServerVersion();
        boolean compatible = false;

        StringBuilder versions = new StringBuilder();

        int i = 0;
        for (String version : supportedVersions) {
            if (currentVersion.startsWith(version)) compatible = true;

            if (i == 0) versions.append(version.substring(1).replaceFirst("_", ".").replace("_", ".X"));
            else if (i == supportedVersions.length - 1) versions.append(" or " + version.substring(1).replaceFirst("_", ".").replace("_", ".X"));
            else versions.append(", " + version.substring(1).replaceFirst("_", ".").replace("_", ".X"));

            i++;
        }

        if (true) {
            incompatible = !compatible;
            // Sorry, but BSkyBlock does not support this version yet :/
            // For safety reasons, it will shutdown.
            System.err.println("### BSkyBlock is NOT compatible with this version.");
            System.err.println("###");
            System.err.println("### Hopefully you'll read this text before reporting this as a bug on our GitHub!!!");
            System.err.println("###");
            System.err.println("### You're using Minecraft Server " + currentVersion);
            System.err.println("### But BSkyBlock v" + getDescription().getVersion() + " requires you to be using");
            System.err.println("### Minecraft Server " + versions.toString());
            System.err.println("###");
            System.err.println("### Please consider updating your Server software or using a newer");
            System.err.println("### version of BSkyBlock.");
            System.err.println("###");
            System.err.println("### If BSkyBlock is not available on the Server software you are using");
            System.err.println("### don't hesitate to contribute on our GitHub.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load configuration and locales. If there are no errors, load the plugin.
        if(PluginConfig.loadPluginConfig(this)){
            
            playersManager = new PlayersManager(this);
            islandsManager = new IslandsManager(this);
            // Only load metrics if set to true in config
            if(Settings.metrics) {
                metrics = new Metrics(plugin);
                registerCustomCharts();
            }

            offlineHistoryMessages = new OfflineHistoryMessages(this);
            offlineHistoryMessages.load();

            if (Settings.useEconomy && !VaultHelper.setupEconomy()) {
                getLogger().warning("Could not set up economy! - Running without an economy.");
                Settings.useEconomy = false;
            }

            VaultHelper.setupPermissions();

            // Set up commands
            islandCommand = new IslandCommand();
            adminCommand = new AdminCommand();

            // These items have to be loaded when the server has done 1 tick.
            // Note Worlds are not loaded this early, so any Locations or World reference will be null
            // at this point. Therefore, the 1 tick scheduler is required.
            getServer().getScheduler().runTask(this, new Runnable() {

                @Override
                public void run() {
                    // Create the world if it does not exist
                    new IslandWorld(plugin);
                    
                    getServer().getScheduler().runTask(plugin, new Runnable() {

                        @Override
                        public void run() {
                            // Load islands from database
                            islandsManager.load();

                            // TODO: load these from config.yml
                            Settings.chestItems = new ItemStack[] {
                                    new ItemStack(Material.LAVA_BUCKET,1),
                                    new ItemStack(Material.ICE,2),
                                    new ItemStack(Material.MELON_SEEDS,1),
                                    new ItemStack(Material.BONE,2),
                                    new ItemStack(Material.COBBLESTONE,5),
                                    new ItemStack(Material.SAPLING,2)
                            };

                            Settings.defaultLanguage = "en-US";
                            localeManager = new LocaleManager(plugin);

                            // Register Listeners
                            registerListeners();
                            /*
                             *DEBUG CODE
                            Island loadedIsland = islandsManager.getIsland(owner);
                            getLogger().info("Island name = " + loadedIsland.getName());
                            getLogger().info("Island locked = " + loadedIsland.getLocked());
                            //getLogger().info("Random set = " + randomSet);
                            getLogger().info("Island coops = " + loadedIsland.getCoops());
                            for (Entry<SettingsFlag, Boolean> flag: loadedIsland.getFlags().entrySet()) {
                                getLogger().info("Flag " + flag.getKey().name() + " = " + flag.getValue());
                            }
                             */
                            // Save islands & players data asynchronously every X minutes
                            Settings.databaseBackupPeriod = 10 * 60 * 20;
                            plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {

                                @Override
                                public void run() {
                                    playersManager.save(true);
                                    islandsManager.save(true);
                                    offlineHistoryMessages.save(true);
                                }
                            }, Settings.databaseBackupPeriod, Settings.databaseBackupPeriod);
                        }
                    });
                } 
            });
        }
    }

    public IslandCommand getIslandCommand() {
        return islandCommand;
    }

    private void registerListeners() {
        PluginManager manager = getServer().getPluginManager();
        // Player join events
        manager.registerEvents(new JoinLeaveListener(this), this);
        manager.registerEvents(new NetherEvents(this), this);
        manager.registerEvents(new NetherPortals(this), this);
        manager.registerEvents(new IslandGuard(this), this);
        manager.registerEvents(new IslandGuard1_8(this), this);
        manager.registerEvents(new IslandGuard1_9(this), this);
        manager.registerEvents(new PanelListener(this), this);
    }

    @Override
    public void onDisable(){
        if (!incompatible) {
            // Save data
            playersManager.shutdown();
            islandsManager.shutdown();
            //offlineHistoryMessages.shutdown();
        }
    }

    private void registerCustomCharts(){
        metrics.addCustomChart(new Metrics.SingleLineChart("islands_count") {

            @Override
            public int getValue() {
                return islandsManager.getCount();
            }
        });

        metrics.addCustomChart(new Metrics.SingleLineChart("created_islands") {

            @Override
            public int getValue() {
                int created = islandsManager.metrics_getCreatedCount();
                islandsManager.metrics_setCreatedCount(0);
                return created;
            }
        });

        metrics.addCustomChart(new Metrics.SimplePie("default_locale") {

            @Override
            public String getValue() {
                return Settings.defaultLanguage;
            }
        });

        metrics.addCustomChart(new Metrics.SimplePie("database") {

            @Override
            public String getValue() {
                return BSBDatabase.getDatabase().toString();
            }
        });
    }

    /**
     * Returns the player database
     * @return the player database
     */
    public PlayersManager getPlayers(){
        return playersManager;
    }

    /**
     * Returns the island database
     * @return the island database
     */
    public IslandsManager getIslands(){
        return islandsManager;
    }

    public static BSkyBlock getPlugin() {
        return plugin;
    }

    /**
     * @param sender
     * @return Locale object for sender
     */
    public BSBLocale getLocale(CommandSender sender) {
        return localeManager.getLocale(sender);
    }

    /**
     * @param uuid
     * @return Locale object for UUID
     */
    public BSBLocale getLocale(UUID uuid) {
        return localeManager.getLocale(uuid);
    }

    public NMSAbstraction getNMSHandler() {
        NMSAbstraction nmsHandler = null;
        try {
            nmsHandler = Util.getNMSHandler();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return nmsHandler;
    }
}
