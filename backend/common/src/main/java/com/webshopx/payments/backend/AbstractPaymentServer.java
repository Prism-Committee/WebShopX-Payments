package com.webshopx.payments.backend;

import com.google.gson.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.webshopx.payments.backend.data.ClientInfo;
import com.webshopx.payments.backend.data.HookReceive;
import com.webshopx.payments.backend.payment.PaymentAlipay;
import com.webshopx.payments.backend.payment.PaymentMercadoPago;
import com.webshopx.payments.backend.payment.PaymentPaypal;
import com.webshopx.payments.backend.payment.PaymentWeChat;
import com.webshopx.payments.backend.util.Util;
import com.webshopx.payments.packets.PacketSerializer;
import com.webshopx.payments.packets.backend.PacketBackendPaymentEvent;
import com.webshopx.payments.packets.common.IPacket;
import com.webshopx.payments.packets.plugin.PacketPluginCancelOrder;
import com.webshopx.payments.packets.plugin.PacketPluginCreatePayment;
import com.webshopx.payments.packets.plugin.PacketPluginQueryPayment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * 后端 WebSocket/Http 路由
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class AbstractPaymentServer<C extends ClientInfo<C>> {
    Gson gson = new GsonBuilder().setLenient().create();
    Logger logger;
    Timer timer = new Timer();
    Map<String, List<BiFunction>> executors = new HashMap<>();
    PaymentWeChat wechat = new PaymentWeChat(this);
    PaymentAlipay alipay = new PaymentAlipay(this);
    PaymentPaypal paypal = new PaymentPaypal(this);
    PaymentMercadoPago mercadoPago = new PaymentMercadoPago(this);

    public AbstractPaymentServer(Logger logger) {
        this.logger = logger;
        this.registerExecutor(PacketPluginCreatePayment.class, this::handleCreatePayment);
        this.registerExecutor(PacketPluginQueryPayment.class, this::handleQueryPayment);
        this.registerExecutor(PacketPluginCancelOrder.class, this::handleCancel);
    }

    public Logger getLogger() {
        return logger;
    }

    public Timer getTimer() {
        return timer;
    }

    /**
     * 注册接收包处理器，无返回值
     *
     * @param type     包类型
     * @param executor 处理器
     * @param <T>      包类型
     */
    public <T extends IPacket> void registerExecutor(Class<T> type, BiConsumer<T, C> executor) {
        registerExecutor(type, (packet, client) -> {
            executor.accept(packet, client);
            return null;
        });
    }

    /**
     * 注册接收包处理器，有返回值
     *
     * @param type     包类型
     * @param executor 处理器
     * @param <S>      返回类型
     * @param <T>      包类型
     */
    public <S extends IPacket, T extends IPacket<S>> void registerExecutor(Class<T> type, BiFunction<T, C, S> executor) {
        String key = type.getName();
        List<BiFunction> list = executors.get(key);
        if (list == null) list = new ArrayList<>();
        list.add(executor);
        executors.put(key, list);
    }

    @Nullable
    public Map<String, ClientInfo.Order<C>> getMoneyLockedMap(String type) {
        switch (type.toLowerCase()) {
            case "alipay":
                return alipay.moneyLocked;
            case "wechat":
                return wechat.moneyLocked;
            default:
                return null;
        }
    }

    private PaymentOrderResponse handleOrderRequest(PaymentOrderRequest request, ClientInfo client) {
        // 验证 price 是否符合格式，在可修正时自动修正格式
        Double priceDouble = Util.parseDouble(request.getPrice()).orElse(null);
        if (priceDouble == null) {
            return new PaymentOrderResponse("payment.not-a-number");
        }
        request.setPrice(String.format("%.2f", priceDouble));

        Configuration config = getConfig();
        String currency = normalizeCurrency(request.getCurrency());
        if (currency == null) {
            return new PaymentOrderResponse("payment.invalid-currency");
        }
        request.setCurrency(currency);
        if (!isCurrencySupported(request.getMethod(), currency)) {
            return new PaymentOrderResponse("payment.currency-unsupported");
        }

        // 防止多次请求订单
        if (client.getOrderByPlayer(request.getPlayerName()) != null) {
            return new PaymentOrderResponse("payment.already-requested");
        }

        if (request.getMethod().equals("wechat")) {
            // 微信 Hook
            if (config.getHook().isEnable() && config.getHook().getWeChat().isEnable()) {
                return wechat.handleHook(request, client, config);
            }
            // 微信 Native
            if (config.getWeChatNative().isEnable()) {
                return wechat.handleNative(request, client, config);
            }
        }
        if (request.getMethod().equals("alipay")) {
            // 支付宝 Hook
            if (config.getHook().isEnable() && config.getHook().getAlipay().isEnable()) {
                return alipay.handleHook(request, client, config);
            }
            // 支付宝当面付
            if (config.getAlipayFaceToFace().isEnable()) {
                if (config.getAlipayFaceToFace().isUseBasicPollingMode()) {
                    return alipay.handlePolling(request, client, config);
                } else {
                    return alipay.handleFaceToFace(request, client, config);
                }
            }
        }
        if (request.getMethod().equals("paypal")) {
            // Paypal 官方接口
            if (config.getPaypal().isEnable()) {
                return paypal.handleCreateOrder(request, client, config);
            }
        }
        if (request.getMethod().equals("mercadopago")) {
            if (config.getMercadoPago().isEnable()) {
                return mercadoPago.handleCreateOrder(request, client, config);
            }
        }
        return new PaymentOrderResponse("payment.type-unknown");
    }

    private PacketPluginCreatePayment.Response handleCreatePayment(PacketPluginCreatePayment packet, ClientInfo client) {
        if (packet.getMerchantOrderId() == null || packet.getMerchantOrderId().trim().isEmpty()) {
            return new PacketPluginCreatePayment.Response("payment.invalid-request");
        }
        if (packet.getAmountMinor() <= 0L) {
            return new PacketPluginCreatePayment.Response("payment.invalid-amount");
        }
        String method = normalizeMethod(packet.getMethod());
        if (method == null) {
            return new PacketPluginCreatePayment.Response("payment.type-unknown");
        }
        String currency = normalizeCurrency(packet.getCurrency());
        if (currency == null) {
            return new PacketPluginCreatePayment.Response("payment.invalid-currency");
        }
        if (!isCurrencySupported(method, currency)) {
            return new PacketPluginCreatePayment.Response("payment.currency-unsupported");
        }
        String playerName = "wsxpay:" + packet.getMerchantOrderId();
        PaymentOrderRequest request = new PaymentOrderRequest(
                playerName,
                method,
                packet.getSubject() == null || packet.getSubject().trim().isEmpty() ? packet.getMerchantOrderId() : packet.getSubject(),
                amountMinorToPrice(packet.getAmountMinor()),
                false,
                packet.getDescription(),
                currency,
                packet.getReturnUrl(),
                packet.getNotifyUrl(),
                packet.getExpiresAt()
        );
        PaymentOrderResponse response = handleOrderRequest(request, client);
        if (response.getError() != null && !response.getError().isEmpty()) {
            return new PacketPluginCreatePayment.Response(response.getError());
        }
        long actualAmountMinor = priceToAmountMinor(response.getMoney());
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("backendSubType", response.getSubType());
        String paymentUrl = response.getPaymentUrl();
        String qrCodeUrl = ("paypal".equals(method) || "mercadopago".equals(method)) ? null : paymentUrl;
        if ("paypal".equals(method) || "mercadopago".equals(method)) {
            qrCodeUrl = null;
        }
        return new PacketPluginCreatePayment.Response(
                packet.getMerchantOrderId(),
                response.getOrderId(),
                paymentUrl,
                qrCodeUrl,
                actualAmountMinor,
                currency,
                method,
                response.getSubType(),
                packet.getExpiresAt(),
                extra
        );
    }

    private PacketPluginQueryPayment.Response handleQueryPayment(PacketPluginQueryPayment packet, ClientInfo client) {
        ClientInfo.Order order = null;
        if (packet.getProviderOrderId() != null && !packet.getProviderOrderId().trim().isEmpty()) {
            order = client.getOrder(packet.getProviderOrderId());
        }
        if (order == null && packet.getMerchantOrderId() != null && !packet.getMerchantOrderId().trim().isEmpty()) {
            order = client.getOrderByPlayer("wsxpay:" + packet.getMerchantOrderId());
        }
        String method = normalizeMethod(packet.getMethodCode() == null ? packet.getMethod() : packet.getMethodCode());
        if (method == null && order != null) {
            method = normalizeMethod(order.getType());
        }
        if ("paypal".equals(method) && getConfig().getPaypal().isEnable()) {
            PacketPluginQueryPayment.Response response = paypal.handleQueryPayment(packet, getConfig());
            if (response.getError() == null || response.getError().isEmpty() || order == null) {
                return response;
            }
        }
        if ("mercadopago".equals(method) && getConfig().getMercadoPago().isEnable()) {
            PacketPluginQueryPayment.Response response = mercadoPago.handleQueryPayment(packet, getConfig());
            if (response.getError() == null || response.getError().isEmpty() || order == null) {
                return response;
            }
        }
        if (order == null) {
            return new PacketPluginQueryPayment.Response("payment.cancel.not-found");
        }
        String merchantOrderId = packet.getMerchantOrderId();
        if ((merchantOrderId == null || merchantOrderId.trim().isEmpty()) && order.getPlayerName().startsWith("wsxpay:")) {
            merchantOrderId = order.getPlayerName().substring("wsxpay:".length());
        }
        return new PacketPluginQueryPayment.Response(
                merchantOrderId,
                order.getId(),
                "PAYING",
                order.getType(),
                null,
                null,
                null,
                priceToAmountMinor(order.getMoney()),
                order.getCurrency(),
                0L,
                0L,
                Collections.emptyMap()
        );
    }

    private static String normalizeMethod(String method) {
        if (method == null) return null;
        String normalized = method.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("wechat") || normalized.equals("alipay") || normalized.equals("paypal") || normalized.equals("mercadopago")) {
            return normalized;
        }
        return null;
    }

    private static String amountMinorToPrice(long amountMinor) {
        return BigDecimal.valueOf(amountMinor, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null) return null;
        String trimmed = currency.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static long priceToAmountMinor(String price) {
        try {
            return new BigDecimal(price).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private PacketPluginCancelOrder.Response handleCancel(PacketPluginCancelOrder packet, ClientInfo client) {
        // 取消订单
        ClientInfo.Order order = client.removeOrder(packet.getOrderId());
        if (order != null) {
            Map<String, ClientInfo.Order<C>> moneyLocked = getMoneyLockedMap(order.getType());
            if (moneyLocked != null) {
                ClientInfo.Order locked = moneyLocked.get(order.getMoney());
                if (locked != null && locked.getId().equals(order.getId())) {
                    moneyLocked.remove(locked.getMoney());
                }
            }
            Runnable action = order.getCancelAction();
            if (action != null) {
                action.run();
            }
            return new PacketPluginCancelOrder.Response();
        }
        return new PacketPluginCancelOrder.Response("payment.cancel.not-found");
    }

    public abstract List<String> getAllProcess();

    public abstract Configuration getConfig();

    public void send(@NotNull C client, @NotNull IPacket packet) {
        send(client, packet, null);
    }

    public abstract void send(@NotNull C client, @NotNull IPacket packet, @Nullable Long echo);

    public void sendPaymentSuccess(@NotNull C client, @NotNull ClientInfo.Order<C> order, @NotNull String money) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("source", "backend");
        send(client, new PacketBackendPaymentEvent(
                null,
                order.getId(),
                "SUCCESS",
                priceToAmountMinor(money),
                order.getCurrency(),
                order.getType(),
                null,
                System.currentTimeMillis(),
                order.getId() + ":SUCCESS",
                extra
        ));
    }

    public void sendPaymentCancel(@NotNull C client, @NotNull ClientInfo.Order<C> order, @NotNull String reason) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("source", "backend");
        extra.put("reason", reason);
        send(client, new PacketBackendPaymentEvent(
                null,
                order.getId(),
                statusFromCancelReason(reason),
                priceToAmountMinor(order.getMoney()),
                order.getCurrency(),
                order.getType(),
                null,
                0L,
                order.getId() + ":" + reason,
                extra
        ));
    }

    private static String statusFromCancelReason(String reason) {
        String lower = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (lower.contains("timeout") || lower.contains("expired")) return "EXPIRED";
        if (lower.contains("cancel") || lower.contains("closed") || lower.contains("voided")) return "CANCELLED";
        if (lower.contains("failed") || lower.contains("error")) return "FAILED";
        return "UNKNOWN";
    }

    private String currencyForMethod(String method) {
        if ("wechat".equalsIgnoreCase(method)) {
            return getConfig().getWeChatNative().getCurrency();
        }
        if ("alipay".equalsIgnoreCase(method)) {
            return getConfig().getAlipayFaceToFace().getCurrency();
        }
        if ("paypal".equalsIgnoreCase(method)) {
            return getConfig().getPaypal().getCurrency();
        }
        if ("mercadopago".equalsIgnoreCase(method)) {
            return getConfig().getMercadoPago().getCurrency();
        }
        return "CNY";
    }

    private boolean isCurrencySupported(String method, String currency) {
        if (method == null || currency == null) return false;
        return currency.equalsIgnoreCase(currencyForMethod(method));
    }

    public void onMessage(C client, String s) {
        JsonObject json = JsonParser.parseString(s).getAsJsonObject();
        JsonElement echoProperty = json.get("echo");
        Long echo = echoProperty == null ? null : echoProperty.getAsLong();
        IPacket packet = PacketSerializer.deserialize(json);
        if (packet == null) {
            logger.warn("无法解析数据包 {}", s);
            return;
        }
        onMessage(client, packet, echo);
    }

    public void onMessage(C client, IPacket packet, Long echo) {
        Object result = null;
        List<BiFunction> list = executors.get(packet.getClass().getName());
        if (list != null && !list.isEmpty()) for (BiFunction executor : list) {
            Object obj = executor.apply(packet, client);
            if (result == null && obj != null) {
                result = obj;
            }
        }
        if (echo != null && result != null && packet.isResponsePacket(result)) {
            send(client, (IPacket) result, echo);
        }
    }

    private void onHookReceive(HookReceive receive) {
        // 处理接收 hook 收款消息
        Double moneyDouble = Util.parseDouble(receive.getMoney()).orElse(null);
        if (moneyDouble == null) {
            logger.warn("[收款] 收到Hook收款，处理金额时出现错误: 渠道[{}]，金额[{}]", receive.getType(), receive.getMoney());
            return;
        }
        String money = String.format("%.2f", moneyDouble);
        logger.info("[收款] 收到Hook收款，来自 {} 渠道的 ￥{}", receive.getType(), money);
        Map<String, ClientInfo.Order<C>> moneyLocked = getMoneyLockedMap(receive.getType());
        if (moneyLocked == null) {
            logger.warn("[Hook] 无效的渠道 {}", receive.getType());
            return;
        }
        ClientInfo.Order<C> order = moneyLocked.remove(money);
        if (order != null) {
            C client = order.getClient();
            if (!client.isOpen()) {
                logger.warn("[Hook] 玩家 {} 的 ￥{} 订单异常，在付款完成之前插件断开了与后端的连接", order.getPlayerName(), money);
                return;
            }
            logger.info("[Hook] 玩家 {} 的 ￥{} 订单已付款完成，回调订单结果", order.getPlayerName(), money);
            order.remove();
            sendPaymentSuccess(client, order, money);
        }
    }

    protected void receiveHook(CharSequence content) {
        String str = content.toString();
        try {
            HookReceive receive = gson.fromJson(str, HookReceive.class);
            onHookReceive(receive);
        } catch (Throwable t) {
            logger.warn("解析Hook消息时出现异常 `{}`", str, t);
        }
    }
}
