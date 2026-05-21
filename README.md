This plugin is based on [SweetCheckout](https://github.com/MrXiaoM/SweetCheckout), licensed under AGPL-3.0.

# WebShopX-Payments / WSXPay

WebShopX-Payments 是 WebShopX 的支付 provider 插件。它通过 Bukkit `ServicesManager` 注册 `WebShopXPaymentApi` provider，启动内嵌支付后端，并把支付结果回传给 WebShopX。

WebShopX 仍然负责充值订单、钱包余额、账务入账和发货。WebShopX-Payments 只负责创建支付、轮询或接收支付状态、转发支付结果事件、可选的游戏内二维码地图展示，以及可选的 Hook 收款匹配。

## 功能范围

- Bukkit provider id：`webshopx-payments`
- provider 展示名：`WebShopX Payments`
- 发布形态：`with-backend`
- 支持的支付方式：
  - PayPal REST Orders API
  - MercadoPago Checkout Pro
  - 微信支付 Native
  - 支付宝当面付预创建
  - 可选的微信/支付宝 Hook 模式
- 可选的 Minecraft 地图二维码展示
- 可为每个支付平台单独配置 HTTP 代理

## 仓库结构

```text
backend/common                 内嵌后端与支付平台对接
packets                        后端与 Bukkit 插件通信包
plugin/bukkit/shared           Bukkit provider 通用代码
plugin/bukkit/with-backend     可安装的内嵌后端 Bukkit 插件
plugin/nms                     地图二维码兼容适配
wechat-hook                    可选的外部 Hook 辅助工具
```

旧的独立后端和 websocket 插件变体不再作为发布形态。部署时请使用 `with-backend` jar。

## 构建

推荐使用 JDK 25。在仓库根目录执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
$env:JAVA_TOOL_OPTIONS='-Duser.country=US'
.\gradlew.bat :plugin:bukkit:with-backend:build
```

构建产物：

```text
out/WebShopX-Payments-<version>-full.jar
```

## 部署

1. 将 WebShopX 放入服务端 `plugins/` 目录。
2. 将 `WebShopX-Payments-<version>-full.jar` 放入同一个 `plugins/` 目录。
3. 启动一次服务端，生成默认配置。
4. 修改 `plugins/WebShopX-Payments/config.yml`。
5. 修改 `plugins/WebShopX-Payments/backend/config.json`。
6. 重启服务端。

启动时应能看到类似日志：

```text
[WebShopX-Payments] Registered WebShopXPaymentApi provider: webshopx-payments
[WebShopX] Payment provider detected; recharge listener registered: webshopx-payments
```

## 配置

`config.yml` 控制 Bukkit provider 行为：

- `provider-id`：暴露给 WebShopX 的 provider id
- `payment.default-method`：WebShopX 请求 `AUTO` 时使用的默认支付方式
- `payment.enable.*`：Bukkit 侧支付方式开关
- `payment.api-timeout-seconds`：同步等待内嵌后端响应的超时时间
- `payment.timeout`：玩家支付超时时间
- `payment.allow-increasing`：仅 Hook 模式使用，控制金额冲突时是否自动加价
- `payment.action-bar`：游戏内支付进度提示
- `map-item`：可选的地图二维码物品设置

`backend/config.json` 控制支付平台凭据：

- `paypal`：PayPal REST API 凭据、接口地址、币种、代理
- `mercadopago`：MercadoPago Access Token、接口地址、币种、Checkout Pro 返回/通知地址、代理
- `wechat_native`：微信 Native 商户凭据、币种、通知地址、代理
- `alipay_face2face`：支付宝当面付凭据、币种、产品码、代理
- `hook`：可选的 Hook HTTP 接口和个人收款码匹配设置

不要把生产密钥、商户私钥、client secret 等敏感信息提交到源码仓库。

## 命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/wsxpay help` | `webshopxpayments.command` | 查看命令帮助 |
| `/wsxpay reload` | `webshopxpayments.admin` | 重载插件与内嵌后端配置 |
| `/wsxpay status` | `webshopxpayments.admin` | 查看后端/provider 状态 |

## 支付流程

1. WebShopX 调用 `WebShopXPaymentApi#createPayment`。
2. WebShopX-Payments 将请求发送到内嵌后端。
3. 内嵌后端创建上游支付订单，并返回支付链接或二维码内容。
4. WebShopX-Payments 在 `wsxpay-orders.yml` 中保存轻量订单索引，用于查询和重试。
5. 内嵌后端轮询或接收支付结果，并发出 `PacketBackendPaymentEvent`。
6. WebShopX-Payments 将事件转换为 `PaymentNotify`，调用 WebShopX listener。
7. WebShopX 校验并执行充值入账。

如果 WebShopX listener 临时失败，成功支付事件会保持待确认状态并继续重试。

## 常见问题

- WebShopX 找不到 provider：确认 WebShopX 和 WebShopX-Payments 都已安装，并且启动日志包含 `Registered WebShopXPaymentApi provider: webshopx-payments`。
- 返回 `PROVIDER_UNAVAILABLE`：确认安装的是 `with-backend` jar，并且内嵌后端已正常加载。
- 支付方式不可用：确认 `config.yml` 与 `backend/config.json` 中对应支付方式都已启用。
- PayPal 网络失败：在 `backend/config.json` 的 PayPal 配置中启用代理，并优先用 sandbox 凭据测试。
- MercadoPago 网络失败：确认 `backend/config.json` 的 `mercadopago.access_token`、`currency` 与账号所在国家/地区匹配；如在中国大陆服务器访问，优先配置代理。
- 构建出现 Java class version 错误：请使用 JDK 25。

## 致谢

- [MrXiaoM/SweetCheckout](https://github.com/MrXiaoM/SweetCheckout)
- [alipay/alipay-sdk-java-all](https://github.com/alipay/alipay-sdk-java-all)
- [wechatpay-apiv3/wechatpay-java](https://github.com/wechatpay-apiv3/wechatpay-java)
- [eealba/payper](https://github.com/eealba/payper)

