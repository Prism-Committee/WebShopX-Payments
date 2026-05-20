package com.webshopx.payments;

import top.mrxiaom.pluginbase.func.language.IHolderAccessor;
import top.mrxiaom.pluginbase.func.language.Language;
import top.mrxiaom.pluginbase.func.language.LanguageEnumAutoHolder;

import java.util.List;

import static top.mrxiaom.pluginbase.func.language.LanguageEnumAutoHolder.wrap;

@Language(prefix = "cancel-reasons.")
public enum CancelReasons implements IHolderAccessor {
    unknown("%reason%"),
    // TODO: 添加各种取消原因

    ;

    CancelReasons(String defaultValue) {
        holder = wrap(this, defaultValue);
    }

    CancelReasons(String... defaultValue) {
        holder = wrap(this, defaultValue);
    }

    CancelReasons(List<String> defaultValue) {
        holder = wrap(this, defaultValue);
    }

    private final LanguageEnumAutoHolder<CancelReasons> holder;

    public LanguageEnumAutoHolder<CancelReasons> holder() {
        return holder;
    }

    public static CancelReasons fromString(String type) {
        String key = type.toLowerCase().replace(".", "__").replace("-", "_");
        for (CancelReasons error : values()) {
            if (error.name().equals(key)) {
                return error;
            }
        }
        return unknown;
    }
}
