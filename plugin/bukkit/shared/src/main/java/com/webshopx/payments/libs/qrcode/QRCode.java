package com.webshopx.payments.libs.qrcode;

import com.webshopx.payments.libs.qrcode.enums.ErrorCorrectionLevel;
import com.webshopx.payments.libs.qrcode.enums.Mode;

/**
 * @deprecated 请导入依赖 <code>top.mrxiaom:qrcode-encoder:1.0.0</code> 并使用其中的 <code>QRCode</code> 类代替
 * @see top.mrxiaom.qrcode.QRCode
 */
@Deprecated
public class QRCode {
    public final top.mrxiaom.qrcode.QRCode impl;

    @Deprecated
    public QRCode() {
        this(new top.mrxiaom.qrcode.QRCode());
    }

    @Deprecated
    private QRCode(top.mrxiaom.qrcode.QRCode impl) {
        this.impl = impl;
    }

    @Deprecated
    public int getTypeNumber() {
        return impl.getTypeNumber();
    }

    @Deprecated
    public void setTypeNumber(int typeNumber) {
        impl.setTypeNumber(typeNumber);
    }

    @Deprecated
    public ErrorCorrectionLevel getErrorCorrectionLevel() {
        return ErrorCorrectionLevel.from(impl.getErrorCorrectionLevel());
    }

    @Deprecated
    public void setErrorCorrectionLevel(ErrorCorrectionLevel errorCorrectionLevel) {
        impl.setErrorCorrectionLevel(errorCorrectionLevel.impl);
    }

    @Deprecated
    public void addData(String data) {
        impl.addData(data);
    }

    @Deprecated
    public void addData(String data, Mode mode) {
        impl.addData(data, mode.impl);
    }

    @Deprecated
    public void clearData() {
        impl.clearData();
    }

    @Deprecated
    public boolean isDark(int row, int col) {
        return impl.isDark(row, col);
    }

    @Deprecated
    public int getModuleCount() {
        return impl.getModuleCount();
    }

    @Deprecated
    public void make() {
        impl.make();
    }

    @Deprecated
    public static QRCode create(String data, ErrorCorrectionLevel errorCorrectionLevel) {
        top.mrxiaom.qrcode.QRCode impl = top.mrxiaom.qrcode.QRCode.create(data, errorCorrectionLevel.impl);
        return new QRCode(impl);
    }

    @Deprecated
    public static void set8BitByteEncoding(final String _8BitByteEncoding) {
        top.mrxiaom.qrcode.QRCode.set8BitByteEncoding(_8BitByteEncoding);
    }

    @Deprecated
    public static String get8BitByteEncoding() {
        return top.mrxiaom.qrcode.QRCode.get8BitByteEncoding();
    }
}
