package eu.minelife.mLSkyblockComp.listeners;

import eu.minelife.mLSkyblockComp.MLSkyblockComp;
import eu.minelife.mLSkyblockComp.managers.CompetitionManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener for farming-related events to track player farming activities during competitions.
 */
public class FarmingListener implements Listener {
    private final MLSkyblockComp plugin;
    private final CompetitionManager competitionManager;

    // Map of materials to their corresponding category names in the config
    private final Map<Material, String> materialToCategory;

    public FarmingListener(MLSkyblockComp plugin) {
        this.plugin = plugin;
        this.competitionManager = plugin.getCompetitionManager();
        this.materialToCategory = initializeMaterialMap();
    }

    /**
     * Initialize the mapping between Materials and category names in the config.
     */
    private Map<Material, String> initializeMaterialMap() {
        Map<Material, String> map = new HashMap<>();

        // Add mappings for standard crops
        map.put(Material.WHEAT, "Wheat");
        map.put(Material.NETHER_WART, "Nether_Wart");
        map.put(Material.POTATOES, "Potato");
        map.put(Material.CARROTS, "Carrot");

        // Add more crops as needed

        return map;
    }

    /**
     * Handle the PlayerHarvestBlockEvent which is triggered when a player right-clicks
     * a crop to harvest it without breaking the block (e.g., for wheat, beetroot).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        Block block = event.getHarvestedBlock();
        Material material = block.getType();

        String category = materialToCategory.get(material);
        if (category != null) {
            // Count the number of items dropped
            int count = 0;
            for (ItemStack item : event.getItemsHarvested()) {
                if (item.getType() == material || 
                    (material == Material.WHEAT && item.getType() == Material.WHEAT) ||
                    (material == Material.POTATOES && item.getType() == Material.POTATO) ||
                    (material == Material.CARROTS && item.getType() == Material.CARROT) ||
                    (material == Material.NETHER_WART && item.getType() == Material.NETHER_WART)) {
                    count += item.getAmount();
                }
            }

            if (count > 0) {
                competitionManager.recordFarming(player, category, count);
            }
        }
    }

    /**
     * Handle the BlockBreakEvent which is triggered when a player breaks a block.
     * This is used to detect when players break fully grown crops.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();

        // Check if there's an active competition
        if (competitionManager.getCurrentCompetition() == null) {
            return;
        }

        String currentCategory = competitionManager.getCurrentCompetition().getCategory();
        String category = materialToCategory.get(material);

        if (category != null) {
            // Check if the crop is fully grown
            if (block.getBlockData() instanceof Ageable) {
                Ageable ageable = (Ageable) block.getBlockData();

                if (ageable.getAge() == ageable.getMaximumAge()) {
                    // The crop is fully grown, count it as 1
                    competitionManager.recordFarming(player, category, 1);
                }
            } else if (material == Material.NETHER_WART) {
                // Special case for nether wart which might not use Ageable
                org.bukkit.block.data.BlockData blockData = block.getBlockData();
                if (blockData instanceof org.bukkit.block.data.Ageable) {
                    org.bukkit.block.data.Ageable ageable = (org.bukkit.block.data.Ageable) blockData;

                    if (ageable.getAge() == ageable.getMaximumAge()) {
                        competitionManager.recordFarming(player, category, 1);
                    }
                }
            }
        }
    }

    /**
     * Handle the BlockDropItemEvent which is triggered when a block drops items.
     * This is used as a backup to catch any crops that might not be caught by the other events.
     * 
     * Note: This event is fired after BlockBreakEvent, and by this time the block might have been
     * replaced with AIR. This is normal Minecraft behavior - when a block is broken, it's replaced with AIR.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockMaterial = block.getType();

        // Check if there's an active competition
        if (competitionManager.getCurrentCompetition() == null) {
            return;
        }

        String currentCategory = competitionManager.getCurrentCompetition().getCategory();

        // Only process if this is a crop block
        String category = materialToCategory.get(blockMaterial);
        if (category != null) {
            int count = 0;

            // Count the dropped items that match the crop type
            for (org.bukkit.entity.Item item : event.getItems()) {
                ItemStack itemStack = item.getItemStack();
                Material itemMaterial = itemStack.getType();

                // Match the dropped item to the crop
                if ((blockMaterial == Material.WHEAT && itemMaterial == Material.WHEAT) ||
                    (blockMaterial == Material.POTATOES && itemMaterial == Material.POTATO) ||
                    (blockMaterial == Material.CARROTS && itemMaterial == Material.CARROT) ||
                    (blockMaterial == Material.NETHER_WART && itemMaterial == Material.NETHER_WART)) {
                    count += itemStack.getAmount();
                }
            }

            if (count > 0) {
                competitionManager.recordFarming(player, category, count);
            }
        }
    }
}
