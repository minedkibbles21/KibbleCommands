package com.minedkibbles21.kibblecommands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AdminGui implements Listener {
    private final KibbleCommands plugin;

    public AdminGui(KibbleCommands plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        if (!canUseGui(player)) {
            sendMsg(player, plugin.prefix() + "&cYou do not have permission to open this GUI.");
            return;
        }
        GuiHolder holder = new GuiHolder(GuiView.MAIN, null);
        Inventory inventory = Bukkit.createInventory(holder, 54, title("KibbleCommands Admin"));
        holder.setInventory(inventory);
        
        List<AliasDefinition> aliases = new ArrayList<>(plugin.getDefinitions().values());
        aliases.sort(Comparator.comparing(AliasDefinition::getAlias));
        
        int slot = 0;
        for (AliasDefinition definition : aliases) {
            if (slot >= 45) break;
            inventory.setItem(slot, aliasItem(definition));
            holder.aliasSlots.put(slot, definition.getAlias());
            slot++;
        }
        
        inventory.setItem(45, item(Material.BOOK, "Help", NamedTextColor.YELLOW, "Print admin commands to chat."));
        inventory.setItem(46, item(Material.ANVIL, "Add Alias", NamedTextColor.GREEN, "Use /kc add <alias> <command...>."));
        inventory.setItem(48, item(Material.EMERALD_BLOCK, "Reload", NamedTextColor.GREEN, "Reload config.yml and aliases."));
        inventory.setItem(49, item(Material.COMPASS, "Refresh", NamedTextColor.AQUA, "Refresh this menu."));
        inventory.setItem(53, item(Material.BARRIER, "Close", NamedTextColor.RED, "Close this menu."));
        player.openInventory(inventory);
    }

    private void openDetails(Player player, String alias) {
        AliasDefinition definition = plugin.getDefinitions().get(alias);
        if (definition == null) {
            sendMsg(player, plugin.prefix() + "&cThat alias no longer exists.");
            openMain(player);
            return;
        }
        GuiHolder holder = new GuiHolder(GuiView.DETAILS, alias);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("Alias: /" + alias));
        holder.setInventory(inventory);
        
        inventory.setItem(10, item(Material.COMMAND_BLOCK, "/" + definition.getAlias(), NamedTextColor.GOLD, "Target: /" + definition.getCommand(), "Executes as: " + definition.getExecuteAs()));
        inventory.setItem(12, item(Material.TRIPWIRE_HOOK, "Permission", NamedTextColor.YELLOW, definition.hasPermission() ? definition.getPermission() : "No custom permission", "Global use permission can also be enabled in config.yml."));
        inventory.setItem(14, item(Material.CLOCK, "Rules", NamedTextColor.AQUA, "Pass args: " + yesNo(definition.isPassArgs()), "Cooldown: " + (definition.getCooldown() > 0 ? definition.getCooldown() + "s" : "disabled"), "Player only: " + yesNo(definition.isPlayerOnly()), "Console only: " + yesNo(definition.isConsoleOnly())));
        inventory.setItem(16, item(Material.REDSTONE_BLOCK, "Remove Alias", NamedTextColor.RED, "Open confirmation before deleting."));
        inventory.setItem(22, item(Material.ARROW, "Back", NamedTextColor.GRAY, "Return to the alias list."));
        player.openInventory(inventory);
    }

    private void openRemoveConfirm(Player player, String alias) {
        GuiHolder holder = new GuiHolder(GuiView.CONFIRM_REMOVE, alias);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("Remove /" + alias + "?"));
        holder.setInventory(inventory);
        
        inventory.setItem(11, item(Material.LIME_WOOL, "Confirm Remove", NamedTextColor.GREEN, "Delete /" + alias + " from config.yml."));
        inventory.setItem(15, item(Material.RED_WOOL, "Cancel", NamedTextColor.RED, "Keep this alias."));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
        if (!(inventoryHolder instanceof GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!canUseGui(player)) {
            player.closeInventory();
            sendMsg(player, plugin.prefix() + "&cYou do not have permission to use this GUI.");
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        switch (holder.view) {
            case MAIN -> handleMainClick(player, holder, slot);
            case DETAILS -> handleDetailsClick(player, holder, slot);
            case CONFIRM_REMOVE -> handleConfirmClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMainClick(Player player, GuiHolder holder, int slot) {
        String alias = holder.aliasSlots.get(slot);
        if (alias != null) {
            openDetails(player, alias);
            return;
        }
        switch (slot) {
            case 45 -> sendCommandHelp(player);
            case 46 -> sendAddInstructions(player);
            case 48 -> reloadAliases(player);
            case 49 -> openMain(player);
            case 53 -> player.closeInventory();
        }
    }

    private void handleDetailsClick(Player player, GuiHolder holder, int slot) {
        switch (slot) {
            case 16 -> openRemoveConfirm(player, holder.alias);
            case 22 -> openMain(player);
        }
    }

    private void handleConfirmClick(Player player, GuiHolder holder, int slot) {
        switch (slot) {
            case 11 -> removeAlias(player, holder.alias);
            case 15 -> openDetails(player, holder.alias);
        }
    }

    private void reloadAliases(Player player) {
        if (!canReload(player)) {
            sendMsg(player, plugin.prefix() + "&cYou do not have permission to reload.");
            return;
        }
        plugin.reloadConfig();
        plugin.getCooldownTracker().clearAll();
        plugin.reloadAliases();
        sendMsg(player, plugin.prefix() + "&aConfiguration reloaded.");
        openMain(player);
    }

    private void removeAlias(Player player, String alias) {
        if (!canManage(player)) {
            sendMsg(player, plugin.prefix() + "&cYou do not have permission to remove aliases.");
            openMain(player);
            return;
        }
        boolean removed = plugin.removeAlias(alias);
        if (removed) {
            sendMsg(player, plugin.prefix() + "&aAlias &e/" + alias + " &ahas been removed.");
        } else {
            sendMsg(player, plugin.prefix() + "&cAlias &e/" + alias + " &cwas not found.");
        }
        openMain(player);
    }

    private void sendCommandHelp(Player player) {
        player.closeInventory();
        sendMsg(player, "&8&m                                        ");
        sendMsg(player, "  &6&lKibbleCommands &7Admin Commands");
        sendMsg(player, "&8&m                                        ");
        sendMsg(player, "  &e/kc gui &7- Open this GUI.");
        sendMsg(player, "  &e/kc list &7- List active aliases.");
        sendMsg(player, "  &e/kc info <alias> &7- Show alias details.");
        sendMsg(player, "  &e/kc add <alias> <command...> &7- Add an alias.");
        sendMsg(player, "  &e/kc remove <alias> &7- Remove an alias.");
        sendMsg(player, "  &e/kc reload &7- Reload config.yml.");
        sendMsg(player, "&8&m                                        ");
    }

    private void sendAddInstructions(Player player) {
        player.closeInventory();
        sendMsg(player, plugin.prefix() + "&7Use &e/kc add <alias> <command...> &7to create a quick alias.");
        sendMsg(player, plugin.prefix() + "&7Example: &e/kc add healme essentials:heal {player}");
        sendMsg(player, plugin.prefix() + "&7Edit &econfig.yml &7afterward for permissions, cooldowns, and console execution.");
    }

    private ItemStack aliasItem(AliasDefinition definition) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("Target: /" + definition.getCommand(), NamedTextColor.GRAY));
        lore.add(line("Executes as: " + definition.getExecuteAs(), NamedTextColor.GRAY));
        if (definition.hasPermission()) {
            lore.add(line("Permission: " + definition.getPermission(), NamedTextColor.YELLOW));
        }
        if (definition.getCooldown() > 0) {
            lore.add(line("Cooldown: " + definition.getCooldown() + "s", NamedTextColor.AQUA));
        }
        lore.add(line("Click to view details.", NamedTextColor.GREEN));
        return item(Material.NAME_TAG, text("/" + definition.getAlias(), NamedTextColor.GOLD), lore);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(line(line, NamedTextColor.GRAY));
        }
        return item(material, text(name, color), lines);
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private Component title(String title) {
        return text(title, NamedTextColor.GOLD);
    }

    private Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private boolean canUseGui(Player player) {
        return player.isOp() || player.hasPermission("kibblecommands.admin") || player.hasPermission("kibblecommands.gui");
    }

    private boolean canManage(Player player) {
        return player.isOp() || player.hasPermission("kibblecommands.admin");
    }

    private boolean canReload(Player player) {
        return canManage(player) || player.hasPermission("kibblecommands.reload");
    }

    private void sendMsg(CommandSender sender, String msg) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }

    private static final class GuiHolder implements InventoryHolder {
        private final GuiView view;
        private final String alias;
        private final Map<Integer, String> aliasSlots = new HashMap<>();
        private Inventory inventory;

        private GuiHolder(GuiView view, String alias) {
            this.view = view;
            this.alias = alias;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum GuiView {
        MAIN,
        DETAILS,
        CONFIRM_REMOVE
    }
}
