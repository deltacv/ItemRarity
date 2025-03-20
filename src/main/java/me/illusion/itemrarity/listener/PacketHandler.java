package me.illusion.itemrarity.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import me.illusion.itemrarity.ItemRarityPlugin;
import me.illusion.itemrarity.data.Rarity;
import me.illusion.utilities.item.ItemUtil;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PacketHandler {

    private final ItemRarityPlugin main;

    public PacketHandler(ItemRarityPlugin main) {
        this.main = main;
        registerListener(main);
    }

    private void registerListener(ItemRarityPlugin main) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();

        // Format (in order)
        // int syncId, int revision, int slot, ItemStack stack
        manager.addPacketListener(new PacketAdapter(main, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                ItemStack originalItem = packet.getItemModifier().read(0);

                if (originalItem != null) {
                    ItemStack modifiedItem = replaceData(originalItem.clone()); // Clone it, so we don't modify the real one
                    packet.getItemModifier().write(0, modifiedItem);
                }
            }
        });

        manager.addPacketListener(new PacketAdapter(main, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                List<ItemStack> items = packet.getItemListModifier().readSafely(0);

                if (items != null) {
                    List<ItemStack> modifiedItems = new ArrayList<>();
                    for (ItemStack item : items) {
                        modifiedItems.add(item != null ? replaceData(item.clone()) : null); // Clone each item before modifying
                    }
                    packet.getItemListModifier().write(0, modifiedItems);
                }
            }
        });


        // LISTEN FOR SERVERBOUND INVENTORY INTERACTIONS
        manager.addPacketListener(new PacketAdapter(main, PacketType.Play.Client.SET_CREATIVE_SLOT) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                ItemStack originalItem = packet.getItemModifier().read(0);

                if (originalItem != null) {
                    ItemStack modifiedItem = removeData(originalItem.clone()); // Clone before modifying
                    packet.getItemModifier().write(0, modifiedItem);
                }
            }
        });

        manager.addPacketListener(new PacketAdapter(main, PacketType.Play.Client.WINDOW_CLICK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                ItemStack originalItem = packet.getItemModifier().readSafely(0);

                if (originalItem != null) {
                    ItemStack modifiedItem = removeData(originalItem.clone());
                    packet.getItemModifier().write(0, modifiedItem);
                }
            }
        });
    }
    private ItemStack replaceData(ItemStack item) {
        if (item == null || item.getType().name().contains("AIR") || main.getRarityManager().isIgnored(item.getType())) {
            return item;
        }

        ItemStack copy = item.clone(); // Clone to avoid modifying real item
        ItemMeta copyMeta = copy.getItemMeta();

        if (copyMeta == null) return item; // Failsafe

        List<String> newLore = copyMeta.getLore();
        if (newLore == null) newLore = new ArrayList<>();

        newLore = removeLore(newLore); // Remove old rarity lines
        Rarity rarity = main.getRarityManager().getRarity(copy);
        newLore.add(rarity.getLore());

        copyMeta.setLore(ItemUtil.colorize(newLore));
        copy.setItemMeta(copyMeta);

        return copy;
    }

    /**
     * Removes rarity-related lore from an item before it is sent to the server.
     */
    private ItemStack removeData(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return item;

        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();

        if (meta.hasLore()) {
            List<String> newLore = new ArrayList<>(meta.getLore());
            newLore = removeLore(newLore); // Remove rarity lines
            meta.setLore(ItemUtil.colorize(newLore));
            copy.setItemMeta(meta);
        }

        return copy;
    }

    private List<String> removeLore(List<String> original) {
        List<String> copy = new ArrayList<>(original);

        for (Rarity rarity : main.getRarityManager().getRarities()) {
            List<String> secondCopy = new ArrayList<>(copy);
            for (String line : copy)
                if (line.startsWith(ChatColor.translateAlternateColorCodes('&', rarity.getLore()))) {
                    // System.out.println("removed " + line);
                    secondCopy.remove(line);
                }

            copy = secondCopy;
        }

        return copy;
    }
}
