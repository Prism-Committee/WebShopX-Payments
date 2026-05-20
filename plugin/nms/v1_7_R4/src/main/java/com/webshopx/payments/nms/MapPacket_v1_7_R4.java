package com.webshopx.payments.nms;

import net.minecraft.server.v1_7_R4.*;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_7_R4.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_7_R4.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.IdentityHashMap;

public class MapPacket_v1_7_R4 implements IMapPacket {
    public static class MapItem extends ItemWorldMap {
        private MapItem() {
            c("map");
            f("map_filled");
        }
        @Override
        public boolean h() {
            return false;
        }
        @Override
        public Packet c(net.minecraft.server.v1_7_R4.ItemStack itemstack, World world, EntityHuman entityhuman) {
            return null;
        }
    }
    public static class PacketMap {
        public final int mapId;
        public final byte[] buffer;

        public PacketMap(int mapId, byte[] buffer) {
            this.mapId = mapId;
            this.buffer = buffer;
        }
    }
    MapItem MAP = new MapItem();
    @SuppressWarnings({"unchecked"})
    public MapPacket_v1_7_R4() {
        RegistryMaterials registry = Item.REGISTRY;
        // 获取原地图类型的 ID
        Item vanillaMap = Items.MAP;
        int mapId = Item.getId(vanillaMap);
        try {
            Field field = null, field1 = null;
            for (Field f : RegistryMaterials.class.getDeclaredFields()) {
                if (f.getType().isAssignableFrom(RegistryID.class)) {
                    field = f;
                    field.setAccessible(true);
                    break;
                }
            }
            if (field == null) {
                throw new NoSuchFieldException("Can't find RegistryID");
            }
            RegistryID registryID = (RegistryID) field.get(registry);
            for (Field f : RegistryID.class.getDeclaredFields()) {
                if (f.getType().isAssignableFrom(IdentityHashMap.class)) {
                    field1 = f;
                    field1.setAccessible(true);
                    break;
                }
            }
            if (field1 == null) {
                throw new NoSuchFieldException("Can't find IdentityHashMap");
            }
            IdentityHashMap<Object, Integer> map = (IdentityHashMap<Object, Integer>) field1.get(registryID);
            // 将 新的地图类型 注入进 类型->数字ID 对照表
            // RegistryID 中还有个 数字ID->类型 列表，那个列表绝对不能动
            map.put(MAP, mapId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("物品 Registry 注入失败，插件暂不支持当前服务端", e);
        }
    }

    @Override
    public Object createMapPacket(int mapId, byte[] colors) {
        return new PacketMap(mapId, colors);
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        CraftPlayer p = (CraftPlayer) player;
        if (packet instanceof PacketMap) {
            PacketMap data = (PacketMap) packet;
            for (int x = 0; x < 128; ++x) {
                byte[] bytes = new byte[131];
                bytes[1] = (byte)x;

                for(int y = 0; y < 128; ++y) {
                    bytes[y + 3] = data.buffer[y * 128 + x];
                }

                PacketPlayOutMap packetPlayOutMap = new PacketPlayOutMap(data.mapId, bytes, (byte) 0);
                p.getHandle().playerConnection.sendPacket(packetPlayOutMap);
            }
            return;
        }
        p.getHandle().playerConnection.sendPacket((Packet) packet);
    }

    @Override
    public byte[] getColors(MapRenderer renderer) {
        Class<?> type = renderer.getClass();
        try {
            Field worldMapField = type.getDeclaredField("worldMap");
            worldMapField.setAccessible(true);
            WorldMap worldMap = (WorldMap) worldMapField.get(renderer);

            byte[] buffer = new byte[16384];
            Arrays.fill(buffer, (byte)0);

            for(int x = 0; x < 128; ++x) {
                for(int y = 0; y < 128; ++y) {
                    int i = y * 128 + x;
                    byte color = worldMap.colors[i];
                    if (color >= 0 || color <= -113) {
                        buffer[i] = color;
                    }
                }
            }
            return buffer;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(type.getName(), e);
        }
    }

    @Override
    @SuppressWarnings({"deprecation"})
    public @Nullable MapView getMap(@NotNull Integer mapId) {
        return Bukkit.getMap(mapId.shortValue());
    }

    @Override
    public ItemStack overrideMapItem(ItemStack item) {
        net.minecraft.server.v1_7_R4.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        Item type = nmsItem.getItem();
        if (type instanceof ItemWorldMap && !(type instanceof MapItem)) {
            nmsItem.setItem(MAP);
            return CraftItemStack.asCraftMirror(nmsItem);
        }
        return item;
    }
}
