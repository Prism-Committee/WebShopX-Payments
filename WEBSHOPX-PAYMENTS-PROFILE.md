# WebShopX-Payments：WebShopX 的支付插件

[![Java](https://camo.githubusercontent.com/0fde08834d6cdca7409ed47c4dbd37b994191ed7dea45005bd612684c5c90a08/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f4a6176612d31375f2d2d5f32352d626c75653f7374796c653d666c61742d737175617265266c6f676f3d6f70656e6a646b266c6f676f436f6c6f723d7768697465)](https://github.com/Prism-Committee/WebShopX-Issues#) [![Paper](https://camo.githubusercontent.com/b44782a938f66b00a6a0d386bfaf4e50012ca2b9a070c02ee06551ff82b6f464/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f50617065722d312e31382e322532422d6379616e3f7374796c653d666c61742d737175617265)](https://github.com/Prism-Committee/WebShopX-Issues#) [![Folia](https://camo.githubusercontent.com/23ef3e358825df1117f62be6452dede872a800b2e4b502f997ddc7efb6541963/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f466f6c69612d312e31392e342532422d707572706c653f7374796c653d666c61742d737175617265)](https://github.com/Prism-Committee/WebShopX-Issues#)

> WebShopX-Payments 负责把现实中的支付接入到 WebShopX

## 快捷链接

[![](https://img.shields.io/badge/官方Wiki-Docusaurus-34d058?style=for-the-badge&logo=docusaurus&logoColor=white)](https://docs.akihito.dpdns.org/category/webshopx-payments)
[![](https://img.shields.io/badge/GitHub-WebShopX--Payments-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Prism-Committee/WebShopX-Payments)

---

## 主要功能

`WebShopX-Payments`（又称 `WSXPay`）是 `WebShopX` 的支付 Provider（底层服务提供者）插件。它将现实中的货币支付与 Minecraft 服务器（WebShopX）无缝连接。

- **WebShopX**：负责核心业务，包括玩家账户余额、充值订单管理、商品展示与购买逻辑、兑换码以及发货（玩家领取/充值命令执行）。
- **WebShopX-Payments**：专注于支付链路本身，包括请求内嵌支付后端创建支付、轮询/接收回调支付状态、匹配 Hook 收款通知，最终将成功的支付状态上报回 `WebShopX`。

> 📌 **部署前提**：这是一个**可选前置插件**，必须与 `WebShopX` 核心插件配合使用。它自身不处理商品与发货逻辑，单独安装将不会实现任何业务功能。

---

## 支持的支付方式

本页汇总 `WebShopX-Payments` 当前支持或已实现的支付方案，便于你快速评估可用性与接入优先级。

| **状态** | **平台**    | **方案**                                                     | **说明**                                                     |
| -------- | ----------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 🟢        | 支付宝      | [订单码支付](https://open.alipay.com/api/detail?code=I1080300001000068149&index=0) | **官方接口**。由服务端调用官方 API 生成动态支付二维码，用户扫码完成付款。 |
| 🔵        | 微信支付    | [Native 支付](https://pay.weixin.qq.com/static/product/product_intro.shtml?name=native) | **官方接口**。标准扫码支付模式。系统生成二维码码，用户扫码后拉起标准微信收银台。 |
| 🟢        | PayPal      | [REST API v2](https://developer.paypal.com/docs/api/orders/v2/) | **官方接口**。调用 PayPal 官方订单 API，动态生成支付审批链接或二维码。 |
| 🟡        | MercadoPago | [Pro Checkout](https://www.mercadopago.com.br/developers/)   | **官方接口**。MercadoPago官方接口。                          |
| 🟢        | Stripe      | [Checkout / Elements](https://docs.stripe.com/)              | **官方接口**。已接入其标准 Web 支付流，支持多币种及各类国际信用卡。 |
| 🔵        | 支付宝      | 免签 Hook                                                    | **第三方方案**。后端配置固定金额收款码，通过 Hook 截取开放平台的交易通知或账单信息，实现免签自动化回调。 |
| 🔵        | 微信支付    | 免签 Hook                                                    | **第三方方案**。后端配置固定金额收款码，通过 Hook 截取微信 PC 端的实时收款通知，由后端匹配金额并确认付款。 |

> - **🟢 完整验证**：方案可行。开发人员已完成全流程的闭环测试，确认功能正常。
> - **🔵 间接验证**：方案可行。已有其他人员测试并成功反馈，但开发人员未亲自验证。
> - **🟡 待验证**：代码已实现。因开发人员缺少相关接口权限/凭证，暂未进行实际测试。
>

---

## 快速开始

1. 将 `WebShopX-Payments.jar` 放入服务器的 `plugins` 文件夹中。
2. 重启服务器，以生成默认配置文件。
3. 打开 `plugins/WebShopX-Payments/` 目录修改配置文件，参照 [官方Wiki](https://docs.akihito.dpdns.org/category/webshopx-payments) 配置您所需的支付接口参数。
4. 重启服务器。
5. 在 `WebShopX` 的 `admin.html` 页面勾选识别到的支付方式。

---

## 游戏内命令

| 命令 | 说明 |
|---|---|
| `/wsxpay help` | 查看帮助 |
| `/wsxpay reload` | 重载配置 |
| `/wsxpay status` | 查看插件当前状态 |

---

## 适用场景

- 正在使用 WebShopX，并希望接入多种支付方式
- 希望接入现实的支付方式

---

**📌 关于二次开发与版权说明**

本插件为基于 **SweetCheckout** 的深度二次开发版本。原插件采用 **AGPL-3.0 开源协议**，允许自由修改与分发；本插件亦严格遵守并采用 **AGPL-3.0 开源协议**。

- **原项目地址**：[GitHub - SweetCheckout](https://github.com/MrXiaoM/SweetCheckout)
- ❤️ **特别致谢**：在此特别感谢原作者 **[MrXiaoM](https://www.minebbs.com/members/mrxiaom.24586/)**。感谢其提供的高质量开源基础与优秀的设计思路。虽然本项目在功能上进行了大幅度重构与魔改，但正是得益于原作者的开源精神，才让本项目得以诞生！

---

## 更多信息

- **开源协议**：AGPL-3.0
- **官方Wiki**：https://docs.akihito.dpdns.org/category/webshopx-payments
- **GitHub**：https://github.com/Prism-Committee/WebShopX-Payments
- **QQ 交流群**：[![QQ Group](https://camo.githubusercontent.com/f17a3537e4b20c43a32d7d4214e604dff9d410788a6ae3ee129acb1ba8c9e76b/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f51515f47726f75702d3633363830333337322d3132423746353f7374796c653d666c61742d737175617265266c6f676f3d74656e63656e742d7171266c6f676f436f6c6f723d7768697465)](https://qun.qq.com/universal-share/share?ac=1&authKey=EMHvFOsEqOBnEQi%2FJZtN%2BFwamTisdy0A02IwhRsxJG8t9GWK4uKs2G4CgZpT3yHW&busi_data=eyJncm91cENvZGUiOiI2MzY4MDMzNzIiLCJ0b2tlbiI6ImlpV3lHR3BFT3NvdWxUYysrSnFBN3lSWGRGU1BlTmF4a3FJSnNXeFBNZkI0emZRVDUxdCszbzdEc1NzUlNDTS8iLCJ1aW4iOiI5NTg2MzAxNDYifQ%3D%3D&data=8nepSQv0_dZIm_ZCWW-lPMXP8xlcFXyNWUolkq1DvckJaLbB0JYLVwmuOfmH0Z7mKXGgRx6yhwpi9bjWCvi66Q&svctype=4&tempid=h5_group_info)
- **Discord 社区**：[![Discord](https://camo.githubusercontent.com/102e5e8c137384dab2598b6b750c435e234481fc62812b0e3bc55fe881abfa95/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f446973636f72642d4a6f696e2d3538363546323f7374796c653d666c61742d737175617265266c6f676f3d646973636f7264266c6f676f436f6c6f723d7768697465)](https://discord.gg/4mSg4VyxBN)
- **免责声明与使用条款**：[点击跳转](https://docs.akihito.dpdns.org/webshopx-payments/overview#%E5%85%8D%E8%B4%A3%E5%A3%B0%E6%98%8E%E4%B8%8E%E4%BD%BF%E7%94%A8%E6%9D%A1%E6%AC%BE)

