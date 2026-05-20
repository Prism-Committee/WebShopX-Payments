package com.webshopx.payments.backend.data;

public class LocalClientInfo extends ClientInfo<LocalClientInfo> {
    @Override
    public boolean isOpen() {
        return true;
    }
}
