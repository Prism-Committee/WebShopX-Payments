package com.webshopx.payments.api;

import com.webshopx.payments.func.PaymentAPI;

public abstract class PaymentClient {
    protected final PaymentAPI parent;

    protected PaymentClient(PaymentAPI parent) {
        this.parent = parent;
    }

    public PaymentAPI getParent() {
        return parent;
    }

    public abstract String getUrl();

    public abstract boolean isOpen();

    public abstract void close();

    public abstract void connect();

    public abstract void send(String message);
}
