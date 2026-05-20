package com.webshopx.payments.nms;

import net.minecraft.server.v1_8_R3.MapIcon;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutMap;
import net.minecraft.server.v1_8_R3.WorldMap;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MapPacket_v1_8_R3 implements IMapPacket {
    @Override
    public Object createMapPacket(int mapId, byte[] colors) {
        List<MapIcon> mapIcons = new ArrayList<>();
        return new PacketPlayOutMap(mapId, (byte) 0, mapIcons, colors, 0, 0, 128, 128);
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        CraftPlayer p = (CraftPlayer) player;
        p.getHandle().playerConnection.sendPacket((Packet<?>) packet);
    }

    @Override
    public byte[] getColors(MapRenderer renderer) {
        Class<?> type = renderer.getClass();
        try {
            Field worldMapField = type.getDeclaredField("worldMap");
            worldMapField.setAccessible(true);
            WorldMap worldMap = (WorldMap) worldMapField.get(renderer);
            return worldMap.colors;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(type.getName(), e);
        }
    }

    @Override
    @SuppressWarnings({"deprecation"})
    public @Nullable MapView getMap(@NotNull Integer mapId) {
        return Bukkit.getMap(mapId.shortValue());
    }
}
