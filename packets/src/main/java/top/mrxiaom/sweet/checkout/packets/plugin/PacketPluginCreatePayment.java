package top.mrxiaom.sweet.checkout.packets.plugin;

import top.mrxiaom.sweet.checkout.packets.common.IPacket;
import top.mrxiaom.sweet.checkout.packets.common.IResponsePacket;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebShopXPaymentApi 标准下单包。
 */
@SuppressWarnings("FieldMayBeFinal")
public class PacketPluginCreatePayment implements IPacket<PacketPluginCreatePayment.Response> {
    private String merchantOrderId;
    private String userId;
    private String playerUuid;
    private String method;
    private String subject;
    private String description;
    private long amountMinor;
    private String currency;
    private String returnUrl;
    private String notifyUrl;
    private long expiresAt;
    private Map<String, String> metadata;

    public PacketPluginCreatePayment(
            String merchantOrderId,
            String userId,
            String playerUuid,
            String method,
            String subject,
            String description,
            long amountMinor,
            String currency,
            String returnUrl,
            String notifyUrl,
            long expiresAt,
            Map<String, String> metadata
    ) {
        this.merchantOrderId = merchantOrderId;
        this.userId = userId;
        this.playerUuid = playerUuid;
        this.method = method;
        this.subject = subject;
        this.description = description;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.returnUrl = returnUrl;
        this.notifyUrl = notifyUrl;
        this.expiresAt = expiresAt;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public String getMerchantOrderId() {
        return merchantOrderId;
    }

    public String getUserId() {
        return userId;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getMethod() {
        return method;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public long getAmountMinor() {
        return amountMinor;
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

    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public Class<Response> getResponsePacket() {
        return Response.class;
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class Response implements IResponsePacket {
        private String error;
        private String merchantOrderId;
        private String providerOrderId;
        private String paymentUrl;
        private String qrCodeUrl;
        private long actualAmountMinor;
        private String currency;
        private String method;
        private String methodCode;
        private long expiresAt;
        private Map<String, String> extra;

        public Response(String error) {
            this.error = error;
            this.extra = new LinkedHashMap<>();
        }

        public Response(
                String merchantOrderId,
                String providerOrderId,
                String paymentUrl,
                String qrCodeUrl,
                long actualAmountMinor,
                String currency,
                String method,
                String methodCode,
                long expiresAt,
                Map<String, String> extra
        ) {
            this.error = "";
            this.merchantOrderId = merchantOrderId;
            this.providerOrderId = providerOrderId;
            this.paymentUrl = paymentUrl;
            this.qrCodeUrl = qrCodeUrl;
            this.actualAmountMinor = actualAmountMinor;
            this.currency = currency;
            this.method = method;
            this.methodCode = methodCode;
            this.expiresAt = expiresAt;
            this.extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
        }

        public String getError() {
            return error;
        }

        public String getMerchantOrderId() {
            return merchantOrderId;
        }

        public String getProviderOrderId() {
            return providerOrderId;
        }

        public String getPaymentUrl() {
            return paymentUrl;
        }

        public String getQrCodeUrl() {
            return qrCodeUrl;
        }

        public long getActualAmountMinor() {
            return actualAmountMinor;
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

        public long getExpiresAt() {
            return expiresAt;
        }

        public Map<String, String> getExtra() {
            return extra;
        }
    }
}
