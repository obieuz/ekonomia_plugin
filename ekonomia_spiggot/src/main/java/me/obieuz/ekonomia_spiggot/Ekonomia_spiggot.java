package me.obieuz.ekonomia_spiggot;

import org.bukkit.*;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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

    private final HashMap<UUID, Double> playerValues = new HashMap<>();
    private final HashMap<Material, Double> itemValues = new HashMap<>();
    private final HashMap<Location, HashMap<String, Object>> blockMetadata = new HashMap<>();

    private final Double deathPenalty = 0.1;
    private final Integer storeConstant = 1000;

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

        if(!amount.matches("\\d+"))
        {
            player.sendMessage("Amount should be a number");
        }

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
        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();

        if(itemInMainHand.getType() == Material.PAPER)
        {
            NamespacedKey key = new NamespacedKey(this,"amount");
            ItemMeta meta = itemInMainHand.getItemMeta();

            if(!meta.getPersistentDataContainer().has(key,PersistentDataType.INTEGER))
            {
                player.sendMessage("Failed to withdraw, take a screenshot and send to admin");
                return;
            }

            int amount = meta.getPersistentDataContainer().get(key,PersistentDataType.INTEGER);

            player.getInventory().removeItem(itemInMainHand);

            playerValues.put(player.getUniqueId(), playerValues.get(player.getUniqueId()) + amount);

            player.sendMessage("You withdraw "+amount+"$, now your balance is "+playerValues.get(player.getUniqueId()));

        }

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
            Double price = Double.parseDouble( block.getMetadata("price").get(0).value().toString() );

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

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::updateBalanceTab, 0, 20);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        double newBalance = playerValues.get(player.getUniqueId()) * (1 - deathPenalty);

        playerValues.put(player.getUniqueId(), newBalance);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerValues.putIfAbsent(event.getPlayer().getUniqueId(), 0.0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();
        String command_type = cmd.getName();

        balance_command(command_type, player, playerId, args);

        pay_command(command_type, player, playerId, args);

        sell_command(command_type, player, playerId, args);

        rynek_command(command_type, player, playerId, args);

        withdraw_command(command_type, player, playerId, args);

        return true;
    }

    private void loadData(){
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : dataConfig.getKeys(false)) {
            UUID playerId = UUID.fromString(key);
            Double balance = dataConfig.getDouble(key);
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
        itemValues.put(Material.DIAMOND, 100.0);
        itemValues.put(Material.GOLD_INGOT, 50.0);
        itemValues.put(Material.IRON_INGOT, 25.0);
        itemValues.put(Material.COAL, 10.0);
        itemValues.put(Material.STICK, 1.0);
    }
    private void updateBalanceTab()
    {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Integer balance = (int) Math.floor(playerValues.get(player.getUniqueId()));
            player.setPlayerListName(player.getName() + " " + balance + "$");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        Player player = event.getPlayer();

        if(block.hasMetadata("creator")){
            UUID creatorId = UUID.fromString(block.getMetadata("creator").get(0).value().toString());

            if(player.getGameMode() != GameMode.CREATIVE)
            {
                if(!creatorId.equals(player.getUniqueId())){
                    player.sendMessage("You cannot break other player's shop.");
                    event.setCancelled(true);
                    return;
                }
            }

            removeShop(block);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();

        if (block.getType() == Material.CHEST) {
            for (BlockFace face : BlockFace.values()) {

                Block adjacentBlock = block.getRelative(face);

                if (adjacentBlock.getType() == Material.CHEST && adjacentBlock.hasMetadata("creator")) {
                    event.getPlayer().sendMessage("You cannot place chests next to shop's chest.");
                    event.setCancelled(true);
                    break;
                }
            }
        }
        
        if (block.getType() == Material.HOPPER) {
            for (BlockFace face : BlockFace.values()) {

                Block adjacentBlock = block.getRelative(face);

                if (adjacentBlock.getType() == Material.CHEST && adjacentBlock.hasMetadata("creator")) {
                    event.getPlayer().sendMessage("You cannot place hoppers next to shop's chest.");
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    private void removeShop(Block block){
        block.removeMetadata("shop", this);
        block.removeMetadata("shop_type", this);
        block.removeMetadata("amount", this);
        block.removeMetadata("price", this);
        block.removeMetadata("item", this);
        block.removeMetadata("creator", this);

        if(block.getType() == Material.OAK_WALL_SIGN || block.getType() ==  Material.SPRUCE_WALL_SIGN || block.getType() ==  Material.BIRCH_WALL_SIGN || block.getType() ==  Material.JUNGLE_WALL_SIGN || block.getType() ==  Material.ACACIA_WALL_SIGN || block.getType() ==  Material.DARK_OAK_WALL_SIGN){
            Block attachedBlock = block.getRelative(((org.bukkit.block.data.type.WallSign) block.getBlockData()).getFacing().getOppositeFace());
            attachedBlock.removeMetadata("creator",this);

            blockMetadata.remove(attachedBlock.getLocation());
        }

        blockMetadata.remove(block.getLocation());
    }

    private void balance_command(String command, Player player, UUID playerId, String[] args) {
        if (command.equalsIgnoreCase("balance")) {
            player.sendMessage("Your balance is: " + playerValues.get(player.getUniqueId()) + "$");
        }
    }

    private void pay_command(String command, Player player, UUID playerId, String[] args) {
        if(!command.equalsIgnoreCase("pay")){
            return;
        }
        if (args.length != 2) {
            player.sendMessage("Usage: /pay <player> <amount>");
            return;
        }

        try {
            int amount = Integer.parseInt(args[1]);

            Double playerBalance = playerValues.get(playerId);

            if(playerBalance < amount || amount < 0){
                player.sendMessage("You do not have enough money to pay that amount.");
                return;
            }

            Player target_player = Bukkit.getPlayer(args[0]);

            if(target_player == null){
                player.sendMessage("Player not found.");
                return;
            }

            playerValues.put(playerId, playerValues.get(playerId) - amount);
            playerValues.put(target_player.getUniqueId(), playerValues.get(target_player.getUniqueId()) + amount);

            player.sendMessage("Paid " + amount + " to " + target_player.getName() + ". New balance: " + playerValues.get(playerId));
            target_player.sendMessage("Received " + amount + " from " + player.getName() + ". New balance: " + playerValues.get(target_player.getUniqueId()));
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid amount. Please enter a number.");
            return;
        }
    }

    private void sell_command(String command, Player player, UUID playerId, String[] args) {
        if(!command.equalsIgnoreCase("sell")){
            return;
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if(args.length != 1){
            int amount = itemInHand.getAmount();

            Double value = itemValues.get(itemInHand.getType());

            if(value == null){
                value = 0.1;
            }

            player.getInventory().removeItem(new ItemStack(itemInHand.getType(), amount));

            playerValues.put(playerId, playerValues.get(playerId) + (value * amount));

            player.sendMessage("Sold " + amount + " " + itemInHand.getType() + " for " + (value * amount) + ". New balance: " + playerValues.get(playerId));

            return;
        }

        int totalAmount = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == itemInHand.getType()) {
                totalAmount += item.getAmount();
            }
        }

        int sellAmount = Integer.parseInt(args[0]);

        if(sellAmount < 0) {
            player.kickPlayer("MAM CIE HUJKU");
            return;
        }

        if(totalAmount < sellAmount){
            player.sendMessage("You do not have enough items to sell that amount.");
            return;
        }

        Double value = itemValues.get(itemInHand.getType());

        if(value == null){
            value = 0.1;
        }

        player.getInventory().removeItem(new ItemStack(itemInHand.getType(), sellAmount));

        playerValues.put(playerId, playerValues.get(playerId) + (value * sellAmount));

        player.sendMessage("Sold " + sellAmount + " " + itemInHand.getType() + " for " + (value * sellAmount) + ". New balance: " + playerValues.get(playerId));


        return;
    }

    private void rynek_command(String command, Player player, UUID playerId, String[] args) {
        if (!command.equalsIgnoreCase("rynek")) {
            return;
        }
        if(!player.getWorld().getName().equals("world")){
            player.sendMessage("You can only use this command in the world.");
            return;
        }

        World world = Bukkit.getWorld("world");
        Location cords_to_rynek = new Location(world,0, 100, 0);
        player.teleport(cords_to_rynek);
        player.sendMessage("Teleportacja przebiegła pomyślnie");
    }

    private void withdraw_command(String command, Player player, UUID playerId, String[] args) {
        if (!command.equalsIgnoreCase("withdraw")) {
            return;
        }

        if (!args[0].matches("\\d+")) {
            player.sendMessage("Amount to withdraw should be a number");
            return;
        }

        int amount = Integer.parseInt(args[0]);

        if(amount < 0 || playerValues.get(playerId) < amount) {
            player.sendMessage("You are too poor to withdraw this amount");
            return;
        }

        ItemStack item = new ItemStack(Material.PAPER);

        ItemMeta meta = item.getItemMeta();

        if(meta == null) {
            player.sendMessage("Failed to withdraw, take a screenshot and send to admin");
            return;
        }

        meta.setDisplayName(amount + "$");

        NamespacedKey key = new NamespacedKey(this,"amount");

        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, amount);

        item.setItemMeta(meta);

        player.getInventory().addItem(item);

        playerValues.put(playerId, playerValues.get(playerId) - amount);

        player.sendMessage("You withdraw "+amount+"$, now your balance is "+playerValues.get(playerId));
    }
@EventHandler 
    public void onEntityExplode(EntityExplodeEvent event)
    {
        for(Block block : event.blockList())
        {
             if(block.hasMetadata("creator")){
                 event.blockList().remove(block);
             }
        }
    }
}
