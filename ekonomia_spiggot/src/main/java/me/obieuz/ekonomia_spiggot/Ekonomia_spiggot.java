package me.obieuz.ekonomia_spiggot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Ekonomia_spiggot extends JavaPlugin implements Listener {

    private final HashMap<UUID, Integer> playerValues = new HashMap<>();
    private final HashMap<Material, Integer> itemValues = new HashMap<>();
    private final HashMap<Location, HashMap<String, Object>> blockMetadata = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        String[] lines = event.getLines();

        if(event.getBlock().hasMetadata("shop")) {
            return;
        }

        if (!lines[0].equals("[shop]")) {
            return;
        }

        if(lines.length < 4){
            player.sendMessage("Every shop should contains 4 lines. Check the docs.");
            return;
        }

        Block block = event.getBlock();

        Block signBlock = event.getBlock();
        Block attachedBlock = signBlock.getRelative(((org.bukkit.block.data.type.WallSign) signBlock.getBlockData()).getFacing().getOppositeFace());

        if(attachedBlock.getType() != Material.CHEST)
        {
            player.sendMessage("Shop must be placed on a chest.");
            return;
        }

        Chest chest = (Chest) attachedBlock.getState();

        block.setMetadata("shop", new FixedMetadataValue(this, true));

        String shopType =lines[1].split(" ")[0];

        String amount = lines[1].split(" ")[1];

        if(shopType.equals("buy")){
            block.setMetadata("shop_type", new FixedMetadataValue(this, "buy"));
        }
        else if(shopType.equals("sell")){
            block.setMetadata("shop_type", new FixedMetadataValue(this, "sell"));
        }
        else{
            player.sendMessage("Invalid shop type. Second line should have 'buy' or 'sell'.");
            return;
        }
        block.setMetadata("amount", new FixedMetadataValue(this, amount));

        String price = lines[2];
        block.setMetadata("price", new FixedMetadataValue(this, price));

        String item = lines[3];
        block.setMetadata("item", new FixedMetadataValue(this, item));

        chest.setMetadata("creator", new FixedMetadataValue(this, player.getUniqueId()));
        block.setMetadata("creator", new FixedMetadataValue(this, player.getUniqueId()));

        blockMetadata.put(chest.getLocation(), new HashMap<>() {{
            put("creator", player.getUniqueId().toString());
        }});

        blockMetadata.put(block.getLocation(), new HashMap<>() {{
            put("shop", true);
            put("shop_type", shopType);
            put("amount", amount);
            put("price", price);
            put("item", item);
            put("creator", player.getUniqueId().toString());
        }});

        player.sendMessage("Shop created! You are "+ shopType + "ing " + amount + " " + item + " for " + price);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if(block == null){
            return;
        }

        if(block.getType() == Material.CHEST){
            Chest chest = (Chest) block.getState();

            if(chest.hasMetadata("creator")){
                UUID creatorId = UUID.fromString(chest.getMetadata("creator").get(0).value().toString());

                if(!creatorId.equals(player.getUniqueId())){
                    player.sendMessage("This is not your shop.");
                    event.setCancelled(true);
                }
            }
            return;
        }

        if(block.getType() == Material.OAK_WALL_SIGN || block.getType() ==  Material.SPRUCE_WALL_SIGN || block.getType() ==  Material.BIRCH_WALL_SIGN || block.getType() ==  Material.JUNGLE_WALL_SIGN || block.getType() ==  Material.ACACIA_WALL_SIGN || block.getType() ==  Material.DARK_OAK_WALL_SIGN){
            if(event.getAction() != Action.RIGHT_CLICK_BLOCK){
                return;
            }
            if(!block.hasMetadata("shop")){
                return;
            }

            Block attachedBlock = block.getRelative(((org.bukkit.block.data.type.WallSign) block.getBlockData()).getFacing().getOppositeFace());
            Chest chest = (Chest) attachedBlock.getState();

            Integer amount = Integer.parseInt( block.getMetadata("amount").get(0).value().toString() );
            Integer price = Integer.parseInt( block.getMetadata("price").get(0).value().toString() );
            String item = block.getMetadata("item").get(0).value().toString();
            String shopType =  block.getMetadata("shop_type").get(0).value().toString();
            UUID creatorId = UUID.fromString(block.getMetadata("creator").get(0).value().toString());

            if(creatorId.equals(player.getUniqueId())){
                player.sendMessage("You cannot buy from your own shop.");
                event.setCancelled(true);
                return;
            }

            if(shopType.contains("buy"))
            {
                if(playerValues.get(player.getUniqueId()) < price){
                    player.sendMessage("You do not have enough money to buy this item.");
                    event.setCancelled(true);
                    return;
                }

                Inventory chestInventory = chest.getInventory();

                if(chestInventory.containsAtLeast(new ItemStack(Material.getMaterial(item), amount), amount)){
                    chestInventory.removeItem(new ItemStack(Material.getMaterial(item), amount));

                    playerValues.put(player.getUniqueId(), playerValues.get(player.getUniqueId()) - price);

                    playerValues.put(creatorId, playerValues.get(creatorId) + price);

                    player.getInventory().addItem(new ItemStack(Material.getMaterial(item), amount));

                    player.sendMessage("Bought " + amount + " " + item + " for " + price + ". New balance: " + playerValues.get(player.getUniqueId()));
                }
                else{
                    player.sendMessage("Shop does not have enough items to sell.");
                }
            }
            else if(shopType.contains("sell"))
            {
                Inventory playerInventory = player.getInventory();

                if(playerInventory.containsAtLeast(new ItemStack(Material.getMaterial(item), amount), amount)){
                    playerInventory.removeItem(new ItemStack(Material.getMaterial(item), amount));
                    playerValues.put(player.getUniqueId(), playerValues.get(player.getUniqueId()) + price);

                    playerValues.put(creatorId, playerValues.get(creatorId) - price);

                    chest.getInventory().addItem(new ItemStack(Material.getMaterial(item), amount));

                    player.sendMessage("Sold " + amount + " " + item + " for " + price + ". New balance: " + playerValues.get(player.getUniqueId()));
                }
                else{
                    player.sendMessage("You do not have enough items to sell.");
                    event.setCancelled(true);
                }
            }
            event.setCancelled(true);
        }
    }

    @Override
    public void onDisable() {
        saveBlockMetadata();
        saveData();
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        InitializeItemValues();
        loadData();
        loadBlockMetadata();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerValues.putIfAbsent(event.getPlayer().getUniqueId(), 0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("balance")) {
            player.sendMessage("Your balance is: " + playerValues.get(player.getUniqueId()));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("deposit")) {
            if (args.length != 1) {
                player.sendMessage("Usage: /deposit <amount>");
                return true;
            }

            try {
                int amount = Integer.parseInt(args[0]);
                playerValues.put(playerId, playerValues.get(playerId) + amount);
                player.sendMessage("Deposited " + amount + ". New balance: " + playerValues.get(playerId));
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid amount. Please enter a number.");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("pay")) {
            if (args.length != 2) {
                player.sendMessage("Usage: /pay <player> <amount>");
                return true;
            }

            try {
                int amount = Integer.parseInt(args[1]);

                int playerBalance = playerValues.get(playerId);

                if(playerBalance < amount || amount < 0){
                    player.sendMessage("You do not have enough money to pay that amount.");
                    return true;
                }

                Player target_player = Bukkit.getPlayer(args[0]);

                if(target_player == null){
                    player.sendMessage("Player not found.");
                    return true;
                }

                playerValues.put(playerId, playerValues.get(playerId) - amount);
                playerValues.put(target_player.getUniqueId(), playerValues.get(target_player.getUniqueId()) + amount);

                player.sendMessage("Paid " + amount + " to " + target_player.getName() + ". New balance: " + playerValues.get(playerId));
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid amount. Please enter a number.");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("sell")) {

            if(args.length != 1){
                player.sendMessage("Usage: /sell <amount>");
                return true;
            }

            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            int amount = itemInHand.getAmount();

            int sellAmount = Integer.parseInt(args[0]);

            if(amount < sellAmount){
                player.sendMessage("You do not have enough items to sell that amount.");
                return true;
            }

            int value = itemValues.get(itemInHand.getType());

            player.getInventory().removeItem(new ItemStack(itemInHand.getType(), sellAmount));

            playerValues.put(playerId, playerValues.get(playerId) + (value * sellAmount));

            player.sendMessage("Sold " + sellAmount + " " + itemInHand.getType() + " for " + (value * sellAmount) + ". New balance: " + playerValues.get(playerId));


            return true;
        }

        return false;
    }

    private void loadData(){
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : dataConfig.getKeys(false)) {
            UUID playerId = UUID.fromString(key);
            int balance = dataConfig.getInt(key);
            playerValues.put(playerId, balance);
        }
    }

    private void loadBlockMetadata() {
        File metadataFile = new File(getDataFolder(), "block_metadata.yml");
        if (!metadataFile.exists()) {
            return;
        }

        FileConfiguration metadataConfig = YamlConfiguration.loadConfiguration(metadataFile);

        for (String key : metadataConfig.getKeys(false)) {
            String[] parts = key.split(",");
            World world = Bukkit.getWorld(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            Location location = new Location(world, x, y, z);
            HashMap<String, Object> metadata = new HashMap<>();

            Block block = world.getBlockAt(location);

            for (String metaKey : metadataConfig.getConfigurationSection(key).getKeys(false)) {
                metadata.put(metaKey, metadataConfig.get(key + "." + metaKey));

                block.setMetadata(metaKey, new FixedMetadataValue(this, metadataConfig.get(key + "." + metaKey)));
            }

            blockMetadata.put(location, metadata);

        }
    }

    private void saveData() {
        if (dataConfig == null || dataFile == null) {
            return;
        }

        for (UUID playerId : playerValues.keySet()) {
            dataConfig.set(playerId.toString(), playerValues.get(playerId));
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveBlockMetadata() {
        File metadataFile = new File(getDataFolder(), "block_metadata.yml");

        try (FileWriter fileWriter = new FileWriter(metadataFile, false)) {

        } catch (IOException e) {
            e.printStackTrace();
        }

        FileConfiguration metadataConfig = YamlConfiguration.loadConfiguration(metadataFile);

        for (Map.Entry<Location, HashMap<String, Object>> entry : blockMetadata.entrySet()) {
            Location location = entry.getKey();
            HashMap<String, Object> metadata = entry.getValue();

            String locString = location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
            for (Map.Entry<String, Object> metaEntry : metadata.entrySet()) {
                metadataConfig.set(locString + "." + metaEntry.getKey(), metaEntry.getValue());
            }
        }

        try {
            metadataConfig.save(metadataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void InitializeItemValues()
    {
        itemValues.put(Material.DIAMOND, 100);
        itemValues.put(Material.GOLD_INGOT, 50);
        itemValues.put(Material.IRON_INGOT, 25);
        itemValues.put(Material.COAL, 10);
        itemValues.put(Material.STICK, 1);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block.hasMetadata("shop")) {
            block.removeMetadata("shop", this);
            block.removeMetadata("shop_type", this);
            block.removeMetadata("amount", this);
            block.removeMetadata("price", this);
            block.removeMetadata("item", this);
            block.removeMetadata("creator", this);

            blockMetadata.remove(block.getLocation());
        }
        if(block.hasMetadata("creator")){
            block.removeMetadata("creator", this);

            blockMetadata.remove(block.getLocation());
        }
    }
}
