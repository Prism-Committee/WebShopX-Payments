package com.webshopx.payments;

import com.webshopx.payment.api.PaymentCreateRequest;
import com.webshopx.payment.api.PaymentCreateResult;
import com.webshopx.payment.api.PaymentListener;
import com.webshopx.payment.api.PaymentMethod;
import com.webshopx.payment.api.PaymentNotify;
import com.webshopx.payment.api.PaymentNotifyResult;
import com.webshopx.payment.api.PaymentQueryRequest;
import com.webshopx.payment.api.PaymentQueryResult;
import com.webshopx.payment.api.PaymentStatus;
import com.webshopx.payment.api.WebShopXPaymentApi;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import com.webshopx.payments.PluginCommon;
import com.webshopx.payments.api.PaymentEventBridge;
import com.webshopx.payments.func.PaymentAPI;
import com.webshopx.payments.packets.backend.PacketBackendPaymentEvent;
import com.webshopx.payments.packets.plugin.PacketPluginCreatePayment;
import com.webshopx.payments.packets.plugin.PacketPluginQueryPayment;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class WsxPaymentApi implements WebShopXPaymentApi, PaymentEventBridge {
    private static final String PROVIDER_ID = "webshopx-payments";
    private static final String DISPLAY_NAME = "WebShopX Payments";

    private final PluginCommon plugin;
    private final File ordersFile;
    private final Map<String, PaymentListener> listeners = new ConcurrentHashMap<>();
    private final Map<String, OrderRecord> byMerchantOrderId = new ConcurrentHashMap<>();
    private final Map<String, OrderRecord> byProviderOrderId = new ConcurrentHashMap<>();

    public WsxPaymentApi(PluginCommon plugin) {
        this.plugin = plugin;
        this.ordersFile = new File(plugin.getDataFolder(), "wsxpay-orders.yml");
        loadOrders();
        plugin.getScheduler().runTaskTimer(this::retryUndeliveredNotifications, 20L * 30L, 20L * 60L);
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Set<PaymentMethod> supportedMethods() {
        EnumSet<PaymentMethod> methods = EnumSet.noneOf(PaymentMethod.class);
        if (isMethodEnabled("alipay")) methods.add(PaymentMethod.ALIPAY);
        if (isMethodEnabled("wechat")) methods.add(PaymentMethod.WECHAT);
        if (isMethodEnabled("paypal")) methods.add(PaymentMethod.PAYPAL);
        if (isMethodEnabled("mercadopago")) methods.add(PaymentMethod.CUSTOM);
        if (!methods.isEmpty()) methods.add(PaymentMethod.AUTO);
        return Collections.unmodifiableSet(methods);
    }

    @Override
    public PaymentCreateResult createPayment(PaymentCreateRequest request) {
        if (request == null) {
            return createError(null, "INVALID_REQUEST", "request is null");
        }
        String merchantOrderId = trimToNull(request.getMerchantOrderId());
        if (merchantOrderId == null) {
            return createError(null, "INVALID_REQUEST", "merchantOrderId is required");
        }
        if (request.getAmountMinor() <= 0L) {
            return createError(merchantOrderId, "INVALID_AMOUNT", "amountMinor must be greater than 0");
        }
        String methodCode = resolveMethodCode(request.getPreferredMethod(), request.getMethodCode());
        if (methodCode == null) {
            return createError(merchantOrderId, "METHOD_UNSUPPORTED", "payment method is not enabled");
        }
        String currency = normalizeCurrency(request.getCurrency());
        if (currency == null) {
            return createError(merchantOrderId, "INVALID_CURRENCY", "currency is required");
        }
        OrderRecord existing = byMerchantOrderId.get(merchantOrderId);
        if (existing != null) {
            if (isClosed(existing.status) && existing.status != PaymentStatus.SUCCESS) {
                return createError(merchantOrderId, "ORDER_ALREADY_CLOSED", "payment order is already closed");
            }
            return toCreateResult(existing);
        }

        PaymentAPI api = PaymentAPI.inst();
        PacketPluginCreatePayment packet = new PacketPluginCreatePayment(
                merchantOrderId,
                request.getUserId(),
                request.getPlayerUuid() == null ? null : request.getPlayerUuid().toString(),
                methodCode,
                request.getSubject(),
                request.getDescription(),
                request.getAmountMinor(),
                currency,
                request.getReturnUrl(),
                request.getNotifyUrl(),
                request.getExpiresAt() == null ? 0L : request.getExpiresAt().toEpochMilli(),
                request.getMetadata()
        );
        try {
            CompletableFuture<PacketPluginCreatePayment.Response> future = api.sendFuture(packet);
            PacketPluginCreatePayment.Response response = future.get(apiTimeoutSeconds(), TimeUnit.SECONDS);
            if (response.getError() != null && !response.getError().isEmpty()) {
                return createError(merchantOrderId, mapBackendError(response.getError()), response.getError());
            }
            OrderRecord record = new OrderRecord();
            record.merchantOrderId = merchantOrderId;
            record.providerOrderId = response.getProviderOrderId();
            record.status = PaymentStatus.PAYING;
            record.method = methodFromCode(response.getMethod());
            record.methodCode = response.getMethod();
            record.backendMethodCode = response.getMethodCode();
            record.amountMinor = response.getActualAmountMinor() > 0L ? response.getActualAmountMinor() : request.getAmountMinor();
            record.currency = normalizeCurrency(response.getCurrency());
            if (record.currency == null) record.currency = currency;
            record.payUrl = response.getPaymentUrl();
            record.qrCodeUrl = response.getQrCodeUrl();
            record.expiresAt = response.getExpiresAt() > 0L ? Instant.ofEpochMilli(response.getExpiresAt()) : request.getExpiresAt();
            record.extra.putAll(response.getExtra());
            putOrder(record);
            saveOrders();
            return toCreateResult(record);
        } catch (Exception e) {
            return createError(merchantOrderId, e instanceof java.util.concurrent.TimeoutException ? "UPSTREAM_TIMEOUT" : "PROVIDER_UNAVAILABLE", e.getMessage());
        }
    }

    @Override
    public PaymentQueryResult queryPayment(PaymentQueryRequest request) {
        if (request == null) {
            return queryError(null, null, "INVALID_REQUEST", "request is null");
        }
        OrderRecord record = findOrder(request.getMerchantOrderId(), request.getProviderOrderId());
        if (record != null && isClosed(record.status)) {
            return toQueryResult(record);
        }
        if (record != null && PaymentAPI.inst().isConnected()) {
            try {
                PacketPluginQueryPayment packet = new PacketPluginQueryPayment(
                        record.merchantOrderId,
                        record.providerOrderId,
                        methodCode(record.method),
                        record.methodCode
                );
                PacketPluginQueryPayment.Response response = PaymentAPI.inst().sendFuture(packet).get(apiTimeoutSeconds(), TimeUnit.SECONDS);
                if (response.getError() == null || response.getError().isEmpty()) {
                    record.status = statusFromString(response.getStatus());
                    if (response.getAmountMinor() > 0L) record.amountMinor = response.getAmountMinor();
                    if (trimToNull(response.getCurrency()) != null) record.currency = response.getCurrency();
                    if (response.getExpiresAt() > 0L) record.expiresAt = Instant.ofEpochMilli(response.getExpiresAt());
                    if (response.getPaidAt() > 0L) record.paidAt = Instant.ofEpochMilli(response.getPaidAt());
                    record.extra.putAll(response.getExtra());
                    putOrder(record);
                    saveOrders();
                }
            } catch (Exception ignored) {
            }
        }
        if (record != null) {
            return toQueryResult(record);
        }
        return queryError(request.getMerchantOrderId(), request.getProviderOrderId(), "ORDER_NOT_FOUND", "payment order not found");
    }

    @Override
    public void registerListener(String consumerId, PaymentListener listener) {
        String id = trimToNull(consumerId);
        if (id == null || listener == null) return;
        listeners.put(id, listener);
        retryUndeliveredNotifications();
    }

    @Override
    public void unregisterListener(String consumerId) {
        String id = trimToNull(consumerId);
        if (id != null) listeners.remove(id);
    }

    @Override
    public void handleBackendPaymentEvent(PacketBackendPaymentEvent event) {
        if (event == null) return;
        OrderRecord record = findOrder(event.getMerchantOrderId(), event.getProviderOrderId());
        if (record == null) {
            record = new OrderRecord();
            record.merchantOrderId = event.getMerchantOrderId();
            record.providerOrderId = event.getProviderOrderId();
        }
        PaymentStatus newStatus = statusFromString(event.getStatus());
        if (record.status == newStatus && record.notified) return;
        record.status = newStatus;
        record.amountMinor = event.getAmountMinor() > 0L ? event.getAmountMinor() : record.amountMinor;
        record.currency = normalizeCurrency(event.getCurrency()) == null ? record.currency : normalizeCurrency(event.getCurrency());
        record.method = methodFromCode(event.getMethod());
        record.methodCode = event.getMethod();
        if (event.getPaidAt() > 0L) record.paidAt = Instant.ofEpochMilli(event.getPaidAt());
        record.extra.putAll(event.getExtra());
        putOrder(record);
        saveOrders();
        dispatchNotify(record, event.getRawEventId());
    }

    @Override
    public void retryUndeliveredNotifications() {
        if (listeners.isEmpty()) return;
        for (OrderRecord record : byMerchantOrderId.values()) {
            if (record.status == PaymentStatus.SUCCESS && !record.notified) {
                dispatchNotify(record, null);
            }
        }
    }

    @Override
    public void close() {
        saveOrders();
        listeners.clear();
    }

    private void dispatchNotify(OrderRecord record, String rawEventId) {
        if (listeners.isEmpty() || record.merchantOrderId == null) {
            record.notified = false;
            putOrder(record);
            saveOrders();
            return;
        }
        boolean delivered = true;
        PaymentNotify notify = new PaymentNotify();
        notify.setMerchantOrderId(record.merchantOrderId);
        notify.setProviderOrderId(record.providerOrderId);
        notify.setStatus(record.status);
        notify.setAmountMinor(record.amountMinor);
        notify.setCurrency(record.currency);
        notify.setMethod(record.method);
        notify.setMethodCode(record.methodCode);
        notify.setPaidAt(record.paidAt);
        notify.setRawEventId(rawEventId == null ? record.providerOrderId + ":" + record.status.name() : rawEventId);
        notify.setExtra(record.extra);
        for (Map.Entry<String, PaymentListener> entry : listeners.entrySet()) {
            try {
                PaymentNotifyResult result = entry.getValue().onPaymentNotify(notify);
                if (result == null || !result.isSuccess()) {
                    delivered = false;
                    record.extra.put("listener." + entry.getKey(), result == null ? "NULL_RESULT" : result.getCode());
                }
            } catch (Throwable t) {
                delivered = false;
                record.extra.put("listener." + entry.getKey(), t.getClass().getSimpleName());
            }
        }
        record.notified = delivered;
        putOrder(record);
        saveOrders();
    }

    private boolean isMethodEnabled(String method) {
        String lower = method.toLowerCase(Locale.ROOT);
        if (plugin.getConfig().contains("methods." + lower + ".enabled")) {
            return plugin.getConfig().getBoolean("methods." + lower + ".enabled");
        }
        return plugin.getConfig().getBoolean("payment.enable." + lower);
    }

    private String resolveMethodCode(PaymentMethod method, String methodCode) {
        if (method == null || method == PaymentMethod.AUTO) {
            String configured = trimToNull(plugin.getConfig().getString("payment.default-method"));
            if (configured != null) {
                String lower = configured.toLowerCase(Locale.ROOT);
                if (isMethodEnabled(lower)) return lower;
            }
            if (isMethodEnabled("alipay")) return "alipay";
            if (isMethodEnabled("wechat")) return "wechat";
            if (isMethodEnabled("paypal")) return "paypal";
            if (isMethodEnabled("mercadopago")) return "mercadopago";
            return null;
        }
        if (method == PaymentMethod.CUSTOM) {
            String code = trimToNull(methodCode);
            return code != null && isMethodEnabled(code) ? code.toLowerCase(Locale.ROOT) : null;
        }
        String code = methodCode(method);
        return code != null && isMethodEnabled(code) ? code : null;
    }

    private int apiTimeoutSeconds() {
        return Math.max(1, plugin.getConfig().getInt("payment.api-timeout-seconds", 15));
    }

    private void putOrder(OrderRecord record) {
        if (record.merchantOrderId != null) byMerchantOrderId.put(record.merchantOrderId, record);
        if (record.providerOrderId != null) byProviderOrderId.put(record.providerOrderId, record);
    }

    private OrderRecord findOrder(String merchantOrderId, String providerOrderId) {
        String merchant = trimToNull(merchantOrderId);
        if (merchant != null) {
            OrderRecord record = byMerchantOrderId.get(merchant);
            if (record != null) return record;
        }
        String provider = trimToNull(providerOrderId);
        return provider == null ? null : byProviderOrderId.get(provider);
    }

    private void loadOrders() {
        if (!ordersFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(ordersFile);
        ConfigurationSection section = yaml.getConfigurationSection("orders");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection orderSection = section.getConfigurationSection(key);
            if (orderSection == null) continue;
            OrderRecord record = OrderRecord.load(orderSection);
            putOrder(record);
        }
    }

    private synchronized void saveOrders() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("orders");
        for (OrderRecord record : byMerchantOrderId.values()) {
            String key = Base64.getUrlEncoder().withoutPadding().encodeToString(record.merchantOrderId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            record.save(section.createSection(key));
        }
        try {
            yaml.save(ordersFile);
        } catch (IOException e) {
            plugin.warn("保存 WSXPay 订单索引失败", e);
        }
    }

    private PaymentCreateResult toCreateResult(OrderRecord record) {
        PaymentCreateResult result = new PaymentCreateResult();
        result.setSuccess(true);
        result.setMerchantOrderId(record.merchantOrderId);
        result.setProviderOrderId(record.providerOrderId);
        result.setStatus(record.status);
        result.setMethod(record.method);
        result.setMethodCode(record.methodCode);
        result.setPayUrl(record.payUrl);
        result.setQrCodeUrl(record.qrCodeUrl);
        result.setExpiresAt(record.expiresAt);
        result.setExtra(record.extra);
        return result;
    }

    private PaymentQueryResult toQueryResult(OrderRecord record) {
        PaymentQueryResult result = new PaymentQueryResult();
        result.setSuccess(true);
        result.setMerchantOrderId(record.merchantOrderId);
        result.setProviderOrderId(record.providerOrderId);
        result.setStatus(record.status);
        result.setMethod(record.method);
        result.setMethodCode(record.methodCode);
        result.setPayUrl(record.payUrl);
        result.setQrCodeUrl(record.qrCodeUrl);
        result.setExpiresAt(record.expiresAt);
        result.setPaid(record.status == PaymentStatus.SUCCESS);
        result.setAmountMinor(record.amountMinor);
        result.setCurrency(record.currency);
        result.setPaidAt(record.paidAt);
        result.setExtra(record.extra);
        return result;
    }

    private PaymentCreateResult createError(String merchantOrderId, String code, String message) {
        PaymentCreateResult result = new PaymentCreateResult();
        result.setSuccess(false);
        result.setMerchantOrderId(merchantOrderId);
        result.setErrorCode(code);
        result.setMessage(message);
        return result;
    }

    private PaymentQueryResult queryError(String merchantOrderId, String providerOrderId, String code, String message) {
        PaymentQueryResult result = new PaymentQueryResult();
        result.setSuccess(false);
        result.setMerchantOrderId(merchantOrderId);
        result.setProviderOrderId(providerOrderId);
        result.setErrorCode(code);
        result.setMessage(message);
        return result;
    }

    private static String methodCode(PaymentMethod method) {
        if (method == PaymentMethod.ALIPAY) return "alipay";
        if (method == PaymentMethod.WECHAT) return "wechat";
        if (method == PaymentMethod.PAYPAL) return "paypal";
        return null;
    }

    private static PaymentMethod methodFromCode(String method) {
        String lower = trimToNull(method);
        if (lower == null) return PaymentMethod.AUTO;
        lower = lower.toLowerCase(Locale.ROOT);
        if (lower.equals("alipay")) return PaymentMethod.ALIPAY;
        if (lower.equals("wechat")) return PaymentMethod.WECHAT;
        if (lower.equals("paypal")) return PaymentMethod.PAYPAL;
        return PaymentMethod.CUSTOM;
    }

    private static PaymentStatus statusFromString(String status) {
        if (status == null) return PaymentStatus.UNKNOWN;
        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return PaymentStatus.UNKNOWN;
        }
    }

    private static PaymentStatus statusFromReason(String reason) {
        String lower = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (lower.contains("timeout") || lower.contains("expired")) return PaymentStatus.EXPIRED;
        if (lower.contains("cancel") || lower.contains("closed") || lower.contains("voided")) return PaymentStatus.CANCELLED;
        if (lower.contains("failed") || lower.contains("error")) return PaymentStatus.FAILED;
        return PaymentStatus.UNKNOWN;
    }

    private static boolean isClosed(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED
                || status == PaymentStatus.EXPIRED || status == PaymentStatus.CANCELLED
                || status == PaymentStatus.REFUNDED;
    }

    private static long priceToAmountMinor(String price) {
        try {
            return new BigDecimal(price).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String normalizeCurrency(String currency) {
        String value = trimToNull(currency);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String mapBackendError(String error) {
        String lower = error == null ? "" : error.toLowerCase(Locale.ROOT);
        if (lower.contains("not-a-number") || lower.contains("invalid-amount")) return "INVALID_AMOUNT";
        if (lower.contains("type-unknown")) return "METHOD_UNSUPPORTED";
        if (lower.contains("not-found")) return "ORDER_NOT_FOUND";
        if (lower.contains("timeout")) return "UPSTREAM_TIMEOUT";
        if (lower.contains("config")) return "CONFIG_MISSING";
        return "UPSTREAM_ERROR";
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trim = value.trim();
        return trim.isEmpty() ? null : trim;
    }

    private static final class OrderRecord {
        String merchantOrderId;
        String providerOrderId;
        PaymentStatus status = PaymentStatus.UNKNOWN;
        PaymentMethod method = PaymentMethod.AUTO;
        String methodCode;
        String backendMethodCode;
        long amountMinor;
        String currency = "CNY";
        String payUrl;
        String qrCodeUrl;
        Instant expiresAt;
        Instant paidAt;
        boolean notified;
        Map<String, String> extra = new LinkedHashMap<>();

        static OrderRecord load(ConfigurationSection section) {
            OrderRecord record = new OrderRecord();
            record.merchantOrderId = section.getString("merchant-order-id");
            record.providerOrderId = section.getString("provider-order-id");
            record.status = statusFromString(section.getString("status"));
            record.method = methodFromCode(section.getString("method"));
            record.methodCode = section.getString("method");
            record.backendMethodCode = section.getString("backend-method-code");
            record.amountMinor = section.getLong("amount-minor");
            record.currency = section.getString("currency", "CNY");
            record.payUrl = section.getString("pay-url");
            record.qrCodeUrl = section.getString("qr-code-url");
            record.expiresAt = instant(section.getLong("expires-at"));
            record.paidAt = instant(section.getLong("paid-at"));
            record.notified = section.getBoolean("notified");
            ConfigurationSection extraSection = section.getConfigurationSection("extra");
            if (extraSection != null) for (String key : extraSection.getKeys(false)) {
                record.extra.put(key, String.valueOf(extraSection.get(key)));
            }
            return record;
        }

        void save(ConfigurationSection section) {
            section.set("merchant-order-id", merchantOrderId);
            section.set("provider-order-id", providerOrderId);
            section.set("status", status.name());
            section.set("method", methodCode);
            section.set("backend-method-code", backendMethodCode);
            section.set("amount-minor", amountMinor);
            section.set("currency", currency);
            section.set("pay-url", payUrl);
            section.set("qr-code-url", qrCodeUrl);
            section.set("expires-at", expiresAt == null ? 0L : expiresAt.toEpochMilli());
            section.set("paid-at", paidAt == null ? 0L : paidAt.toEpochMilli());
            section.set("notified", notified);
            ConfigurationSection extraSection = section.createSection("extra");
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                extraSection.set(entry.getKey(), entry.getValue());
            }
        }

        private static Instant instant(long epochMillis) {
            return epochMillis <= 0L ? null : Instant.ofEpochMilli(epochMillis);
        }
    }
}
