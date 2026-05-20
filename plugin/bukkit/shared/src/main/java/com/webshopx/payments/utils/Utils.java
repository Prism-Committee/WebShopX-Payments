package com.webshopx.payments.utils;

import com.google.common.collect.Iterables;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.mrxiaom.pluginbase.BukkitPlugin;
import com.webshopx.payments.nms.NMS;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Random;

public class Utils {
    public static <T> T random(List<T> list, T def) {
        if (list.isEmpty()) return def;
        if (list.size() == 1) return list.get(0);
        return list.get(new Random().nextInt(list.size()));
    }

    public static void writeBase64(File file, byte @NotNull [] bytes) {
        try (FileWriter writer = new FileWriter(file)) {
            String encoded = Base64.getEncoder().encodeToString(bytes);
            writer.write(encoded);
        } catch (IOException e) {
            BukkitPlugin.getInstance().warn("写入 Base64 时出现错误", e);
        }
    }

    public static byte @Nullable [] readBase64(File file, int requireLength) {
        if (!file.exists()) return null;
        try (FileReader reader = new FileReader(file)) {
            char[] buffer = new char[16384];
            StringBuilder sb = new StringBuilder();
            int len;
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            byte[] decoded = Base64.getDecoder().decode(sb.toString());
            return decoded.length < requireLength ? null : decoded;
        } catch (IOException | IllegalArgumentException e) {
            BukkitPlugin.getInstance().warn("读取 Base64 时出现错误", e);
            return null;
        }
    }

    public static String consume(String[] args, int startIndex, String spliter) {
        StringBuilder sb = new StringBuilder();
        int last = args.length - 1;
        for (int i = startIndex; i < args.length; i++) {
            sb.append(args[i]);
            if (i < last) sb.append(spliter);
        }
        return sb.toString();
    }

    @SuppressWarnings({"deprecation"})
    public static MapRenderer getMapRenderer(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta instanceof MapMeta) {
            MapMeta map = (MapMeta) meta;
            try { // getMapView 在 1.13 加入
                MapView mapView = map.getMapView();
                if (mapView != null) {
                    return Iterables.getFirst(mapView.getRenderers(), null);
                }
            } catch (LinkageError ignored) {
                // 旧版本解决方案
                return NMS.getFirstRenderer((int) item.getDurability());
            }
        }
        return null;
    }
}
