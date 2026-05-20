package com.webshopx.payments.packets.common;

@SuppressWarnings({"rawtypes"})
public class NoResponse implements IPacket {
    @Override
    public Class<?> getResponsePacket() {
        return null;
    }
}
