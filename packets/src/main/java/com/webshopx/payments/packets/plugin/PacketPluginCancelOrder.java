package com.webshopx.payments.packets.plugin;

import com.webshopx.payments.packets.common.IPacket;
import com.webshopx.payments.packets.common.IResponsePacket;

/**
 * 插件主动取消订单
 */
@SuppressWarnings("FieldMayBeFinal")
public class PacketPluginCancelOrder implements IPacket<PacketPluginCancelOrder.Response> {
    /**
     * 订单ID
     */
    private String orderId;

    public PacketPluginCancelOrder(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    @Override
    public Class<Response> getResponsePacket() {
        return Response.class;
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class Response implements IResponsePacket {
        /**
         * 错误信息，无错误时为空字符串
         */
        private String error;

        public Response() {
            error = "";
        }

        public Response(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
}
