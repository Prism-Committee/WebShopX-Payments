package com.webshopx.payments.packets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.bind.JsonTreeWriter;
import org.jetbrains.annotations.Nullable;
import com.webshopx.payments.packets.common.IPacket;

public class PacketSerializer {
    public static final Gson gson = new GsonBuilder().create();

    @SuppressWarnings({"rawtypes"})
    public static JsonObject serialize(IPacket packet) {
        Class<? extends IPacket> type = packet.getClass();
        JsonTreeWriter writer = new JsonTreeWriter();
        gson.toJson(packet, type, writer);
        JsonObject json = (JsonObject) writer.get();
        json.addProperty("class", type.getName());
        return json;
    }

    @Nullable
    @SuppressWarnings({"rawtypes"})
    public static IPacket deserialize(JsonObject json) {
        JsonPrimitive classJson = json.getAsJsonPrimitive("class");
        String className = classJson != null ? classJson.getAsString() : null;
        if (className == null) return null;
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
        Object result = gson.fromJson(json, type);
        return result instanceof IPacket ? (IPacket) result : null;
    }
}
