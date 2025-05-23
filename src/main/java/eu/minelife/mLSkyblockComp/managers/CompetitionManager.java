package eu.minelife.mLSkyblockComp.managers;

import eu.minelife.mLSkyblockComp.MLSkyblockComp;
import eu.minelife.mLSkyblockComp.models.Competition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.AbstractMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CompetitionManager {
    private final MLSkyblockComp plugin;
    private final Logger logger;
    private final List<String> competitionPeriods;
    private final List<String> categories;
    private final Map<UUID, Map<String, Integer>> playerFarmCounts;
    private final Map<String, Map<String, Map<String, String>>> rewards;
    private boolean requireMinimumPlayers; // Whether to require minimum players to start competition
    private ZoneId timezone; // Timezone for competition scheduling

    private Competition currentCompetition;
    private BukkitTask schedulerTask;
    private final Random random;
    private String announcedCategory; // Track which category has been pre-announced

    // Static counter to track the number of active scheduler tasks
    private static int activeSchedulerTasks = 0;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH.mm");

    public CompetitionManager(MLSkyblockComp plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.random = new Random();
        this.playerFarmCounts = new ConcurrentHashMap<>();
        this.rewards = new HashMap<>();

        // Load general settings from config
        this.requireMinimumPlayers = plugin.getConfig().getBoolean("requireMinimumPlayers", true);

        // Load timezone from config
        String timezoneStr = plugin.getConfig().getString("timezone", "UTC");
        try {
            this.timezone = ZoneId.of(timezoneStr);
        } catch (Exception e) {
            logger.warning("Invalid timezone: " + timezoneStr + ". Falling back to UTC.");
            this.timezone = ZoneId.of("UTC");
        }

        // Load competition periods from config
        this.competitionPeriods = plugin.getConfig().getStringList("competitionPeriods");

        // Load categories from config
        this.categories = new ArrayList<>();
        for (String key : plugin.getConfig().getKeys(false)) {
            if (!key.equals("competitionPeriods") && !key.equals("requireMinimumPlayers") && 
                !key.equals("timezone") && plugin.getConfig().isConfigurationSection(key)) {
                categories.add(key);

                // Load rewards for this category
                ConfigurationSection categorySection = plugin.getConfig().getConfigurationSection(key);
                Map<String, Map<String, String>> categoryRewards = new HashMap<>();

                if (categorySection != null) {
                    for (String rewardKey : categorySection.getKeys(false)) {
                        ConfigurationSection rewardSection = categorySection.getConfigurationSection(rewardKey);
                        if (rewardSection != null) {
                            Map<String, String> rewardDetails = new HashMap<>();
                            rewardDetails.put("command", rewardSection.getString("command"));
                            rewardDetails.put("message", rewardSection.getString("message"));
                            categoryRewards.put(rewardKey, rewardDetails);
                        }
                    }
                }

                rewards.put(key, categoryRewards);
            }
        }
    }

    public void startScheduler() {
        // Cancel any existing scheduler task
        if (schedulerTask != null) {
            stopScheduler();
        }

        // Cancel all tasks owned by this plugin to ensure no duplicate tasks
        Bukkit.getScheduler().cancelTasks(plugin);

        // Reset the active tasks counter to ensure accuracy
        activeSchedulerTasks = 0;

        // Run the scheduler every second instead of every 10 seconds
        // This ensures we don't miss the exact time when a competition should start
        schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkCompetitionSchedule, 20L, 20L); // Check every second
        activeSchedulerTasks++;
    }

    public void stopScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
            activeSchedulerTasks--;
        }

        if (currentCompetition != null) {
            endCompetition();
        }
    }

    private void checkCompetitionSchedule() {
        // Get current time in the configured timezone
        LocalTime now = LocalTime.now(timezone);

        // Check if we need to end a competition
        if (currentCompetition != null && now.isAfter(currentCompetition.getEndTime())) {
            endCompetition();
        }

        // Flag to track if we've already started or announced a competition in this run
        boolean competitionHandled = false;

        // Check if we need to start a new competition or announce an upcoming one
        for (String periodStr : competitionPeriods) {
            // Skip if we've already handled a competition in this run
            if (competitionHandled) {
                break;
            }

            String[] times = periodStr.split(" - ");
            if (times.length != 2) {
                logger.warning("Invalid competition period format: " + periodStr);
                continue;
            }

            try {
                LocalTime startTime = LocalTime.parse(times[0], TIME_FORMATTER);
                LocalTime endTime = LocalTime.parse(times[1], TIME_FORMATTER);

                // Calculate one minute before start time
                LocalTime oneMinuteBefore = startTime.minusMinutes(1);

                // If current time is between start and end time, and no competition is running
                if (currentCompetition == null) {
                    // More flexible time comparison - check if the current time is within the same minute as the start time
                    boolean isStartTime = now.getHour() == startTime.getHour() && now.getMinute() == startTime.getMinute();
                    boolean isWithinPeriod = (now.isAfter(startTime) || isStartTime) && now.isBefore(endTime);

                    if (isStartTime || isWithinPeriod) {
                        // Check if there are enough players online (if required)
                        if (!requireMinimumPlayers || Bukkit.getOnlinePlayers().size() >= 5) {
                            startCompetition(startTime, endTime);
                            competitionHandled = true;
                        }
                        break;
                    } 

                    // More flexible time comparison for announcement - check if the current time is within the same minute as one minute before
                    boolean isOneMinuteBefore = now.getHour() == oneMinuteBefore.getHour() && now.getMinute() == oneMinuteBefore.getMinute();

                    if (isOneMinuteBefore && announcedCategory == null) {
                        // Announce competition starting in 1 minute if there are enough players (if required)
                        if (!requireMinimumPlayers || Bukkit.getOnlinePlayers().size() >= 5) {
                            announceUpcomingCompetition(startTime);
                            competitionHandled = true;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error parsing competition period: " + periodStr + " - " + e.getMessage());
            }
        }
    }

    private void announceUpcomingCompetition(LocalTime startTime) {
        // Select a random category if not already announced
        if (announcedCategory == null) {
            announcedCategory = categories.get(random.nextInt(categories.size()));
        }

        // Announce upcoming competition
        Bukkit.broadcastMessage("§c------------------------------------------------");
        Bukkit.broadcastMessage("§6§l[Competition] §eA farming competition will begin in §f1 minute§e!");
        Bukkit.broadcastMessage("§6§l[Competition] §eWhat to farm: §f" + formatCategoryName(announcedCategory));
        Bukkit.broadcastMessage("§6§l[Competition] §eGet ready to farm!");
        Bukkit.broadcastMessage("§c------------------------------------------------");
    }

    private void startCompetition(LocalTime startTime, LocalTime endTime) {
        // Use the announced category if available, otherwise select a random one
        String category;
        if (announcedCategory != null) {
            category = announcedCategory;
            announcedCategory = null; // Reset for next time
        } else {
            category = categories.get(random.nextInt(categories.size()));
        }

        startCompetition(category, startTime, endTime);
    }

    /**
     * Manually start a competition with a specific category.
     * This is used for testing purposes.
     *
     * @param category The farming category for this competition
     * @param durationMinutes The duration of the competition in minutes
     * @return true if the competition was started, false if a competition is already running
     */
    public boolean startManualCompetition(String category, int durationMinutes) {
        if (currentCompetition != null) {
            return false; // Competition already running
        }

        if (!categories.contains(category)) {
            logger.warning("Invalid category for manual competition: " + category);
            return false;
        }

        LocalTime now = LocalTime.now(timezone);
        LocalTime endTime = now.plusMinutes(durationMinutes);

        startCompetition(category, now, endTime);
        return true;
    }

    /**
     * Start a competition with the specified category, start time, and end time.
     *
     * @param category The farming category for this competition
     * @param startTime The start time of the competition
     * @param endTime The end time of the competition
     */
    private void startCompetition(String category, LocalTime startTime, LocalTime endTime) {
        // Create new competition
        currentCompetition = new Competition(category, startTime, endTime);

        // Clear previous farm counts
        playerFarmCounts.clear();

        // Announce competition start
        Bukkit.broadcastMessage("§c------------------------------------------------");
        Bukkit.broadcastMessage("§6§l[Competition] §eA new farming competition has started!");
        Bukkit.broadcastMessage("§6§l[Competition] §cThe player with most farmed items wins!");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l[Competition] §eWhat to farm: §f" + formatCategoryName(category));
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l[Competition] §eEnds at: §f" + endTime.format(TIME_FORMATTER));
        Bukkit.broadcastMessage("§c------------------------------------------------");
    }

    private void endCompetition() {
        if (currentCompetition == null) return;

        String category = currentCompetition.getCategory();

        // Create a sorted list of participants by count
        List<Map.Entry<UUID, Integer>> sortedParticipants = new ArrayList<>();

        for (Map.Entry<UUID, Map<String, Integer>> entry : playerFarmCounts.entrySet()) {
            UUID playerId = entry.getKey();
            Map<String, Integer> counts = entry.getValue();
            int count = counts.getOrDefault(category, 0);

            if (count > 0) {
                sortedParticipants.add(new AbstractMap.SimpleEntry<>(playerId, count));
            }
        }

        // Sort participants by count (descending)
        sortedParticipants.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        // Announce the results
        Bukkit.broadcastMessage("§6§l[Competition] §eThe competition has ended!");
        Bukkit.broadcastMessage("");

        if (!sortedParticipants.isEmpty()) {
            // Announce top 3 farmers
            Bukkit.broadcastMessage("§6§l[Competition] §cTop Farmers:");

            int position = 1;
            for (Map.Entry<UUID, Integer> entry : sortedParticipants) {
                if (position > 3) break; // Only show top 3

                UUID playerId = entry.getKey();
                int count = entry.getValue();
                String playerName = Bukkit.getOfflinePlayer(playerId).getName();

                if (playerName != null) {
                    Bukkit.broadcastMessage("§6§l#" + position + " §f" + playerName + " §e- §f" + count + " §e" + formatCategoryName(category));
                }

                position++;
            }

            // Give reward to the winner
            if (!sortedParticipants.isEmpty()) {
                UUID winnerId = sortedParticipants.get(0).getKey();
                Player winner = Bukkit.getPlayer(winnerId);
                if (winner != null) {
                    giveRandomReward(winner, category);
                }
            }
        } else {
            Bukkit.broadcastMessage("§6§l[Competition] §eNo participants in this competition!");
        }

        // Reset current competition
        currentCompetition = null;
    }

    private void giveRandomReward(Player player, String category) {
        Map<String, Map<String, String>> categoryRewards = rewards.get(category);
        if (categoryRewards == null || categoryRewards.isEmpty()) {
            logger.warning("No rewards found for category: " + category);
            return;
        }

        // Get a random reward
        List<String> rewardKeys = new ArrayList<>(categoryRewards.keySet());
        String randomRewardKey = rewardKeys.get(random.nextInt(rewardKeys.size()));
        Map<String, String> rewardDetails = categoryRewards.get(randomRewardKey);

        if (rewardDetails != null) {
            String command = rewardDetails.get("command");
            String message = rewardDetails.get("message");

            // Replace placeholders
            command = command.replace("%player%", player.getName())
                             .replace("%NAME%", player.getName());

            // Execute command
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            // Send message to player
            if (message != null && !message.isEmpty()) {
                player.sendMessage(message.replace("&", "§"));
            }
        }
    }

    public void recordFarming(Player player, String material, int amount) {
        if (currentCompetition == null) {
            return;
        }

        String category = currentCompetition.getCategory();

        // Normalize both strings for comparison (convert to lowercase, remove underscores and spaces)
        String normalizedMaterial = material.toLowerCase().replace("_", "").replace(" ", "");
        String normalizedCategory = category.toLowerCase().replace("_", "").replace(" ", "");

        // Check if the normalized material matches the normalized category
        if (!normalizedMaterial.equals(normalizedCategory)) {
            // Send a message to the player explaining why their farming activity wasn't counted
            player.sendMessage("§6§l[Competition] §eThe current competition is for §f" + formatCategoryName(category) + 
                              "§e, not §f" + formatCategoryName(material) + "§e!");
            return;
        }

        UUID playerId = player.getUniqueId();
        playerFarmCounts.computeIfAbsent(playerId, k -> new HashMap<>());

        Map<String, Integer> counts = playerFarmCounts.get(playerId);
        counts.put(category, counts.getOrDefault(category, 0) + amount);

        // Send a message to the player about their progress
        //player.sendMessage("§6§l[Competition] §eYou have farmed §f" + counts.get(category) + " §e" + formatCategoryName(category) + "!");
    }

    public Competition getCurrentCompetition() {
        return currentCompetition;
    }

    public Map<UUID, Integer> getCurrentStandings() {
        if (currentCompetition == null) return Collections.emptyMap();

        String category = currentCompetition.getCategory();
        Map<UUID, Integer> standings = new HashMap<>();

        for (Map.Entry<UUID, Map<String, Integer>> entry : playerFarmCounts.entrySet()) {
            UUID playerId = entry.getKey();
            Map<String, Integer> counts = entry.getValue();
            int count = counts.getOrDefault(category, 0);
            standings.put(playerId, count);
        }

        return standings;
    }

    private String formatCategoryName(String category) {
        return category.replace("_", " ").toLowerCase();
    }

    /**
     * Get the list of available farming categories.
     * 
     * @return A list of category names
     */
    public List<String> getCategories() {
        return new ArrayList<>(categories);
    }
}
