package com.webshopx.payments;

import top.mrxiaom.pluginbase.func.language.IHolderAccessor;
import top.mrxiaom.pluginbase.func.language.Language;
import top.mrxiaom.pluginbase.func.language.LanguageEnumAutoHolder;

import java.util.List;

import static top.mrxiaom.pluginbase.func.language.LanguageEnumAutoHolder.wrap;

@Language(prefix = "messages.")
public enum Messages implements IHolderAccessor {
    not_connect("&eWebShopX-Payments is not connected to the payment backend."),
    cancelled("&ePayment cancelled: %reason%"),
    no_permission("&cYou do not have permission to use this command."),

    commands__reload("&aWebShopX-Payments configuration reloaded."),
    commands__status(
            "&eWebShopX-Payments",
            "&fBackend connected: &b%backend_connected%",
            "&fProvider registered: &b%provider_registered%"),
    commands__help(
            "&eWebShopX-Payments",
            "&f/wsxpay status &7Show backend/provider status",
            "&f/wsxpay reload &7Reload configuration");

    Messages(String defaultValue) {
        holder = wrap(this, defaultValue);
    }

    Messages(String... defaultValue) {
        holder = wrap(this, defaultValue);
    }

    Messages(List<String> defaultValue) {
        holder = wrap(this, defaultValue);
    }

    private final LanguageEnumAutoHolder<Messages> holder;

    public LanguageEnumAutoHolder<Messages> holder() {
        return holder;
    }
}
