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
    private String description;
    private String currency;
    private String returnUrl;
    private String notifyUrl;
    private long expiresAt;

    public PaymentOrderRequest(String playerName, String method, String subject, String price, boolean allowIncreasing) {
        this.playerName = playerName;
        this.method = method;
        this.subject = subject;
        this.price = price;
        this.allowIncreasing = allowIncreasing;
    }

    public PaymentOrderRequest(
            String playerName,
            String method,
            String subject,
            String price,
            boolean allowIncreasing,
            String description,
            String currency,
            String returnUrl,
            String notifyUrl,
            long expiresAt
    ) {
        this(playerName, method, subject, price, allowIncreasing);
        this.description = description;
        this.currency = currency;
        this.returnUrl = returnUrl;
        this.notifyUrl = notifyUrl;
        this.expiresAt = expiresAt;
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

    public String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}
