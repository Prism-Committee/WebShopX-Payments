package com.webshopx.payments.backend;

import com.webshopx.payments.api.LocalPaymentClient;
import com.webshopx.payments.backend.data.LocalClientInfo;
import com.webshopx.payments.backend.logger.LoggerAdapter;
import com.webshopx.payments.func.PaymentAPI;

import java.io.File;

public class BukkitMain extends CommonMain<LocalClientInfo, PluginPaymentServer> {
    private final PluginPaymentServer server;
    private LocalPaymentClient client;
    private final java.util.logging.Logger julLogger;

    public BukkitMain(java.util.logging.Logger logger, File dataFolder) {
        super(new LoggerAdapter(logger), dataFolder);
        this.julLogger = logger;
        reloadConfig();
        this.server = new PluginPaymentServer(this, getLogger());
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (config != null) {
            julLogger.setLevel(config.resolveLogLevel());
        }
    }

    public void beforePluginReloadConfig() {
        reloadConfig();
        server.restart();
        if (client == null) {
            client = new LocalPaymentClient(this, PaymentAPI.inst());
        }
    }

    public LocalPaymentClient getClient() {
        return client;
    }

    @Override
    public PluginPaymentServer getServer() {
        return server;
    }
}
