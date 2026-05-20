package com.webshopx.payments.backend;

/**
 * Backend order response shared by payment handlers.
 */
@SuppressWarnings("FieldMayBeFinal")
public class PaymentOrderResponse {
    private String error;
    private String subType;
    private String orderId;
    private String money;
    private String paymentUrl;

    public PaymentOrderResponse(String error) {
        this.error = error;
        this.orderId = "";
        this.paymentUrl = "";
    }

    public PaymentOrderResponse(String subType, String orderId, String money, String paymentUrl) {
        this.error = "";
        this.subType = subType;
        this.orderId = orderId;
        this.money = money;
        this.paymentUrl = paymentUrl;
    }

    public String getError() {
        return error;
    }

    public String getSubType() {
        return subType;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getMoney() {
        return money;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }
}
