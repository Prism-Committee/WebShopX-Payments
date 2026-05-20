package com.webshopx.payments.backend.payment;

import com.google.gson.JsonParser;
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
        return new PaymentOrderResponse("hook", orderId, order.getMoney(), paymentUrl);
    }

    public PaymentOrderResponse handleNative(PaymentOrderRequest request, C client, Configuration config) {
        // 微信支付的订单总金额单位为「分」，保留两位小数的结果去掉小数点，再转整数完事
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

        // 调用下单方法，得到应答
        NativePrepay.Response response;
        try {
            response = ProxySupport.call(config.resolveProxy(config.getWeChatNative().getProxy()), server.getLogger(), () -> service.run(prepayRequest));
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] 微信 Native支付 下单结果: {}", WXPayUtility.toJson(response));
            }
        } catch (Exception e) {
            client.removeOrder(orderId);
            server.getLogger().warn("微信 Native支付 API执行错误", e);
            return new PaymentOrderResponse("payment.internal-error");
        }

        ClientInfo.Order<C> order = client.createOrder(orderId, "wechat", request.getPlayerName(), request.getPrice());
        order.setCancelAction(() -> cancelWeChatNative(orderId));
        // 轮询检查是否交易成功
        order.setTask(new TimerTask() {
            @Override
            public void run() {
                checkWeChatNative(client, this, orderId);
            }
        });
        // 每3秒检查一次是否支付成功
        server.getTimer().schedule(order.getTask(), 1000L, 3000L);
        return new PaymentOrderResponse("native", orderId, order.getMoney(), response.getCodeUrl());
    }

    private void checkWeChatNative(C client, TimerTask task, String orderId) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) { // 插件连接断开时、任务不存在时取消任务，并关闭交易
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
                server.getLogger().info("[DEBUG] 微信 Native支付 检查结果: {}", WXPayUtility.toJson(response));
            }
        } catch (Exception e) {
            server.getLogger().warn("微信 Native支付 API检查订单时执行错误", e);
            return;
        }
        switch (response.tradeState) {
            case "SUCCESS": // 支付成功
                client.removeOrder(order);
                String openId = response.payer.openid;
                String money;
                if (response.amount.payerTotal != null) {
                    money = String.format("%.2f", response.amount.payerTotal / 100.0);
                } else {
                    money = order.getMoney();
                }
                server.getLogger().info("[收款] 从微信Native收款，来自 {} 的 ￥{}", openId, money);
                server.sendPaymentSuccess(client, order, money);
                break;
            case "REFUND": // 转入退款
            case "CLOSED": // 已关闭
                client.removeOrder(order);
                server.sendPaymentCancel(client, order, "payment.native." + response.tradeState.toLowerCase());
                break;
            case "NOTPAY": // 未支付，忽略
                break;
            case "REVOKED": // 已撤销 (仅付款码，忽略)
            case "USERPAYING": // 用户支付中 (仅付款码，忽略)
            case "PAYERROR": // 支付失败 (仅付款码，忽略)
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
            server.getLogger().warn("微信 Native支付 API关闭交易时执行错误", e);
        }
    }
}
