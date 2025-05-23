package eu.minelife.mLSkyblockComp;

import eu.minelife.mLSkyblockComp.commands.CompetitionCommand;
import eu.minelife.mLSkyblockComp.listeners.FarmingListener;
import eu.minelife.mLSkyblockComp.managers.CompetitionManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class MLSkyblockComp extends JavaPlugin {
    private static MLSkyblockComp instance;
    private CompetitionManager competitionManager;
    private Logger logger;
    private CompetitionCommand competitionCommand;

    @Override
    public void onEnable() {
        logger = getLogger();

        // Set instance
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        competitionManager = new CompetitionManager(this);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new FarmingListener(this), this);

        // Register commands
        competitionCommand = new CompetitionCommand(this);
        getCommand("competition").setExecutor(competitionCommand);
        getCommand("competition").setTabCompleter(competitionCommand);

        // Start competition scheduler
        competitionManager.startScheduler();

        logger.info("MLSkyblockComp has been enabled!");
    }

    @Override
    public void onDisable() {
        // Stop competition scheduler
        if (competitionManager != null) {
            competitionManager.stopScheduler();
        }

        // Cancel all tasks owned by this plugin to ensure clean shutdown
        Bukkit.getScheduler().cancelTasks(this);

        logger.info("MLSkyblockComp has been disabled!");
    }

    @Override
    public void reloadConfig() {
        // Stop current competition scheduler
        if (competitionManager != null) {
            competitionManager.stopScheduler();
        }

        // Reload config file
        super.reloadConfig();

        // Reinitialize competition manager
        competitionManager = new CompetitionManager(this);

        // Restart scheduler
        competitionManager.startScheduler();

        logger.info("MLSkyblockComp configuration reloaded!");
    }

    public static MLSkyblockComp getInstance() {
        return instance;
    }

    public CompetitionManager getCompetitionManager() {
        return competitionManager;
    }
}
