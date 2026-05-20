package com.webshopx.payments.libs.qrcode.enums;

@Deprecated
public enum Mode {
    MODE_NUMBER(top.mrxiaom.qrcode.enums.Mode.MODE_NUMBER),
    MODE_ALPHA_NUM(top.mrxiaom.qrcode.enums.Mode.MODE_ALPHA_NUM),
    MODE_8BIT_BYTE(top.mrxiaom.qrcode.enums.Mode.MODE_8BIT_BYTE),
    MODE_KANJI(top.mrxiaom.qrcode.enums.Mode.MODE_KANJI);

    public final top.mrxiaom.qrcode.enums.Mode impl;
    public final int value;
    public final int m;
    Mode(top.mrxiaom.qrcode.enums.Mode impl) {
        this.impl = impl;
        this.value = impl.value;
        this.m = impl.m;
    }

    public int getLengthInBits(int type) {
        return impl.getLengthInBits(type);
    }

    public static Mode from(top.mrxiaom.qrcode.enums.Mode impl) {
        for (Mode value : values()) {
            if (value.impl.equals(impl)) {
                return value;
            }
        }
        throw new UnsupportedOperationException("Deprecated");
    }
}
