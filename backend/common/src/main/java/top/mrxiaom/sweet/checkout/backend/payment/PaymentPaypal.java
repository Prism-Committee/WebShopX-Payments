package top.mrxiaom.sweet.checkout.backend.payment;

import io.github.eealba.payper.core.json.Json;
import io.github.eealba.payper.orders.v2.api.CheckoutOrdersApiClient;
import io.github.eealba.payper.orders.v2.model.*;
import top.mrxiaom.sweet.checkout.backend.AbstractPaymentServer;
import top.mrxiaom.sweet.checkout.backend.Configuration;
import top.mrxiaom.sweet.checkout.backend.data.ClientInfo;
import top.mrxiaom.sweet.checkout.packets.plugin.PacketPluginRequestOrder;

import java.util.Collections;
import java.util.List;
import java.util.TimerTask;

public class PaymentPaypal<C extends ClientInfo<C>> {
    private static final Json json = Json.create();
    AbstractPaymentServer<C> server;
    public PaymentPaypal(AbstractPaymentServer<C> server) {
        this.server = server;
    }

    public PacketPluginRequestOrder.Response handleCreateOrder(PacketPluginRequestOrder packet, C client, Configuration config) {
        String orderId = client.nextOrderId();
        if (orderId == null) {
            return new PacketPluginRequestOrder.Response("payment.can-not-create-id");
        }
        try {
            CheckoutOrdersApiClient api = CheckoutOrdersApiClient.create(config.getPaypal().getConfig());
            // 创建订单
            Order createdOrder = api.orders().create().withBody(OrderRequest.builder()
                            // 添加一个订单采购单位
                            .purchaseUnits(Collections.singletonList(PurchaseUnitRequest.builder()
                                    // 设置金额
                                    .amount(AmountWithBreakdown.builder()
                                            .value(packet.getPrice())
                                            .currencyCode(CurrencyCode.CNY)
                                            .build())
                                    .build()))
                            // 设置订单付款意图
                            // CAPTURE: 商家打算在客户付款后立即捕获付款。
                            .intent(CheckoutPaymentIntent.CAPTURE)
                            .build())
                    .retrieve()
                    .toEntity();
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] Paypal 官方接口 下单结果: {}", json.toJson(createdOrder));
            }
            String url = createdOrder.links().get(0).href();

            ClientInfo.Order<C> order = client.createOrder(orderId, "paypal", packet.getPlayerName(), packet.getPrice());
            String outTradeNo = createdOrder.id();
            order.setCancelAction(() -> cancelOrder(outTradeNo));
            // 轮询检查是否交易成功
            order.setTask(new TimerTask() {
                @Override
                public void run() {
                    checkOrder(client, this, orderId, outTradeNo);
                }
            });
            // 每3秒检查一次是否支付成功
            server.getTimer().schedule(order.getTask(), 1000L, 3000L);
            server.getLogger().info("PayPal 官方接口 下单成功 : {}", outTradeNo);
            return new PacketPluginRequestOrder.Response("face2face", orderId, order.getMoney(), url);
        } catch (Exception e) {
            client.removeOrder(orderId);
            server.getLogger().warn("Paypal 官方接口 API执行错误", e);
            return new PacketPluginRequestOrder.Response("payment.internal-error");
        }
    }

    private void checkOrder(C client, TimerTask task, String orderId, String outTradeNo) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) { // 插件连接断开时、任务不存在时取消任务，并关闭交易
            task.cancel();
            if (order != null) {
                order.setTask(null);
                client.removeOrder(order);
            }
            cancelOrder(outTradeNo);
            return;
        }
        try {
            CheckoutOrdersApiClient api = CheckoutOrdersApiClient.create(config.getPaypal().getConfig());

            // 查询订单
            Order response = api.orders().get().withId(outTradeNo).retrieve().toEntity();
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] Paypal 官方接口 检查结果: {}", json.toJson(response));
            }
            switch (response.status()) {
                // 订单是使用指定的上下文创建的。
                case CREATED:
                    // 该订单被保存并保留。订单状态将继续处于进行中，直到对订单中的所有采购单位进行捕获，并使用 final_capture = true。
                case SAVED:
                    // 客户已通过 PayPal 钱包或其他形式的访客或无品牌付款批准付款。例如，银行卡、银行账户等。
                case APPROVED:
                    break;
                // 订单中的所有采购单位都将作废。
                case VOIDED:
                    client.removeOrder(order);
                    server.sendPaymentCancel(client, order, "payment.voided");
                    break;
                // 订单意图已完成，并创建了付款资源。
                // 为了避免麻烦，只添加一个付款单位，无需进行额外检查
                case COMPLETED:
                    List<PurchaseUnit> purchaseUnits = response.purchaseUnits();
                    if (purchaseUnits.isEmpty()) {
                        // 玩家未完成采购单位，取消订单并提示玩家联系管理员
                        client.removeOrder(order);
                        server.sendPaymentCancel(client, order, "payment.not-purchase-all");
                        break;
                    }
                    AmountWithBreakdown amount = purchaseUnits.get(0).amount();
                    client.removeOrder(order);

                    String buyerFullName = response.payer().name().fullName();
                    String money = amount.value();
                    server.getLogger().info("[收款] 从 PayPal 收款，来自 {} 的 ￥{}", buyerFullName, money);
                    server.sendPaymentSuccess(client, order, money);
                    break;
                // 订单需要付款人执行作（e.g. 3DS 身份验证）。
                // 将付款人重定向到在授权或捕获订单之前作为响应的一部分返回的“rel”：“payer-action”HATEOAS 链接。
                // 某些支付来源可能不会返回付款人作 HATEOAS 链接（例如 MB WAY）。
                // 对于这些支付来源，付款人作由计划本身管理（例如，通过短信、电子邮件、应用内通知等）。
                case PAYER_ACTION_REQUIRED:
                    break;
            }
        } catch (Exception e) {
            server.getLogger().warn("Paypal 官方接口 API检查订单时执行错误", e);
        }
    }
    private void cancelOrder(String outTradeNo) {
        // Paypal 无法取消或者作废订单，取消订单这里什么都不用做
        // 让它自动过期就完事了
    }
}
