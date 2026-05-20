package com.webshopx.payments.packets.common;

public interface IResponsePacket extends IPacket<NoResponse> {
    @Override
    default Class<NoResponse> getResponsePacket() {
        return null;
    }
}
