package com.webshopx.payments;

import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.mrxiaom.pluginbase.BukkitPlugin;
import top.mrxiaom.pluginbase.func.LanguageManager;
import top.mrxiaom.pluginbase.paper.PaperFactory;
import top.mrxiaom.pluginbase.resolver.DefaultLibraryResolver;
import top.mrxiaom.pluginbase.utils.ClassLoaderWrapper;
import top.mrxiaom.pluginbase.utils.Util;
import top.mrxiaom.pluginbase.utils.inventory.InventoryFactory;
import top.mrxiaom.pluginbase.utils.item.ItemEditor;
import top.mrxiaom.pluginbase.utils.scheduler.FoliaLibScheduler;
import com.webshopx.payments.api.PaymentClient;
import com.webshopx.payments.api.PaymentEventBridge;
import com.webshopx.payments.func.PaymentAPI;
import com.webshopx.payments.nms.NMS;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

public abstract class PluginCommon extends BukkitPlugin {
    public boolean processingLogs = true;
    private PaymentEventBridge paymentEventBridge;
    private Object wsxPaymentService;

    public static PluginCommon getInstance() {
        return (PluginCommon) BukkitPlugin.getInstance();
    }

    public PluginCommon() throws Exception {
        super(options()
                .bungee(false)
                .adventure(true)
                .database(false)
                .reconnectDatabaseWhenReloadConfig(false)
                .scanIgnore("com.webshopx.payments.libs")
        );
        scheduler = new FoliaLibScheduler(this);

        info("Checking dependency libraries");
        File librariesDir = ClassLoaderWrapper.isSupportLibraryLoader
                ? new File("libraries")
                : new File(this.getDataFolder(), "libraries");
        DefaultLibraryResolver resolver = new DefaultLibraryResolver(getLogger(), librariesDir);

        resolver.addResolvedLibrary(BuildConstants.RESOLVED_LIBRARIES);
        if (Util.isPresent("com.webshopx.payments.backend.BukkitMain")) {
            if (!Util.isPresent("org.apache.commons.io.FileUtils")) {
                resolver.addResolvedLibrary("commons-io/commons-io/2.17.0/commons-io-2.17.0.jar");
            }
        }

        List<URL> libraries = resolver.doResolve();
        info("Adding " + libraries.size() + " dependency libraries to class loader");
        for (URL library : libraries) {
            this.classLoader.addURL(library);
        }
    }

    @Override
    public @NotNull ItemEditor initItemEditor() {
        return PaperFactory.createItemEditor();
    }

    @Override
    public @NotNull InventoryFactory initInventoryFactory() {
        return PaperFactory.createInventoryFactory();
    }

    @Nullable
    public PaymentEventBridge getPaymentEventBridge() {
        return paymentEventBridge;
    }

    public abstract PaymentClient handlePaymentReload(PaymentAPI parent, @Nullable String url) throws URISyntaxException;

    @Override
    public Class<?> getConstructorType() {
        return PluginCommon.class;
    }

    @Override
    protected void beforeLoad() {
        MinecraftVersion.replaceLogger(getLogger());
        MinecraftVersion.disableUpdateCheck();
        MinecraftVersion.disableBStats();
        MinecraftVersion.getVersion();
    }

    @Override
    protected void beforeEnable() {
        if (!NMS.init()) {
            throw new IllegalStateException("Unsupported game version " + MinecraftVersion.getVersion().name());
        }
        if (NMS.isUnknownVersion()) {
            warn("The server version is not explicitly supported; trying the latest compatible implementation.");
        }
        LanguageManager.inst()
                .setLangFile("messages.yml")
                .register(Messages.class, Messages::holder)
                .register(Errors.class, Errors::holder)
                .register(CancelReasons.class, CancelReasons::holder);
    }

    @Override
    protected void afterEnable() {
        getLogger().info("WebShopX-Payments loaded");
        registerWsxPaymentApi();
    }

    @Override
    protected void afterDisable() {
        unregisterWsxPaymentApi();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerWsxPaymentApi() {
        if (wsxPaymentService != null) return;
        try {
            Class apiClass = Class.forName("com.webshopx.payment.api.WebShopXPaymentApi");
            Object service = Class.forName("com.webshopx.payments.WsxPaymentApi")
                    .getConstructor(PluginCommon.class)
                    .newInstance(this);
            if (!(service instanceof PaymentEventBridge)) {
                throw new IllegalStateException("WsxPaymentApi does not implement PaymentEventBridge");
            }
            getServer().getServicesManager().register(apiClass, service, this, ServicePriority.Normal);
            wsxPaymentService = service;
            paymentEventBridge = (PaymentEventBridge) service;
            info("Registered WebShopXPaymentApi provider: webshopx-payments");
        } catch (ClassNotFoundException ignored) {
            info("WebShopXPaymentApi not found; skipped WSXPay provider registration");
        } catch (Throwable t) {
            warn("Failed to register WebShopXPaymentApi provider", t);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void unregisterWsxPaymentApi() {
        Object service = wsxPaymentService;
        if (service == null) return;
        try {
            Class apiClass = Class.forName("com.webshopx.payment.api.WebShopXPaymentApi");
            getServer().getServicesManager().unregister(apiClass, service);
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            warn("Failed to unregister WebShopXPaymentApi provider", t);
        }
        if (paymentEventBridge != null) {
            paymentEventBridge.close();
        }
        paymentEventBridge = null;
        wsxPaymentService = null;
    }
}
