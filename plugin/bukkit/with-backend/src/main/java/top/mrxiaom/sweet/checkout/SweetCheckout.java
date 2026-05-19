package top.mrxiaom.sweet.checkout;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import top.mrxiaom.pluginbase.BukkitPlugin;
import top.mrxiaom.sweet.checkout.api.PaymentClient;
import top.mrxiaom.sweet.checkout.backend.BukkitMain;
import top.mrxiaom.sweet.checkout.func.PaymentAPI;

import java.io.File;
import java.util.logging.Logger;

public class SweetCheckout extends PluginCommon {
    public static SweetCheckout getInstance() {
        return (SweetCheckout) BukkitPlugin.getInstance();
    }

    public SweetCheckout() throws Exception {
    }

    private BukkitMain main;

    public BukkitMain getBackend() {
        return main;
    }

    @Override
    protected void beforeEnable() {
        super.beforeEnable();
        main = new BukkitMain(Logger.getLogger("SweetCheckout-backend"), new File(getDataFolder(), "backend"));
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
}
