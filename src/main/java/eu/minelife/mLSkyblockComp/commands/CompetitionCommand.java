package eu.minelife.mLSkyblockComp.commands;

import eu.minelife.mLSkyblockComp.MLSkyblockComp;
import eu.minelife.mLSkyblockComp.managers.CompetitionManager;
import eu.minelife.mLSkyblockComp.models.Competition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Command handler for the competition plugin.
 */
public class CompetitionCommand implements CommandExecutor, TabCompleter {
    private final MLSkyblockComp plugin;
    private final CompetitionManager competitionManager;

    public CompetitionCommand(MLSkyblockComp plugin) {
        this.plugin = plugin;
        this.competitionManager = plugin.getCompetitionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                showCompetitionInfo(sender);
                break;
            case "standings":
                showStandings(sender);
                break;
            case "reload":
                if (hasPermission(sender, "mlskyblockcomp.admin")) {
                    plugin.reloadConfig();
                    sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
                }
                break;
            case "start":
                if (hasPermission(sender, "mlskyblockcomp.admin")) {
                    startManualCompetition(sender, args);
                }
                break;
            case "help":
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Start a manual competition for testing purposes.
     * Usage: /competition start <category> [duration]
     * 
     * @param sender The command sender
     * @param args The command arguments
     */
    private void startManualCompetition(CommandSender sender, String[] args) {
        if (competitionManager.getCurrentCompetition() != null) {
            sender.sendMessage(ChatColor.RED + "A competition is already running. End it first.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /competition start <category> [duration]");
            sender.sendMessage(ChatColor.RED + "Available categories: " + 
                    String.join(", ", competitionManager.getCategories()));
            return;
        }

        String category = args[1];
        int duration = 5; // Default duration: 5 minutes

        if (args.length >= 3) {
            try {
                duration = Integer.parseInt(args[2]);
                if (duration < 1 || duration > 60) {
                    sender.sendMessage(ChatColor.RED + "Duration must be between 1 and 60 minutes.");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid duration. Please enter a number between 1 and 60.");
                return;
            }
        }

        boolean success = competitionManager.startManualCompetition(category, duration);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Manual competition started with category: " + 
                    formatCategoryName(category) + " for " + duration + " minutes.");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to start competition. Check console for errors.");
            sender.sendMessage(ChatColor.RED + "Available categories: " + 
                    String.join(", ", competitionManager.getCategories()));
        }
    }

    private void showCompetitionInfo(CommandSender sender) {
        Competition currentCompetition = competitionManager.getCurrentCompetition();

        if (currentCompetition == null) {
            sender.sendMessage(ChatColor.RED + "There is no active competition right now.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Current Competition" + ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Category: " + ChatColor.WHITE + 
                formatCategoryName(currentCompetition.getCategory()));
        sender.sendMessage(ChatColor.YELLOW + "Ends at: " + ChatColor.WHITE + 
                currentCompetition.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
    }

    private void showStandings(CommandSender sender) {
        Competition currentCompetition = competitionManager.getCurrentCompetition();

        if (currentCompetition == null) {
            sender.sendMessage(ChatColor.RED + "There is no active competition right now.");
            return;
        }

        Map<UUID, Integer> standings = competitionManager.getCurrentStandings();
        if (standings.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No players have participated in the current competition yet.");
            return;
        }

        // Sort by count (descending)
        List<Map.Entry<UUID, Integer>> sortedStandings = new ArrayList<>(standings.entrySet());
        sortedStandings.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Competition Standings" + ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Category: " + ChatColor.WHITE + 
                formatCategoryName(currentCompetition.getCategory()));

        int position = 1;
        for (Map.Entry<UUID, Integer> entry : sortedStandings) {
            UUID playerId = entry.getKey();
            int count = entry.getValue();

            String playerName = Bukkit.getOfflinePlayer(playerId).getName();
            if (playerName == null) playerName = "Unknown";

            sender.sendMessage(ChatColor.GOLD + "#" + position + " " + 
                    ChatColor.WHITE + playerName + ": " + 
                    ChatColor.YELLOW + count + " " + 
                    formatCategoryName(currentCompetition.getCategory()));

            position++;

            // Only show top 10
            if (position > 10) break;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "MLSkyblockComp Commands" + ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.GOLD + "/competition info " + ChatColor.YELLOW + "- Show information about the current competition");
        sender.sendMessage(ChatColor.GOLD + "/competition standings " + ChatColor.YELLOW + "- Show current competition standings");

        if (hasPermission(sender, "mlskyblockcomp.admin")) {
            sender.sendMessage(ChatColor.GOLD + "/competition reload " + ChatColor.YELLOW + "- Reload the plugin configuration");
            sender.sendMessage(ChatColor.GOLD + "/competition start <category> [duration] " + ChatColor.YELLOW + "- Manually start a competition");
        }
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission) || sender.isOp();
    }

    private String formatCategoryName(String category) {
        return category.replace("_", " ").toLowerCase();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("info", "standings", "help"));

            if (hasPermission(sender, "mlskyblockcomp.admin")) {
                completions.add("reload");
                completions.add("start");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start") && hasPermission(sender, "mlskyblockcomp.admin")) {
            // Tab completion for categories when using /competition start <category>
            return competitionManager.getCategories().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("start") && hasPermission(sender, "mlskyblockcomp.admin")) {
            // Tab completion for duration when using /competition start <category> <duration>
            return Arrays.asList("5", "10", "15", "30").stream()
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
