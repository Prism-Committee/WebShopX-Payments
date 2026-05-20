package com.webshopx.payments.packets.common;

@SuppressWarnings({"rawtypes"})
public interface IPacket<T extends IPacket> {
    Class<T> getResponsePacket();

    default boolean isResponsePacket(Object packet) {
        Class<T> type = getResponsePacket();
        return type != null && type.isInstance(packet);
    }
}
