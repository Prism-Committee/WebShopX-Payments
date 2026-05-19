package top.mrxiaom.sweet.checkout.api;

import top.mrxiaom.sweet.checkout.packets.backend.PacketBackendPaymentEvent;

public interface PaymentEventBridge {
    void handleBackendPaymentConfirm(String providerOrderId, String money);

    void handleBackendPaymentCancel(String providerOrderId, String reason);

    void handleBackendPaymentEvent(PacketBackendPaymentEvent event);

    void retryUndeliveredNotifications();

    void close();
}
