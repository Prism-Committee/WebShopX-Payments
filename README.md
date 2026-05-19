# WebShopX-Payments / WSXPay

WSXPay 是 fork 自 SweetCheckout 的 WebShopX 支付 provider。它保留支付宝、微信、PayPal、Hook、内嵌支付后端和二维码能力，并通过 Bukkit `ServicesManager` 注册 `WebShopXPaymentApi`，由 WebShopX 主插件负责订单、钱包、入账和发货。

本仓库仍保留原 SweetCheckout 的部分兼容命令与模块，后续会逐步弱化内置商城、点券、排行、PAPI 等与 WebShopX 重叠的业务。原项目来源与许可证说明保留在本 README 和 `LICENSE` 中。

![Minecraft versions](https://img.shields.io/badge/minecraft-1.7.10--26.1-blue) ![Bukkit with Backend Java Compatible](https://img.shields.io/badge/bukkit--with--backend-Java_8-purple)

<details>
    <summary>免责声明</summary>
    <p>仅供学习研究与技术交流，请勿用于非法用途，后果自负。</p>
    <p>本项目作者与贡献者不对本项目的有效性、可靠性、安全性等作任何明示或暗示的保证，也不对使用或滥用本项目造成的任何直接或间接的损失、责任、索赔、要求或诉讼承担任何责任。</p>
    <p>本项目源代码或二进制文件的使用者应当遵守相关法律法规，尊重 Tencent 公司和阿里巴巴集团的版权与隐私，不得侵犯其与其它第三方的合法权益，不得从事任何违法或违反道德的行为。</p>
    <p>使用本程序的源代码或二进制文件的任何部分即代表你同意此条款，如有异议，请立即停止使用并删除所有相关文件。</p>
    <p>项目简介中的“无费率”指的是，本项目不额外收取手续费。例如当面付/订单码支付接口，支付宝官方收取<code>0.6%</code>手续费，那么费率就是<code>0.6%</code>，无额外的中间商抽成。</p>
</details>

## 简介

先说缺点，部分场景只能用金额来关联用户，无法承受高并发需求，这对于 Minecraft 服务器来说已基本足够，可以凑合着用。

```
WSXPay 目录结构
  ├─ backend: 内嵌支付后端公共代码
  ├─ packets: 网络包结构
  ├─ plugin: Bukkit插件
  └─ wechat-hook: 微信Hook软件
```

+ `内嵌支付后端公共代码`: 负责支付平台下单、查询和 Hook/回调处理，由 `with-backend` Bukkit 插件直接加载
+ `网络包结构`: 插件内部支付请求和事件对象
+ `Bukkit插件`: 注册 `WebShopXPaymentApi`，内嵌支付后端，兼容旧交互逻辑
+ `微信Hook软件`: 接收微信收款消息，转发给后端处理

## 使用方法

在 Minecraft 服务端上安装 WebShopX 和 WSXPay `with-backend` Bukkit 插件。WSXPay 的支付后端已经内嵌在插件中，不需要额外启动独立后端进程。

在 [MCIO Plugins](https://plugins.mcio.dev/docs/checkout/install/backend) 文档中有一些支付方案的配置教程，自行查阅。

安装后，编辑 WSXPay 插件目录中的 `backend/config.json` 配置支付宝、微信、PayPal 等支付平台参数；编辑 `config.yml` 调整 Bukkit 侧支付方式开关。WebShopX 会通过 `WebShopXPaymentApi` 调用 providerId 为 `webshopx-payments` 的支付服务。

旧版 `/cz points`、`/checkout buy` 等命令暂作为兼容入口保留；WebShopX 接入时不应依赖这些命令完成充值或发货。

## WebShopX 接入

推荐部署关系：

```text
WebShopX
  -> Bukkit ServicesManager
  -> WebShopX-Payments / WSXPay
  -> WSXPay 内嵌后端
  -> 支付宝 / 微信 / PayPal
```

WSXPay 启动时会尝试查找 `com.webshopx.payment.api.WebShopXPaymentApi`。如果 WebShopX 已安装，WSXPay 会注册 provider：

```text
providerId: webshopx-payments
displayName: WebShopX Payments
```

WebShopX 负责创建充值订单、校验金额和币种、入账 ShopCoin。WSXPay 只负责创建支付订单、查询支付状态、转发支付通知，不直接写 WebShopX 数据库，也不直接修改钱包或发货状态。

### Bukkit 配置示例

```yaml
provider-id: webshopx-payments

legacy-business:
  enabled: false
  placeholderapi: false

payment:
  default-method: alipay
  api-timeout-seconds: 15
  enable:
    wechat: true
    alipay: true
    paypal: false
  timeout: 120
  allow-increasing: false
```

`legacy-business.enabled` 默认为 `false`。关闭时，旧 `/checkout points`、`/checkout buy`、排行、交易统计、手动日志等入口不会出现在补全中，也不会处理玩家充值或商品发货。`/wsxpay qrcode`、`/wsxpay map`、`/wsxpay reload` 仍可用于二维码和配置调试。

### 支付通知与补单

WSXPay 会在插件数据目录保存 `wsxpay-orders.yml` 作为本地订单索引，保存 `merchantOrderId`、后端订单号、金额、币种、支付 URL 和通知状态。支付成功后，WSXPay 会把后端事件转换成 `PaymentNotify` 并调用 WebShopX 注册的 listener。

如果 WebShopX listener 临时失败，成功通知不会立即丢弃；WSXPay 会保留未确认状态并定时重试。WebShopX 补单时可通过 `queryPayment` 查询同一笔 `merchantOrderId` 或 `providerOrderId`。

### 扩展支付方式

新增支付方式时，优先在后端支付实现中完成平台签名校验、金额校验和状态标准化，再通过 `PacketBackendPaymentEvent` 推送到 Bukkit 插件。事件至少应包含：

```text
merchantOrderId 或 providerOrderId
status
amountMinor
currency
method
paidAt
rawEventId
```

错误码返回给 WebShopX 时应使用稳定英文码，例如 `METHOD_UNSUPPORTED`、`ORDER_NOT_FOUND`、`UPSTREAM_TIMEOUT`、`SIGNATURE_INVALID`，平台原始消息可以放在 `message` 或 `extra`。

### 常见故障

- WebShopX 找不到支付 provider：确认同时安装 WebShopX 和 WSXPay，且日志中出现 `已注册 WebShopXPaymentApi provider: webshopx-payments`。
- 下单返回 `PROVIDER_UNAVAILABLE`：确认安装的是 `with-backend` 构建，且内嵌后端配置可以正常加载。
- 支付方式不可用：确认 `payment.enable.alipay/wechat/paypal` 和 `backend/config.json` 中对应平台配置均已启用。
- 补单查不到订单：确认 `wsxpay-orders.yml` 未被删除，且 WebShopX 传入的是同一笔 `merchantOrderId`。
- 默认命令无法充值或购买：这是预期行为。WSXPay 默认只作为 WebShopX 支付 provider；如需临时启用旧业务，可将 `legacy-business.enabled` 改为 `true`。

## 支付方案

|   | 平台　　   | 方案　　                                                                                     | 说明                                                                      |
|---|:-------|:-----------------------------------------------------------------------------------------|:------------------------------------------------------------------------|
| ✅ | 支付宝    | [订单码支付](https://b.alipay.com/page/product-workspace/product-detail/I1080300001000068149) | 官方接口。订单码支付是指商家按支付宝的支付协议生成订单`二维码`，用户使用支付宝“扫一扫”即可完成付款。                    |
| ✅ | 支付宝    | Hook                                                                                     | 第三方接口。商家在后端配置各金额的支付`二维码`，付款时将二维码展示给用户，Hook截取开放平台的卖出交易查询信息，由后端进行确认付款的模式。 |
| ✅ | 微信     | [Native](https://pay.weixin.qq.com/static/product/product_intro.shtml?name=native)       | 官方接口。Native支付是指商户系统按微信支付协议生成支付`二维码`，用户再用微信“扫一扫”完成支付的模式。                 |
| ✅ | 微信     | Hook                                                                                     | 第三方接口。商家在后端配置各金额的支付`二维码`，付款时将二维码展示给用户，Hook截取微信PC版收款信息，由后端进行确认付款的模式。     |
| ❔ | PayPal | [PayPal REST API](https://developer.paypal.com/docs/api/orders/v2/)                      | 官方接口。通过第三方的API包装库 `payper`，向官方API发送请求，用户再通过返回的链接包装成的二维码完成支付的模式。         |

+ ✅ 代表 此方案可用，已测试通过。
+ ❔ 代表 此方案已实现，但由于开发者未申请相关接口等原因，未进行测试。
+ ❌ 代表 此方案暂不可用，暂无实现方法。

## 命令 (Bukkit)

根命令 `/sweetcheckout`，别名为 `/checkout` 或 `/cz`。  
`<>`包裹的为必选参数，`[]`包裹的为可选参数。  

| 命令                                     | 描述                                                       | 权限                           |
|----------------------------------------|----------------------------------------------------------|------------------------------|
| `/checkout points <类型> <金额>`           | 通过微信(wechat)或支付宝(alipay)下单指定金额的点券                        | `sweet.checkout.points`      |
| `/checkout buy <商品ID> <类型>`            | 通过微信(wechat)或支付宝(alipay)下单指定商品                           | 在商品配置定义                      |
| `/checkout rank`                       | 查看充值排行榜                                                  | `sweet.checkout.rank`        |
| `/checkout stats <起始时间> [结束时间]`        | 查看指定时间段内的交易统计信息                                          | `sweet.checkout.stats`       |
|                                        | (其时间格式可用 `月`, `年-月`, `年-月-日` 三种格式)                       |                              |
| `/checkout check`                      | 查看自己的充值记录                                                | `sweet.checkout.check`       |
| `/checkout check [玩家]`                 | 查看自己或某人的充值记录                                             | `sweet.checkout.check.other` |
| `/checkout qrcode <二维码内容>`             | 根据输入内容生成二维码，并通过地图展示。此命令用于测试地图以及二维码生成器的可用性                | OP                           |
| `/checkout map [文件名]`                  | 不输入文件名时，将手中的地图保存到`output.map`文件；输入文件名时，通过地图预览文件以测试文件是否正常 | OP                           |
| `/checkout log <玩家> <类型> <金额> <原因...>` | 手动添加充值记录。类型可以是任意字符串。                                     | OP/控制台                       |
| `/checkout reload database`            | 重新连接数据库                                                  | OP/控制台                       |
| `/checkout reload`                     | 重载配置文件                                                   | OP/控制台                       |

## 变量 (PAPI)

```
%sweetcheckout_rank_<第几名>_name% 充值排行榜第几名的玩家名
%sweetcheckout_rank_<第几名>_money% 充值排行榜第几名的金额
%sweetcheckout_points_money_<价格>% 获取原价点券数量
%sweetcheckout_money_shop_<商品ID>% 获取商品原价
%sweetcheckout_modified_money_<价格>% 获取修饰后的点券价格
%sweetcheckout_modified_points_<价格>% 获取修饰后的点券数量
%sweetcheckout_shop_modified_money_<商品ID>% 获取修饰后的商品价格
```

## 鸣谢

+ [alipay/alipay-sdk-java-all](https://github.com/alipay/alipay-sdk-java-all): 支付宝官方SDK(v2) —— Apache-2.0 License
+ [wechatpay-apiv3/wechatpay-java](https://github.com/wechatpay-apiv3/wechatpay-java): 微信支付官方SDK —— Apache-2.0 License
+ [eealba/payper](https://github.com/eealba/payper) PayPal 官方接口 第三方SDK (v2) —— Apache-2.0 License

## 开发者

对于想要添加支付方案支持者，请参见 `:backend:common` 模块的 [top.mrxiaom.sweet.checkout.backend.payment](https://github.com/MrXiaoM/SweetCheckout/tree/main/backend/common/src/main/java/top/mrxiaom/sweet/checkout/backend/payment) 包。支付订单下单逻辑、支付成功事件广播逻辑、取消支付逻辑等等，都在这里，请按自身需求进行增改。  
基本上，经过这一层接口的抽象，我们需要传递的信息大约只有：商品名（可选）、金额、订单号、支付二维码。

注意：由于本项目的目标是尽可能减少中间商，会造成**增加中间商**的拉取请求将被**拒绝**。

[![jitpack badge](https://jitpack.io/v/top.mrxiaom/SweetCheckout.svg)](https://jitpack.io/#top.mrxiaom/SweetCheckout)

```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    compileOnly("top.mrxiaom.SweetCheckout:shared:$VERSION")
    compileOnly("top.mrxiaom:qrcode-encoder:1.0.0") // 1.0.6 起需要添加
}
```

对接本插件的开发文档，请见 [MCIO Plugins](https://plugins.mcio.dev/docs/checkout/api/)。
