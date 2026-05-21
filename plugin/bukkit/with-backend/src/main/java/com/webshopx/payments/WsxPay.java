package com.webshopx.payments;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import top.mrxiaom.pluginbase.BukkitPlugin;
import com.webshopx.payments.api.PaymentClient;
import com.webshopx.payments.backend.BukkitMain;
import com.webshopx.payments.backend.Configuration;
import com.webshopx.payments.func.PaymentAPI;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

public class WsxPay extends PluginCommon {
    public static WsxPay getInstance() {
        return (WsxPay) BukkitPlugin.getInstance();
    }

    public WsxPay() throws Exception {
    }

    private BukkitMain main;

    public BukkitMain getBackend() {
        return main;
    }

    @Override
    protected void beforeEnable() {
        super.beforeEnable();
        main = new BukkitMain(Logger.getLogger("WSXPay-backend"), new File(getDataFolder(), "backend"));
    }

    @Override
    protected void beforeReloadConfig(FileConfiguration config) {
        super.beforeReloadConfig(config);
        if (main != null) {
            main.beforePluginReloadConfig();
        }
    }

    @Override
    protected void afterDisable() {
        super.afterDisable();
        if (main != null) {
            main.getServer().stop();
            main = null;
        }
    }

    @Override
    public PaymentClient handlePaymentReload(PaymentAPI parent, @Nullable String url) {
        return main == null ? null : main.getClient();
    }

    @Override
    public String resolvePaymentCurrency(String method, String fallback) {
        if (main == null || method == null) {
            return super.resolvePaymentCurrency(method == null ? "" : method, fallback);
        }
        Configuration config = main.getConfig();
        String lower = method.trim().toLowerCase(Locale.ROOT);
        if ("wechat".equals(lower)) return config.getWeChatNative().getCurrency();
        if ("alipay".equals(lower)) return config.getAlipayFaceToFace().getCurrency();
        if ("paypal".equals(lower)) return config.getPaypal().getCurrency();
        if ("mercadopago".equals(lower)) return config.getMercadoPago().getCurrency();
        return super.resolvePaymentCurrency(method, fallback);
    }
}
