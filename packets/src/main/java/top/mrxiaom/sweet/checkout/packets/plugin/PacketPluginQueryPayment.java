package top.mrxiaom.sweet.checkout.packets.plugin;

import top.mrxiaom.sweet.checkout.packets.common.IPacket;
import top.mrxiaom.sweet.checkout.packets.common.IResponsePacket;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebShopXPaymentApi 标准查单包。
 */
@SuppressWarnings("FieldMayBeFinal")
public class PacketPluginQueryPayment implements IPacket<PacketPluginQueryPayment.Response> {
    private String merchantOrderId;
    private String providerOrderId;
    private String method;
    private String methodCode;

    public PacketPluginQueryPayment(String merchantOrderId, String providerOrderId, String method, String methodCode) {
        this.merchantOrderId = merchantOrderId;
        this.providerOrderId = providerOrderId;
        this.method = method;
        this.methodCode = methodCode;
    }

    public String getMerchantOrderId() {
        return merchantOrderId;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getMethod() {
        return method;
    }

    public String getMethodCode() {
        return methodCode;
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
        private String status;
        private String method;
        private String methodCode;
        private String paymentUrl;
        private String qrCodeUrl;
        private long amountMinor;
        private String currency;
        private long expiresAt;
        private long paidAt;
        private Map<String, String> extra;

        public Response(String error) {
            this.error = error;
            this.status = "UNKNOWN";
            this.extra = new LinkedHashMap<>();
        }

        public Response(
                String merchantOrderId,
                String providerOrderId,
                String status,
                String method,
                String methodCode,
                String paymentUrl,
                String qrCodeUrl,
                long amountMinor,
                String currency,
                long expiresAt,
                long paidAt,
                Map<String, String> extra
        ) {
            this.error = "";
            this.merchantOrderId = merchantOrderId;
            this.providerOrderId = providerOrderId;
            this.status = status;
            this.method = method;
            this.methodCode = methodCode;
            this.paymentUrl = paymentUrl;
            this.qrCodeUrl = qrCodeUrl;
            this.amountMinor = amountMinor;
            this.currency = currency;
            this.expiresAt = expiresAt;
            this.paidAt = paidAt;
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

        public String getStatus() {
            return status;
        }

        public String getMethod() {
            return method;
        }

        public String getMethodCode() {
            return methodCode;
        }

        public String getPaymentUrl() {
            return paymentUrl;
        }

        public String getQrCodeUrl() {
            return qrCodeUrl;
        }

        public long getAmountMinor() {
            return amountMinor;
        }

        public String getCurrency() {
            return currency;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public long getPaidAt() {
            return paidAt;
        }

        public Map<String, String> getExtra() {
            return extra;
        }
    }
}
