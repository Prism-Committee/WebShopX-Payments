package top.mrxiaom.sweet.checkout.backend.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.request.AlipayDataBillSellQueryRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayDataBillSellQueryResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.google.gson.JsonObject;
import top.mrxiaom.sweet.checkout.backend.AbstractPaymentServer;
import top.mrxiaom.sweet.checkout.backend.Configuration;
import top.mrxiaom.sweet.checkout.backend.data.ClientInfo;
import top.mrxiaom.sweet.checkout.packets.plugin.PacketPluginRequestOrder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PaymentAlipay<C extends ClientInfo<C>> {
    DateTimeFormatter POLLING_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    AbstractPaymentServer<C> server;
    public Map<String, ClientInfo.Order<C>> moneyLocked = new HashMap<>();

    public PaymentAlipay(AbstractPaymentServer<C> server) {
        this.server = server;
    }

    public PacketPluginRequestOrder.Response handleHook(PacketPluginRequestOrder packet, C client, Configuration config) {
        Configuration.AlipayHook hook = config.getHook().getAlipay();
        String money;
        if (moneyLocked.containsKey(packet.getPrice())) {
            if (!packet.isAllowIncreasing()) {
                return new PacketPluginRequestOrder.Response("payment.hook-price-locked");
            }
            double moneyNum = Double.parseDouble(packet.getPrice());
            do {
                moneyNum += 0.01;
            } while (moneyLocked.containsKey(String.format("%.2f", moneyNum)));
            money = String.format("%.2f", moneyNum);
        } else {
            money = packet.getPrice();
        }
        String orderId = client.nextOrderId();
        String paymentUrl = hook.getPaymentUrl(money);
        ClientInfo.Order<C> order = client.createOrder(orderId, "alipay", packet.getPlayerName(), money);
        moneyLocked.put(money, order);

        String startTime = LocalDateTime.now().format(POLLING_FORMAT);
        order.setCancelAction(() -> {/* 轮询模式关闭订单无需进行任何操作，丢弃这个二维码即可 */});
        // 轮询检查是否交易成功
        order.setTask(new TimerTask() {
            @Override
            public void run() {
                checkAlipayHook(client, this, orderId, money, startTime);
            }
        });
        // 每3秒检查一次是否支付成功
        server.getTimer().schedule(order.getTask(), 1000L, 3000L);
        server.getLogger().info("支付宝 Hook 下单成功: {} ￥{}", orderId, money);
        return new PacketPluginRequestOrder.Response("hook", orderId, order.getMoney(), paymentUrl);
    }

    public PacketPluginRequestOrder.Response handleFaceToFace(PacketPluginRequestOrder packet, C client, Configuration config) {
        String orderId = client.nextOrderId();
        if (orderId == null) {
            return new PacketPluginRequestOrder.Response("payment.can-not-create-id");
        }
        try {
            AlipayClient alipayClient = new DefaultAlipayClient(config.getAlipayFaceToFace().getConfig());

            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(orderId);
            model.setTotalAmount(packet.getPrice());
            model.setSubject(packet.getProductName());
            model.setProductCode(config.getAlipayFaceToFace().getProduceCode());
            model.setBody(packet.getProductName());

            request.setBizModel(model);

            AlipayTradePrecreateResponse response = alipayClient.execute(request);

            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] 支付宝 订单码支付 下单结果: {}", response);
            }
            if (response.isSuccess()) {
                ClientInfo.Order<C> order = client.createOrder(orderId, "alipay", packet.getPlayerName(), packet.getPrice());
                String outTradeNo = response.getOutTradeNo();
                order.setCancelAction(() -> cancelAlipayFaceToFace(outTradeNo));
                // 轮询检查是否交易成功
                order.setTask(new TimerTask() {
                    @Override
                    public void run() {
                        checkAlipayFaceToFace(client, this, orderId, outTradeNo);
                    }
                });
                // 每3秒检查一次是否支付成功
                server.getTimer().schedule(order.getTask(), 1000L, 3000L);
                server.getLogger().info("支付宝 订单码支付 下单成功 {} : {}", response.getMsg(), response.getOutTradeNo());
                return new PacketPluginRequestOrder.Response("face2face", orderId, order.getMoney(), response.getQrCode());
            } else {
                client.removeOrder(orderId);
                server.getLogger().warn("支付宝 订单码支付 调用失败 {}, {} {}", response.getMsg(), response.getSubCode(), response.getSubMsg());
                return new PacketPluginRequestOrder.Response("payment.internal-error");
            }
        } catch (AlipayApiException e) {
            client.removeOrder(orderId);
            server.getLogger().warn("支付宝 订单码支付 API执行错误", e);
            return new PacketPluginRequestOrder.Response("payment.internal-error");
        }
    }

    public PacketPluginRequestOrder.Response handlePolling(PacketPluginRequestOrder packet, C client, Configuration config) {
        String orderId = client.nextOrderId();
        if (orderId == null) {
            return new PacketPluginRequestOrder.Response("payment.can-not-create-id");
        }
        // 生成一个二维码，返回给插件扫码
        String uid = config.getAlipayFaceToFace().getSellerId(); // TODO: 使用支付宝接口获取开发者的 pid
        String randomId = UUID.randomUUID().toString().replace("-", "");
        String qrcodeURL = generateQRCode(uid, packet.getPrice(), randomId);
        String startTime = LocalDateTime.now().format(POLLING_FORMAT);
        ClientInfo.Order<C> order = client.createOrder(orderId, "alipay", packet.getPlayerName(), packet.getPrice());
        order.setCancelAction(() -> {/* 轮询模式关闭订单无需进行任何操作，丢弃这个二维码即可 */});
        // 轮询检查是否交易成功
        order.setTask(new TimerTask() {
            @Override
            public void run() {
                checkAlipayPolling(client, this, orderId, randomId, startTime);
            }
        });
        // 每3秒检查一次是否支付成功
        server.getTimer().schedule(order.getTask(), 1000L, 3000L);
        server.getLogger().info("支付宝 轮询模式 下单成功: {} ({})", orderId, randomId);
        return new PacketPluginRequestOrder.Response("polling", orderId, order.getMoney(), qrcodeURL);
    }

    public String generateQRCode(String uid, String price, String goodsMemo) {
        // TODO: 这里生成的是个人码，应该使用商家码才能被开发平台正常读取
        JsonObject json = new JsonObject();
        json.addProperty("s", "money");
        json.addProperty("u", uid);
        json.addProperty("a", price);
        json.addProperty("m", goodsMemo);
        return "alipays://platformapi/startapp?appId=20000123&actionType=scan&biz_data=" + json;
    }

    private void checkAlipayHook(C client, TimerTask task, String orderId, String price, String startTime) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) { // 插件连接断开时、任务不存在时取消任务，并关闭交易
            task.cancel();
            if (order != null) {
                order.setTask(null);
                client.removeOrder(order);
            }
            moneyLocked.remove(price);
            return;
        }
        try {
            // 支付宝商家账户买入交易查询
            AlipayClient alipayClient = new DefaultAlipayClient(config.getHook().getAlipay().getConfig());

            AlipayDataBillSellQueryRequest request = new AlipayDataBillSellQueryRequest();
            AlipayDataBillSellQueryModel model = new AlipayDataBillSellQueryModel();

            // 设置交易流水创建时间的起始范围
            model.setStartTime(startTime);
            // 设置交易流水创建时间的结束范围
            model.setEndTime(LocalDateTime.now().format(POLLING_FORMAT));

            request.setBizModel(model);
            AlipayDataBillSellQueryResponse response = alipayClient.execute(request);

            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] 支付宝 Hook 检查结果: {}", response);
            }
            if (response.isSuccess()) {
                TradeItemResult item = null;
                // 筛选成功的、备注相符的条目
                List<TradeItemResult> list = response.getDetailList();
                if (list != null) for (TradeItemResult ti : list) {
                    if (!ti.getTradeStatus().equals("成功")) continue;
                    if (!price.equals(ti.getTotalAmount())) continue;
                    item = ti;
                    break;
                }
                // 如果存在条目，检查结果
                if (item != null) {
                    String otherAccount = item.getOtherAccount();
                    String money = item.getTotalAmount();
                    client.removeOrder(order);
                    server.getLogger().info("[收款] 从支付宝 Hook 收款，来自 {} 的 ￥{}", otherAccount, money);
                    server.sendPaymentSuccess(client, order, money);
                    moneyLocked.remove(price);
                }
            } else {
                server.getLogger().warn("支付宝 Hook 检查订单失败 {}, {} {}，查询的订单号 {} ({})\n    {}", response.getMsg(), response.getSubCode(), response.getSubMsg(), orderId, price, response.getBody());
            }
        } catch (AlipayApiException e) {
            server.getLogger().warn("支付宝 Hook API检查订单时执行错误", e);
        }
    }

    private void checkAlipayPolling(C client, TimerTask task, String orderId, String randomId, String startTime) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) { // 插件连接断开时、任务不存在时取消任务，并关闭交易
            task.cancel();
            if (order != null) {
                order.setTask(null);
                client.removeOrder(order);
            }
            return;
        }
        try {
            // 支付宝商家账户买入交易查询
            AlipayClient alipayClient = new DefaultAlipayClient(config.getAlipayFaceToFace().getConfig());

            AlipayDataBillSellQueryRequest request = new AlipayDataBillSellQueryRequest();
            AlipayDataBillSellQueryModel model = new AlipayDataBillSellQueryModel();

            // 设置交易流水创建时间的起始范围
            model.setStartTime(startTime);
            // 设置交易流水创建时间的结束范围
            model.setEndTime(LocalDateTime.now().format(POLLING_FORMAT));

            request.setBizModel(model);
            AlipayDataBillSellQueryResponse response = alipayClient.execute(request);

            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] 支付宝 轮询模式 检查结果: {}", response);
            }
            if (response.isSuccess()) {
                TradeItemResult item = null;
                // 筛选成功的、备注相符的条目
                List<TradeItemResult> list = response.getDetailList();
                if (list != null) for (TradeItemResult ti : list) {
                    if (!ti.getTradeStatus().equals("成功")) continue;
                    if (!randomId.equals(ti.getGoodsMemo())) continue;
                    item = ti;
                    break;
                }
                // 如果存在条目，检查结果
                if (item != null) {
                    String otherAccount = item.getOtherAccount();
                    String money = item.getTotalAmount();
                    client.removeOrder(order);
                    if (order.getMoney().equals(money)) {
                        server.getLogger().info("[收款] 从支付宝 轮询模式 收款，来自 {} 的 ￥{}", otherAccount, money);
                        server.sendPaymentSuccess(client, order, money);
                    } else {
                        server.getLogger().warn("[收款] 从支付宝 轮询模式 收款，来自 {} 的 ￥{}，但支付的金额不正确，自动取消订单", otherAccount, money);
                        server.sendPaymentCancel(client, order, "payment.cancel.not-the-agreed-price");
                    }
                }
            } else {
                server.getLogger().warn("支付宝 轮询模式 检查订单失败 {}, {} {}，查询的订单号 {} ({})\n    {}", response.getMsg(), response.getSubCode(), response.getSubMsg(), orderId, randomId, response.getBody());
            }
        } catch (AlipayApiException e) {
            server.getLogger().warn("支付宝 轮询模式 API检查订单时执行错误", e);
        }
    }

    private void checkAlipayFaceToFace(C client, TimerTask task, String orderId, String outTradeNo) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) { // 插件连接断开时、任务不存在时取消任务，并关闭交易
            task.cancel();
            if (order != null) {
                order.setTask(null);
                client.removeOrder(order);
            }
            cancelAlipayFaceToFace(outTradeNo);
            return;
        }
        try {
            // 统一收款订单交易查询
            AlipayClient alipayClient = new DefaultAlipayClient(config.getAlipayFaceToFace().getConfig());

            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();

            model.setOutTradeNo(outTradeNo);
            List<String> queryOptions = new ArrayList<>();
            queryOptions.add("trade_settle_info");
            model.setQueryOptions(queryOptions);

            request.setBizModel(model);

            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] 支付宝 订单码支付 检查结果: {}", response);
            }
            if (response.isSuccess()) {
                String status = response.getTradeStatus();
                switch (status.toUpperCase()) {
                    case "WAIT_BUYER_PAY": // 等待买家付款
                        break;
                    case "TRADE_CLOSED": // 超时未付款，交易关闭
                        client.removeOrder(order);
                        server.sendPaymentCancel(client, order, "payment.timeout");
                        break;
                    case "TRADE_SUCCESS": // 交易支付成功
                    case "TRADE_FINISHED": {// 交易结束，不可退款
                        client.removeOrder(order);
                        // 买家支付宝账号，通常是打码的手机号
                        String buyerLogonId = response.getBuyerLogonId();
                        String money = response.getReceiptAmount();
                        server.getLogger().info("[收款] 从支付宝 订单码支付 收款，来自 {} 的 ￥{}", buyerLogonId, money);
                        server.sendPaymentSuccess(client, order, money);
                        break;
                    }
                }
            } else {
                if (response.getSubCode().equals("ACQ.TRADE_NOT_EXIST")) {
                    // 忽略交易不存在的情况
                    // precreate 之后，只要用户不扫码，交易就不存在
                    // 订单一预创建就开始轮询，不应该处理交易不存在的情况
                    return;
                }
                server.getLogger().warn("支付宝 订单码支付 检查订单失败 {}, {} {}，查询的订单号 {}\n    {}", response.getMsg(), response.getSubCode(), response.getSubMsg(), outTradeNo, response.getBody());
            }
        } catch (AlipayApiException e) {
            server.getLogger().warn("支付宝 订单码支付 API检查订单时执行错误", e);
        }
    }

    private void cancelAlipayFaceToFace(String outTradeNo) {
        try {
            Configuration config = server.getConfig();
            AlipayClient alipayClient = new DefaultAlipayClient(config.getAlipayFaceToFace().getConfig());
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(outTradeNo);
            request.setBizModel(model);
            AlipayTradeCloseResponse response = alipayClient.execute(request);
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] 支付宝 订单码支付 关闭订单结果: {}", response);
            }
            if (!response.isSuccess()) {
                if (!response.getSubCode().equals("ACQ.TRADE_NOT_EXIST")) {
                    server.getLogger().warn("支付宝 订单码支付 关闭订单失败 {}, {} {}，要关闭的订单号 {}\n    {}", response.getMsg(), response.getSubCode(), response.getSubMsg(), outTradeNo, response.getBody());
                }
            }
        } catch (AlipayApiException e) {
            server.getLogger().warn("支付宝 订单码支付 API关闭交易时执行错误", e);
        }
    }
}
