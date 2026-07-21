package com.minedkibbles21.kibblecommands.gui;

import com.minedkibbles21.kibblecommands.KibbleCommands;
import com.minedkibbles21.kibblecommands.models.AliasDefinition;
import com.minedkibbles21.kibblecommands.utils.MessageUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AdminGuiListener
implements Listener {
    private final KibbleCommands plugin;

    public AdminGuiListener(KibbleCommands plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        if (!this.canUseGui(player)) {
            MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&cYou do not have permission to open this GUI.");
            return;
        }
        GuiHolder holder = new GuiHolder(GuiView.MAIN, null);
        Inventory inventory = Bukkit.createInventory((InventoryHolder)holder, (int)54, (Component)this.title("KibbleCommands Admin"));
        holder.setInventory(inventory);
        ArrayList<AliasDefinition> aliases = new ArrayList<AliasDefinition>(this.plugin.getAliasManager().getDefinitions().values());
        aliases.sort(Comparator.comparing(AliasDefinition::getAlias));
        int slot = 0;
        for (AliasDefinition definition : aliases) {
            if (slot >= 45) break;
            inventory.setItem(slot, this.aliasItem(definition));
            holder.aliasSlots.put(slot, definition.getAlias());
            ++slot;
        }
        inventory.setItem(45, this.item(Material.BOOK, "Help", NamedTextColor.YELLOW, "Print admin commands to chat."));
        inventory.setItem(46, this.item(Material.ANVIL, "Add Alias", NamedTextColor.GREEN, "Use /kc add <alias> <command...>."));
        inventory.setItem(48, this.item(Material.EMERALD_BLOCK, "Reload", NamedTextColor.GREEN, "Reload config.yml and aliases."));
        inventory.setItem(49, this.item(Material.COMPASS, "Refresh", NamedTextColor.AQUA, "Refresh this menu."));
        inventory.setItem(53, this.item(Material.BARRIER, "Close", NamedTextColor.RED, "Close this menu."));
        player.openInventory(inventory);
    }

    private void openDetails(Player player, String alias) {
        AliasDefinition definition = this.plugin.getAliasManager().getDefinition(alias);
        if (definition == null) {
            MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&cThat alias no longer exists.");
            this.openMain(player);
            return;
        }
        GuiHolder holder = new GuiHolder(GuiView.DETAILS, alias);
        Inventory inventory = Bukkit.createInventory((InventoryHolder)holder, (int)27, (Component)this.title("Alias: /" + alias));
        holder.setInventory(inventory);
        inventory.setItem(10, this.item(Material.COMMAND_BLOCK, "/" + definition.getAlias(), NamedTextColor.GOLD, "Target: /" + definition.getCommand(), "Executes as: " + definition.getExecuteAs()));
        inventory.setItem(12, this.item(Material.TRIPWIRE_HOOK, "Permission", NamedTextColor.YELLOW, definition.hasPermission() ? definition.getPermission() : "No custom permission", "Global use permission can also be enabled in config.yml."));
        inventory.setItem(14, this.item(Material.CLOCK, "Rules", NamedTextColor.AQUA, "Pass args: " + this.yesNo(definition.isPassArgs()), "Cooldown: " + (String)(definition.getCooldown() > 0 ? definition.getCooldown() + "s" : "disabled"), "Player only: " + this.yesNo(definition.isPlayerOnly()), "Console only: " + this.yesNo(definition.isConsoleOnly())));
        inventory.setItem(16, this.item(Material.REDSTONE_BLOCK, "Remove Alias", NamedTextColor.RED, "Open confirmation before deleting."));
        inventory.setItem(22, this.item(Material.ARROW, "Back", NamedTextColor.GRAY, "Return to the alias list."));
        player.openInventory(inventory);
    }

    private void openRemoveConfirm(Player player, String alias) {
        GuiHolder holder = new GuiHolder(GuiView.CONFIRM_REMOVE, alias);
        Inventory inventory = Bukkit.createInventory((InventoryHolder)holder, (int)27, (Component)this.title("Remove /" + alias + "?"));
        holder.setInventory(inventory);
        inventory.setItem(11, this.item(Material.LIME_WOOL, "Confirm Remove", NamedTextColor.GREEN, "Delete /" + alias + " from config.yml."));
        inventory.setItem(15, this.item(Material.RED_WOOL, "Cancel", NamedTextColor.RED, "Keep this alias."));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
        if (!(inventoryHolder instanceof GuiHolder)) {
            return;
        }
        GuiHolder holder = (GuiHolder)inventoryHolder;
        event.setCancelled(true);
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player)humanEntity;
        if (!this.canUseGui(player)) {
            player.closeInventory();
            MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&cYou do not have permission to use this GUI.");
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        switch (holder.view.ordinal()) {
            case 0: {
                this.handleMainClick(player, holder, slot);
                break;
            }
            case 1: {
                this.handleDetailsClick(player, holder, slot);
                break;
            }
            case 2: {
                this.handleConfirmClick(player, holder, slot);
            }
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
            this.openDetails(player, alias);
            return;
        }
        switch (slot) {
            case 45: {
                this.sendCommandHelp(player);
                break;
            }
            case 46: {
                this.sendAddInstructions(player);
                break;
            }
            case 48: {
                this.reloadAliases(player);
                break;
            }
            case 49: {
                this.openMain(player);
                break;
            }
            case 53: {
                player.closeInventory();
                break;
            }
        }
    }

    private void handleDetailsClick(Player player, GuiHolder holder, int slot) {
        switch (slot) {
            case 16: {
                this.openRemoveConfirm(player, holder.alias);
                break;
            }
            case 22: {
                this.openMain(player);
                break;
            }
        }
    }

    private void handleConfirmClick(Player player, GuiHolder holder, int slot) {
        switch (slot) {
            case 11: {
                this.removeAlias(player, holder.alias);
                break;
            }
            case 15: {
                this.openDetails(player, holder.alias);
                break;
            }
        }
    }

    private void reloadAliases(Player player) {
        if (!this.canReload(player)) {
            MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&cYou do not have permission to reload.");
            return;
        }
        this.plugin.reloadConfig();
        this.plugin.getCooldownManager().clearAll();
        this.plugin.getAliasManager().reload();
        MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&aConfiguration reloaded.");
        this.openMain(player);
    }

    private void removeAlias(Player player, String alias) {
        if (!this.canManage(player)) {
            MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&cYou do not have permission to remove aliases.");
            this.openMain(player);
            return;
        }
        boolean removed = this.plugin.getAliasManager().removeAlias(alias);
        if (removed) {
            MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&aAlias &e/" + alias + " &ahas been removed.");
        } else {
            MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&cAlias &e/" + alias + " &cwas not found.");
        }
        this.openMain(player);
    }

    private void sendCommandHelp(Player player) {
        player.closeInventory();
        MessageUtil.send((CommandSender)player, "&8&m                                        ");
        MessageUtil.send((CommandSender)player, "  &6&lKibbleCommands &7Admin Commands");
        MessageUtil.send((CommandSender)player, "&8&m                                        ");
        MessageUtil.send((CommandSender)player, "  &e/kc gui &7- Open this GUI.");
        MessageUtil.send((CommandSender)player, "  &e/kc list &7- List active aliases.");
        MessageUtil.send((CommandSender)player, "  &e/kc info <alias> &7- Show alias details.");
        MessageUtil.send((CommandSender)player, "  &e/kc add <alias> <command...> &7- Add an alias.");
        MessageUtil.send((CommandSender)player, "  &e/kc remove <alias> &7- Remove an alias.");
        MessageUtil.send((CommandSender)player, "  &e/kc reload &7- Reload config.yml.");
        MessageUtil.send((CommandSender)player, "&8&m                                        ");
    }

    private void sendAddInstructions(Player player) {
        player.closeInventory();
        MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&7Use &e/kc add <alias> <command...> &7to create a quick alias.");
        MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&7Example: &e/kc add healme essentials:heal {player}");
        MessageUtil.send((CommandSender)player, this.plugin.prefix() + "&7Edit &econfig.yml &7afterward for permissions, cooldowns, and console execution.");
    }

    private ItemStack aliasItem(AliasDefinition definition) {
        ArrayList<Component> lore = new ArrayList<Component>();
        lore.add(this.line("Target: /" + definition.getCommand(), NamedTextColor.GRAY));
        lore.add(this.line("Executes as: " + definition.getExecuteAs(), NamedTextColor.GRAY));
        if (definition.hasPermission()) {
            lore.add(this.line("Permission: " + definition.getPermission(), NamedTextColor.YELLOW));
        }
        if (definition.getCooldown() > 0) {
            lore.add(this.line("Cooldown: " + definition.getCooldown() + "s", NamedTextColor.AQUA));
        }
        lore.add(this.line("Click to view details.", NamedTextColor.GREEN));
        return this.item(Material.NAME_TAG, this.text("/" + definition.getAlias(), NamedTextColor.GOLD), lore);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, String ... lore) {
        ArrayList<Component> lines = new ArrayList<Component>();
        for (String line : lore) {
            lines.add(this.line(line, NamedTextColor.GRAY));
        }
        return this.item(material, this.text(name, color), lines);
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
        return this.text(title, NamedTextColor.GOLD);
    }

    private Component text(String text, NamedTextColor color) {
        return Component.text((String)text, (TextColor)color).decoration(TextDecoration.ITALIC, false);
    }

    private Component line(String text, NamedTextColor color) {
        return Component.text((String)text, (TextColor)color).decoration(TextDecoration.ITALIC, false);
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
        return this.canManage(player) || player.hasPermission("kibblecommands.reload");
    }

    private static final class GuiHolder
    implements InventoryHolder {
        private final GuiView view;
        private final String alias;
        private final Map<Integer, String> aliasSlots = new HashMap<Integer, String>();
        private Inventory inventory;

        private GuiHolder(GuiView view, String alias) {
            this.view = view;
            this.alias = alias;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public Inventory getInventory() {
            return this.inventory;
        }
    }

    private static enum GuiView {
        MAIN,
        DETAILS,
        CONFIRM_REMOVE;

    }
}

