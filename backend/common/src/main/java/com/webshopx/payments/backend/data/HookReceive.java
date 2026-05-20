package com.webshopx.payments.backend.data;

public class HookReceive {
    /**
     * 支付来源，可用 wechat 和 alipay
     */
    private String type;
    /**
     * 付款金额
     */
    private String money;

    public String getType() {
        return type;
    }

    public String getMoney() {
        return money;
    }
}
