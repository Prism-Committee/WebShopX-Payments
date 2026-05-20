This project is distributed under the AGPL-3.0 license.

# WebShopX-Payments / WSXPay

WSXPay 是 WebShopX 的支付 provider。它通过 Bukkit `ServicesManager` 注册 `WebShopXPaymentApi`，并内嵌支付后端。WebShopX 负责订单、钱包、入账和发货；WSXPay 只负责支付下单、查单与支付事件转发。

![Minecraft versions](https://img.shields.io/badge/minecraft-1.7.10--26.1-blue) ![Bukkit with Backend Java Compatible](https://img.shields.io/badge/bukkit--with--backend-Java_8-purple)

<details>
    <summary>免责声明</summary>
    <p>仅供学习研究与技术交流，请勿用于非法用途，后果自负。</p>
    <p>本项目作者与贡献者不对本项目的有效性、可靠性、安全性等作任何明示或暗示的保证，也不对使用或滥用本项目造成的任何直接或间接的损失、责任、索赔、要求或诉讼承担任何责任。</p>
    <p>本项目源代码或二进制文件的使用者应当遵守相关法律法规，尊重 Tencent 公司和阿里巴巴集团的版权与隐私，不得侵犯其与其它第三方的合法权益，不得从事任何违法或违反道德的行为。</p>
    <p>使用本程序的源代码或二进制文件的任何部分即代表你同意此条款，如有异议，请立即停止使用并删除所有相关文件。</p>
    <p>项目简介中的“无费率”指的是，本项目不额外收取手续费。例如当面付/订单码支付接口，支付宝官方收取<code>0.6%</code>手续费，那么费率就是<code>0.6%</code>，无额外的中间商抽成。</p>
</details>

## 模块结构

```
WSXPay 目录结构
  ├─ backend: 内嵌支付后端公共代码
  ├─ packets: 支付通信包结构
  ├─ plugin: Bukkit 插件
  └─ wechat-hook: 微信 Hook 工具
```

## 部署与配置

1. 在 Minecraft 服务端安装 WebShopX 和 WSXPay `with-backend` Bukkit 插件。
2. 编辑 `backend/config.json` 配置支付宝、微信、PayPal、Hook 等支付平台参数。
3. 编辑 `config.yml` 配置支付方式启用、二维码地图和超时设置。

WSXPay 启动时会尝试查找 `com.webshopx.payment.api.WebShopXPaymentApi`。如果 WebShopX 已安装，会自动注册 provider：

```text
providerId: webshopx-payments
displayName: WebShopX Payments
```

## 支付方案

|   | 平台   | 方案                                                                                     | 说明                                                                 |
|---|:------|:-----------------------------------------------------------------------------------------|:---------------------------------------------------------------------|
| ✅ | 支付宝 | [订单码支付](https://b.alipay.com/page/product-workspace/product-detail/I1080300001000068149) | 官方接口。生成支付二维码供用户扫码。                                         |
| ✅ | 支付宝 | Hook                                                                                     | 第三方接口。二维码金额与 Hook 收款消息匹配确认。                           |
| ✅ | 微信  | [Native](https://pay.weixin.qq.com/static/product/product_intro.shtml?name=native)       | 官方接口。生成支付二维码供用户扫码。                                         |
| ✅ | 微信  | Hook                                                                                     | 第三方接口。二维码金额与 Hook 收款消息匹配确认。                           |
| ❔ | PayPal | [PayPal REST API](https://developer.paypal.com/docs/api/orders/v2/)                      | 官方接口。通过 `payper` 调用 PayPal，下单后返回支付链接。                   |

## 支付事件与补单

WSXPay 会在插件数据目录保存 `wsxpay-orders.yml` 作为订单索引，记录 `merchantOrderId`、后端订单号、金额、币种、支付 URL 和通知状态。后端事件通过 `PacketBackendPaymentEvent` 推送到 Bukkit 插件，再转换为 `PaymentNotify` 回调 WebShopX listener。

如果 WebShopX listener 临时失败，成功通知不会立即丢弃；WSXPay 会保留未确认状态并定时重试。WebShopX 补单时可通过 `queryPayment` 查询同一笔 `merchantOrderId` 或 `providerOrderId`。

事件建议字段：

```text
merchantOrderId 或 providerOrderId
status
amountMinor
currency
method
paidAt
rawEventId
```

## 二维码地图

WSXPay 支持在游戏内使用地图物品展示二维码。该功能用于“游戏内扫码体验”，可通过 `config.yml` 的 `map-item` 与 `payment.action-bar` 配置控制。若 WebShopX 前端已展示二维码，也可以保留此功能用于管理员调试。

## 命令 (Bukkit)

| 命令              | 描述                 | 权限                     |
|-------------------|----------------------|--------------------------|
| `/wsxpay help`    | 查看帮助             | `webshopxpayments.command` |
| `/wsxpay reload`  | 重载配置             | `webshopxpayments.admin` |
| `/wsxpay status`  | 查看后端连接状态     | `webshopxpayments.admin` |

## 常见故障

- WebShopX 找不到支付 provider：确认同时安装 WebShopX 和 WSXPay，且日志中出现 `Registered WebShopXPaymentApi provider: webshopx-payments`。
- 下单返回 `PROVIDER_UNAVAILABLE`：确认安装的是 `with-backend` 构建，且内嵌后端配置可以正常加载。
- 支付方式不可用：确认 `payment.enable.alipay/wechat/paypal` 和 `backend/config.json` 中对应平台配置均已启用。
- 补单查不到订单：确认 `wsxpay-orders.yml` 未被删除，且 WebShopX 传入的是同一笔 `merchantOrderId`。

## 开发者

新增支付方式请从 `:backend:common` 模块的 `com.webshopx.payments.backend.payment` 包开始，完成平台签名校验、金额校验与状态标准化，再通过 `PacketBackendPaymentEvent` 推送事件。

## 鸣谢

+ [alipay/alipay-sdk-java-all](https://github.com/alipay/alipay-sdk-java-all): 支付宝官方 SDK (v2) —— Apache-2.0 License
+ [wechatpay-apiv3/wechatpay-java](https://github.com/wechatpay-apiv3/wechatpay-java): 微信支付官方 SDK —— Apache-2.0 License
+ [eealba/payper](https://github.com/eealba/payper) PayPal 官方接口第三方 SDK (v2) —— Apache-2.0 License
