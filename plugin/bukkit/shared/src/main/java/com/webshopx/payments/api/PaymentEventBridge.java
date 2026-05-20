package com.webshopx.payments.api;

import com.webshopx.payments.packets.backend.PacketBackendPaymentEvent;

public interface PaymentEventBridge {
    void handleBackendPaymentEvent(PacketBackendPaymentEvent event);

    void retryUndeliveredNotifications();

    void close();
}
