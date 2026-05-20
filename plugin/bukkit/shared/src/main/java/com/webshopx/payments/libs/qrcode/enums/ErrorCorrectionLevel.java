package com.webshopx.payments.libs.qrcode.enums;

@Deprecated
public enum ErrorCorrectionLevel {
    L(top.mrxiaom.qrcode.enums.ErrorCorrectionLevel.L),
    M(top.mrxiaom.qrcode.enums.ErrorCorrectionLevel.M),
    Q(top.mrxiaom.qrcode.enums.ErrorCorrectionLevel.Q),
    H(top.mrxiaom.qrcode.enums.ErrorCorrectionLevel.H);

    public final top.mrxiaom.qrcode.enums.ErrorCorrectionLevel impl;
    public final int value;
    public final int e;
    ErrorCorrectionLevel(top.mrxiaom.qrcode.enums.ErrorCorrectionLevel impl) {
        this.impl = impl;
        this.value = impl.value;
        this.e = impl.e;
    }

    public static ErrorCorrectionLevel from(top.mrxiaom.qrcode.enums.ErrorCorrectionLevel impl) {
        for (ErrorCorrectionLevel value : values()) {
            if (value.impl == impl) {
                return value;
            }
        }
        throw new UnsupportedOperationException("Deprecated");
    }
}
