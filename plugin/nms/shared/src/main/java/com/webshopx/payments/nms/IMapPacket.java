package com.webshopx.payments.nms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IMapPacket {
    Object createMapPacket(int mapId, byte[] colors);

    void sendPacket(Player player, Object packet);

    byte[] getColors(MapRenderer renderer);

    @Nullable
    MapView getMap(@NotNull Integer mapId);

    default ItemStack overrideMapItem(ItemStack item) {
        return item;
    }
}
