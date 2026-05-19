package top.mrxiaom.sweet.checkout.backend;

import com.alipay.api.AlipayConfig;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.wechat.pay.api.WXPay;
import com.wechat.pay.utils.WXPayUtility;
import io.github.eealba.payper.core.client.PayperAuthenticator;
import io.github.eealba.payper.core.client.PayperConfig;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.mrxiaom.sweet.checkout.backend.util.NullAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public class Configuration {
    private static Logger logger = LoggerFactory.getLogger(Configuration.class);
    @SerializedName("debug")
    private boolean debug = false;
    @SerializedName("port")
    private int port = 62233;
    @SerializedName("proxy")
    private ProxySettings proxy = ProxySettings.defaults();
    @SerializedName("wechat_native")
    private WeChatNative weChatNative = new WeChatNative();
    @SerializedName("alipay_face2face")
    private AlipayFaceToFace alipayFaceToFace = new AlipayFaceToFace();
    @SerializedName("paypal")
    private Paypal paypal = new Paypal();
    @SerializedName("hook")
    private Hook hook = new Hook();

    /**
     * 从配置或者文件读取字符串
     *
     * @param logger     日志记录器，用于警告
     * @param dataFolder 数据文件夹
     * @param name       配置名，用于警告
     * @param str        输入的字符串
     * @return 如果输入的字符串以 <code>file:</code> 开头，则读取文件内容，反之返回输入的字符串
     */
    private static String parseString(Logger logger, File dataFolder, String name, String str) {
        if (!str.startsWith("file:")) return str;
        String path = str.substring(5);
        File file = new File(dataFolder, path);
        if (!file.exists()) {
            logger.warn("无法从配置 {} 找到对应文件 {}", name, path);
            return null;
        }
        try {
            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            String log = "读取文件 " + path + " 时出现一个异常";
            logger.warn(log, e);
            return null;
        }
    }

    protected void postLoad(File dataFolder) {
        getWeChatNative().postLoad(dataFolder);
        getAlipayFaceToFace().postLoad(dataFolder);
        getPaypal().postLoad(dataFolder);
        getHook().postLoad(dataFolder);
    }

    public boolean isDebug() {
        return debug;
    }

    public int getPort() {
        return port;
    }

    public ProxySettings getProxy() {
        return proxy;
    }

    public ProxySettings resolveProxy(ProxySettings override) {
        return ProxySettings.resolve(proxy, override);
    }

    public WeChatNative getWeChatNative() {
        return weChatNative;
    }

    public AlipayFaceToFace getAlipayFaceToFace() {
        return alipayFaceToFace;
    }

    public Paypal getPaypal() {
        return paypal;
    }

    public Hook getHook() {
        return hook;
    }

    private static AlipayConfig initAlipayConfig(String appId, String privateKey, String publicKey) {
        AlipayConfig alipayConfig = new AlipayConfig();
        alipayConfig.setServerUrl("https://openapi.alipay.com/gateway.do");
        alipayConfig.setAppId(appId);
        alipayConfig.setPrivateKey(privateKey);
        alipayConfig.setFormat("json");
        alipayConfig.setAlipayPublicKey(publicKey);
        alipayConfig.setCharset("UTF-8");
        alipayConfig.setSignType("RSA2");
        return alipayConfig;
    }

    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class ProxySettings {
        private Boolean enable;
        private String type;
        private String host;
        private Integer port;
        private String username;
        private String password;

        private static ProxySettings defaults() {
            ProxySettings settings = new ProxySettings();
            settings.enable = false;
            settings.type = "HTTP";
            settings.host = "127.0.0.1";
            settings.port = 7890;
            settings.username = "";
            settings.password = "";
            return settings;
        }

        private static ProxySettings resolve(ProxySettings base, ProxySettings override) {
            ProxySettings defaults = defaults();
            ProxySettings resolved = new ProxySettings();
            resolved.enable = firstBoolean(override == null ? null : override.enable, base == null ? null : base.enable, defaults.enable);
            resolved.type = firstString(override == null ? null : override.type, base == null ? null : base.type, defaults.type);
            resolved.host = firstString(override == null ? null : override.host, base == null ? null : base.host, defaults.host);
            resolved.port = firstInteger(override == null ? null : override.port, base == null ? null : base.port, defaults.port);
            resolved.username = firstString(override == null ? null : override.username, base == null ? null : base.username, defaults.username);
            resolved.password = firstString(override == null ? null : override.password, base == null ? null : base.password, defaults.password);
            return resolved;
        }

        private static Boolean firstBoolean(Boolean... values) {
            for (Boolean value : values) {
                if (value != null) return value;
            }
            return false;
        }

        private static Integer firstInteger(Integer... values) {
            for (Integer value : values) {
                if (value != null) return value;
            }
            return 0;
        }

        private static String firstString(String... values) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) return value;
            }
            return "";
        }

        public boolean isEnable() {
            return Boolean.TRUE.equals(enable);
        }

        public String getType() {
            return type == null ? "HTTP" : type;
        }

        public String getHost() {
            return host == null ? "" : host;
        }

        public int getPort() {
            return port == null ? 0 : port;
        }

        public String getUsername() {
            return username == null ? "" : username;
        }

        public String getPassword() {
            return password == null ? "" : password;
        }
    }

    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class WeChatNative {
        private boolean enable = false;
        @SerializedName("host")
        private String host = "https://api.mch.weixin.qq.com";
        @SerializedName("app_id")
        private String appId = "开发者ID";
        @SerializedName("merchant_id")
        private String merchantId = "商户号";
        @SerializedName("merchant_serial_number")
        private String merchantSerialNumber = "商户证书序列号";
        @SerializedName("notify_url")
        private String notifyUrl = "https://mcio.dev/consumer/notify";
        @SerializedName("private_key")
        private String privateKey = "file:secrets/wechat/apiclient_key.pem";
        @SerializedName("public_key")
        private String publicKey = "file:secrets/wechat/pub_key.pem";
        @SerializedName("public_key_id")
        private String publicKeyId = "公钥ID";
        @SerializedName("proxy")
        private ProxySettings proxy;

        @JsonAdapter(NullAdapter.class)
        @Expose(serialize = false, deserialize = false)
        private WXPay config;

        private void postLoad(File dataFolder) {
            if (isEnable()) {
                String privateKey = parseString(logger, dataFolder, "wechat_native.private_key", this.privateKey);
                String publicKey = parseString(logger, dataFolder, "wechat_native.public_key", this.publicKey);
                if (privateKey == null || publicKey == null) {
                    this.enable = false;
                    this.config = null;
                    return;
                }

                this.config = new WXPay(
                        host, appId, merchantId,
                        merchantSerialNumber,
                        WXPayUtility.loadPrivateKeyFromString(privateKey),
                        publicKeyId,
                        WXPayUtility.loadPublicKeyFromString(publicKey)
                );
            } else {
                this.config = null;
            }
        }

        public boolean isEnable() {
            return enable;
        }

        public String getNotifyUrl() {
            return notifyUrl.trim().isEmpty() ? null : notifyUrl;
        }

        public WXPay getConfig() {
            return config;
        }

        public ProxySettings getProxy() {
            return proxy;
        }
    }

    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class AlipayFaceToFace {
        private boolean enable = false;
        @SerializedName("app_id")
        private String appId = "";
        @SerializedName("private_key")
        private String privateKey = "file:secrets/alipay/private.txt";
        @SerializedName("alipay_public_key")
        private String alipayPublicKey = "file:secrets/alipay/public.txt";
        @SerializedName("produce_code")
        private String produceCode = "QR_CODE_OFFLINE";
        @SerializedName("use_basic_polling_mode")
        private boolean useBasicPollingMode = false;
        @SerializedName("seller_id")
        private String sellerId = "";
        @SerializedName("proxy")
        private ProxySettings proxy;

        @JsonAdapter(NullAdapter.class)
        @Expose(serialize = false, deserialize = false)
        private AlipayConfig config;

        private void postLoad(File dataFolder) {
            if (isEnable()) {
                String privateKeyStr = getPrivateKey();
                String publicKeyStr = getAlipayPublicKey();
                String privateKey = parseString(logger, dataFolder, "alipay_face2face.private_key", privateKeyStr);
                String publicKey = parseString(logger, dataFolder, "alipay_face2face.alipay_public_key", publicKeyStr);
                if (privateKey == null || publicKey == null) {
                    this.enable = false;
                    this.config = null;
                    return;
                }
                this.config = initAlipayConfig(getAppId(), privateKey, publicKey);
            } else {
                this.config = null;
            }
        }

        public boolean isEnable() {
            return enable;
        }

        public String getAppId() {
            return appId;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public String getAlipayPublicKey() {
            return alipayPublicKey;
        }

        public String getProduceCode() {
            return produceCode;
        }

        public boolean isUseBasicPollingMode() {
            return useBasicPollingMode;
        }

        public String getSellerId() {
            return sellerId;
        }

        public AlipayConfig getConfig() {
            return config;
        }

        public ProxySettings getProxy() {
            return proxy;
        }
    }

    public static class Paypal {
        private boolean enable = false;
        @SerializedName("host")
        private String host = "https://api-m.paypal.com";
        @SerializedName("client_id")
        private String clientId = "";
        @SerializedName("client_secret")
        private String clientSecret = "";
        @SerializedName("currency")
        private String currency = "USD";
        @SerializedName("proxy")
        private ProxySettings proxy;

        @JsonAdapter(NullAdapter.class)
        @Expose(serialize = false, deserialize = false)
        private PayperConfig config;

        private void postLoad(File dataFolder) {
            if (isEnable()) {
                if (clientId.trim().isEmpty() || clientSecret.trim().isEmpty()) {
                    this.enable = false;
                    this.config = null;
                    return;
                }
                PayperAuthenticator auth = PayperAuthenticator.PayperAuthenticators
                        .of(() -> host, () -> clientId.toCharArray(), () -> clientSecret.toCharArray());
                this.config = PayperConfig.builder().authenticator(auth).build();
            } else {
                this.config = null;
            }
        }

        public boolean isEnable() {
            return enable;
        }

        public PayperConfig getConfig() {
            return config;
        }

        public String getCurrency() {
            return currency == null || currency.trim().isEmpty() ? "USD" : currency.trim().toUpperCase();
        }

        public ProxySettings getProxy() {
            return proxy;
        }
    }

    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class Hook {
        private boolean enable = true;
        @SerializedName("end_point")
        private String endPoint = "/api/hook/receive";
        @SerializedName("wechat")
        private WeChatHook weChat = new WeChatHook();
        @SerializedName("alipay")
        private AlipayHook alipay = new AlipayHook();

        public boolean isEnable() {
            return enable;
        }

        public String getEndPoint() {
            return endPoint;
        }

        public WeChatHook getWeChat() {
            return weChat;
        }

        public AlipayHook getAlipay() {
            return alipay;
        }

        private void postLoad(File dataFolder) {
            getWeChat().postLoad(dataFolder);
            getAlipay().postLoad(dataFolder);
        }
    }

    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class WeChatHook extends HookProperties {
        @SerializedName("require_process")
        private String requireProcess = "SweetCheckout.Hook.WeChat.exe";

        public String getRequireProcess() {
            return requireProcess;
        }
    }

    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class AlipayHook extends HookProperties {
        @SerializedName("app_id")
        private String appId = "";
        @SerializedName("private_key")
        private String privateKey = "file:secrets/alipay/private.txt";
        @SerializedName("alipay_public_key")
        private String alipayPublicKey = "file:secrets/alipay/public.txt";
        @SerializedName("seller_id")
        private String sellerId = "";
        @SerializedName("proxy")
        private ProxySettings proxy;

        @JsonAdapter(NullAdapter.class)
        @Expose(serialize = false, deserialize = false)
        private AlipayConfig config;

        protected void postLoad(File dataFolder) {
            if (isEnable()) {
                String privateKeyStr = getPrivateKey();
                String publicKeyStr = getAlipayPublicKey();
                String privateKey = parseString(logger, dataFolder, "alipay_face2face.private_key", privateKeyStr);
                String publicKey = parseString(logger, dataFolder, "alipay_face2face.alipay_public_key", publicKeyStr);
                if (privateKey == null || publicKey == null) {
                    this.enable = false;
                    this.config = null;
                    return;
                }
                this.config = initAlipayConfig(getAppId(), privateKey, publicKey);
            } else {
                this.config = null;
            }
        }

        public String getAppId() {
            return appId;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public String getAlipayPublicKey() {
            return alipayPublicKey;
        }

        public String getSellerId() {
            return sellerId;
        }

        public AlipayConfig getConfig() {
            return config;
        }

        public ProxySettings getProxy() {
            return proxy;
        }
    }

    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static abstract class HookProperties {
        protected boolean enable = false;
        @SerializedName("payment_url")
        private String paymentUrl = "收款码地址";
        @SerializedName("payment_urls")
        private Map<String, String> paymentUrls = new HashMap<String, String>() {{
            put("1.00", "示例，1元的收款码地址");
        }};

        protected void postLoad(File dataFolder) {}

        public boolean isEnable() {
            return enable;
        }

        public String getPaymentUrl() {
            return paymentUrl;
        }

        public Map<String, String> getPaymentUrls() {
            return paymentUrls;
        }

        public String getPaymentUrl(String price) {
            String trim = getPaymentUrls().getOrDefault(price, "").trim();
            if (trim.isEmpty() || trim.contains("示例，")) {
                return getPaymentUrl();
            } else {
                return trim;
            }
        }
    }
}
