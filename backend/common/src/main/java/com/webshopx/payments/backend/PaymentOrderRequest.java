package com.webshopx.payments.backend;

/**
 * Backend order request shared by payment handlers.
 */
@SuppressWarnings("FieldMayBeFinal")
public class PaymentOrderRequest {
    private String playerName;
    private String method;
    private String subject;
    private String price;
    private boolean allowIncreasing;

    public PaymentOrderRequest(String playerName, String method, String subject, String price, boolean allowIncreasing) {
        this.playerName = playerName;
        this.method = method;
        this.subject = subject;
        this.price = price;
        this.allowIncreasing = allowIncreasing;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getMethod() {
        return method;
    }

    public String getSubject() {
        return subject;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public boolean isAllowIncreasing() {
        return allowIncreasing;
    }
}
