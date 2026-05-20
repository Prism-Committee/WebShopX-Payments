package com.webshopx.payments.backend.payment;

import com.wechat.pay.api.CloseOrder;
import com.wechat.pay.api.NativePrepay;
import com.wechat.pay.api.QueryByOutTradeNo;
import com.wechat.pay.utils.WXPayUtility;
import com.webshopx.payments.backend.AbstractPaymentServer;
import com.webshopx.payments.backend.Configuration;
import com.webshopx.payments.backend.PaymentOrderRequest;
import com.webshopx.payments.backend.PaymentOrderResponse;
import com.webshopx.payments.backend.data.ClientInfo;
import com.webshopx.payments.backend.util.ProxySupport;
import com.webshopx.payments.backend.util.Util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

public class PaymentWeChat<C extends ClientInfo<C>> {
    AbstractPaymentServer<C> server;
    public Map<String, ClientInfo.Order<C>> moneyLocked = new HashMap<>();

    public PaymentWeChat(AbstractPaymentServer<C> server) {
        this.server = server;
    }

    public PaymentOrderResponse handleHook(PaymentOrderRequest request, C client, Configuration config) {
        Configuration.WeChatHook hook = config.getHook().getWeChat();
        String requireProcess = hook.getRequireProcess();
        if (!requireProcess.isEmpty()) {
            String target = File.separator + requireProcess;
            boolean notRunning = true;
            for (String command : server.getAllProcess()) {
                if (command.endsWith(target)) {
                    notRunning = false;
                    break;
                }
            }
            if (notRunning) {
                return new PaymentOrderResponse("payment.hook-not-running");
            }
        }
        String money;
        if (moneyLocked.containsKey(request.getPrice())) {
            if (!request.isAllowIncreasing()) {
                return new PaymentOrderResponse("payment.hook-price-locked");
            }
            double moneyNum = Double.parseDouble(request.getPrice());
            do {
                moneyNum += 0.01;
            } while (moneyLocked.containsKey(String.format("%.2f", moneyNum)));
            money = String.format("%.2f", moneyNum);
        } else {
            money = request.getPrice();
        }
        String orderId = client.nextOrderId();
        String paymentUrl = hook.getPaymentUrl(money);
        ClientInfo.Order<C> order = client.createOrder(orderId, "wechat", request.getPlayerName(), money);
        moneyLocked.put(money, order);
        server.getLogger().info("WeChat Hook order created: merchantOrderId={}, amount={}", orderId, money);
        return new PaymentOrderResponse("hook", orderId, order.getMoney(), paymentUrl);
    }

    public PaymentOrderResponse handleNative(PaymentOrderRequest request, C client, Configuration config) {
        Long priceWeChat = Util.parseLong(request.getPrice().replace(".", "")).orElse(null);
        if (priceWeChat == null) {
            return new PaymentOrderResponse("payment.not-a-number");
        }
        String orderId = client.nextOrderId();
        if (orderId == null) {
            return new PaymentOrderResponse("payment.can-not-create-id");
        }
        NativePrepay service = new NativePrepay(config.getWeChatNative().getConfig());

        NativePrepay.CommonPrepayRequest prepayRequest = new NativePrepay.CommonPrepayRequest();
        prepayRequest.description = request.getSubject();
        prepayRequest.outTradeNo = orderId;
        prepayRequest.notifyUrl = config.getWeChatNative().getNotifyUrl();
        prepayRequest.amount = new NativePrepay.CommonAmountInfo();
        prepayRequest.amount.total = priceWeChat;
        prepayRequest.amount.currency = "CNY";

        NativePrepay.Response response;
        try {
            response = ProxySupport.call(config.resolveProxy(config.getWeChatNative().getProxy()), server.getLogger(), () -> service.run(prepayRequest));
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] WeChat Native prepay response: {}", WXPayUtility.toJson(response));
            }
        } catch (Exception e) {
            client.removeOrder(orderId);
            server.getLogger().warn("WeChat Native prepay failed: merchantOrderId={}", orderId, e);
            return new PaymentOrderResponse("payment.internal-error");
        }

        ClientInfo.Order<C> order = client.createOrder(orderId, "wechat", request.getPlayerName(), request.getPrice());
        order.setCancelAction(() -> cancelWeChatNative(orderId));
        order.setTask(new TimerTask() {
            @Override
            public void run() {
                checkWeChatNative(client, this, orderId);
            }
        });
        server.getTimer().schedule(order.getTask(), 1000L, 3000L);
        server.getLogger().info("WeChat Native order created: merchantOrderId={}", orderId);
        return new PaymentOrderResponse("native", orderId, order.getMoney(), response.getCodeUrl());
    }

    private void checkWeChatNative(C client, TimerTask task, String orderId) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) {
            task.cancel();
            if (order != null) {
                order.setTask(null);
                client.removeOrder(order);
            }
            cancelWeChatNative(orderId);
            return;
        }
        QueryByOutTradeNo service = new QueryByOutTradeNo(config.getWeChatNative().getConfig());

        QueryByOutTradeNo.QueryByOutTradeNoRequest request = new QueryByOutTradeNo.QueryByOutTradeNoRequest();
        request.outTradeNo = orderId;

        QueryByOutTradeNo.Response response;
        try {
            response = ProxySupport.call(config.resolveProxy(config.getWeChatNative().getProxy()), server.getLogger(), () -> service.run(request));
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] WeChat Native query response: {}", WXPayUtility.toJson(response));
            }
        } catch (Exception e) {
            server.getLogger().warn("WeChat Native query failed: merchantOrderId={}", orderId, e);
            return;
        }
        switch (response.tradeState) {
            case "SUCCESS":
                client.removeOrder(order);
                String openId = response.payer.openid;
                String money;
                if (response.amount.payerTotal != null) {
                    money = String.format("%.2f", response.amount.payerTotal / 100.0);
                } else {
                    money = order.getMoney();
                }
                server.getLogger().info("WeChat Native payment completed: payerOpenId={}, amount=CNY {}", openId, money);
                server.sendPaymentSuccess(client, order, money);
                break;
            case "REFUND":
            case "CLOSED":
                client.removeOrder(order);
                server.getLogger().info("WeChat Native order ended: merchantOrderId={}, state={}", orderId, response.tradeState);
                server.sendPaymentCancel(client, order, "payment.native." + response.tradeState.toLowerCase());
                break;
            case "NOTPAY":
            case "REVOKED":
            case "USERPAYING":
            case "PAYERROR":
            default:
                break;
        }
    }

    private void cancelWeChatNative(String orderId) {
        Configuration config = server.getConfig();

        CloseOrder service = new CloseOrder(config.getWeChatNative().getConfig());

        CloseOrder.OrderRequest request = new CloseOrder.OrderRequest();
        request.outTradeNo = orderId;

        try {
            ProxySupport.run(config.resolveProxy(config.getWeChatNative().getProxy()), server.getLogger(), () -> service.run(request));
        } catch (Exception e) {
            server.getLogger().warn("WeChat Native close order failed: merchantOrderId={}", orderId, e);
        }
    }
}
