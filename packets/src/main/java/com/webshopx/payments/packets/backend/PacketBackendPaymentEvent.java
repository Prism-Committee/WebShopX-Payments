package com.webshopx.payments.packets.backend;

import com.webshopx.payments.packets.common.IPacket;
import com.webshopx.payments.packets.common.NoResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后端向 Bukkit 插件推送的标准支付事件。
 */
@SuppressWarnings("FieldMayBeFinal")
public class PacketBackendPaymentEvent implements IPacket<NoResponse> {
    private String merchantOrderId;
    private String providerOrderId;
    private String status;
    private long amountMinor;
    private String currency;
    private String method;
    private String methodCode;
    private long paidAt;
    private String rawEventId;
    private Map<String, String> extra;

    public PacketBackendPaymentEvent(
            String merchantOrderId,
            String providerOrderId,
            String status,
            long amountMinor,
            String currency,
            String method,
            String methodCode,
            long paidAt,
            String rawEventId,
            Map<String, String> extra
    ) {
        this.merchantOrderId = merchantOrderId;
        this.providerOrderId = providerOrderId;
        this.status = status;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.method = method;
        this.methodCode = methodCode;
        this.paidAt = paidAt;
        this.rawEventId = rawEventId;
        this.extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
    }

    public String getMerchantOrderId() {
        return merchantOrderId;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getStatus() {
        return status;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMethod() {
        return method;
    }

    public String getMethodCode() {
        return methodCode;
    }

    public long getPaidAt() {
        return paidAt;
    }

    public String getRawEventId() {
        return rawEventId;
    }

    public Map<String, String> getExtra() {
        return extra;
    }

    @Override
    public Class<NoResponse> getResponsePacket() {
        return NoResponse.class;
    }
}
