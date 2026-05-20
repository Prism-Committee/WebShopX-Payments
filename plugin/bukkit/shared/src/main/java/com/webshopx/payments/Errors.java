package com.webshopx.payments;

import top.mrxiaom.pluginbase.func.language.IHolderAccessor;
import top.mrxiaom.pluginbase.func.language.Language;
import top.mrxiaom.pluginbase.func.language.LanguageEnumAutoHolder;

import java.util.List;

import static top.mrxiaom.pluginbase.func.language.LanguageEnumAutoHolder.wrap;

@Language(prefix = "errors.")
public enum Errors implements IHolderAccessor {
    unknown("&eUnknown payment error: %type%"),
    payment__not_a_number("&eInvalid payment amount."),
    payment__already_requested("&ePlease finish the current payment first."),
    payment__type_unknown("&eUnknown payment method."),
    payment__cancel__not_found("&ePayment order not found."),
    payment__cancel__not_the_agreed_price("&eThe paid amount does not match this order."),
    payment__timeout("&ePayment order timed out."),
    payment__hook_price_locked("&ePayment system is busy, please try again later."),
    payment__can_not_create_id("&ePayment system is busy, please try again later."),
    payment__internal_error("&ePayment provider returned an internal error.");

    Errors(String defaultValue) {
        holder = wrap(this, defaultValue);
    }

    Errors(String... defaultValue) {
        holder = wrap(this, defaultValue);
    }

    Errors(List<String> defaultValue) {
        holder = wrap(this, defaultValue);
    }

    private final LanguageEnumAutoHolder<Errors> holder;

    public LanguageEnumAutoHolder<Errors> holder() {
        return holder;
    }

    public static Errors fromString(String type) {
        String key = type.toLowerCase().replace(".", "__").replace("-", "_");
        for (Errors error : values()) {
            if (error.name().equals(key)) {
                return error;
            }
        }
        return unknown;
    }
}
