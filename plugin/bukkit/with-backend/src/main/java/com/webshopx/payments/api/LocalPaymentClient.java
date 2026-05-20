package com.webshopx.payments.api;

import com.webshopx.payments.backend.BukkitMain;
import com.webshopx.payments.backend.data.LocalClientInfo;
import com.webshopx.payments.func.PaymentAPI;

public class LocalPaymentClient extends PaymentClient {
    private final BukkitMain main;
    private final LocalClientInfo info;

    public LocalPaymentClient(BukkitMain main, PaymentAPI parent) {
        super(parent);
        this.main = main;
        this.info = new LocalClientInfo();
    }

    @Override
    public String getUrl() {
        return "local";
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void close() {
    }

    @Override
    public void connect() {
    }

    @Override
    public void send(String message) {
        main.getServer().onMessage(info, message);
    }
}
