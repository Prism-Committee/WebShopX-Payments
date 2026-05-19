package top.mrxiaom.sweet.checkout.backend;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import top.mrxiaom.sweet.checkout.backend.data.ClientInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;

@SuppressWarnings({"FieldMayBeFinal"})
public abstract class CommonMain<C extends ClientInfo<C>, S extends AbstractPaymentServer<C>> {
    protected Configuration config;
    protected File dataFolder;
    protected boolean running = true;
    protected final Logger logger;
    protected static final Gson gson = new GsonBuilder()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                    Expose expose = fieldAttributes.getAnnotation(Expose.class);
                    return expose != null && !expose.serialize();
                }

                @Override
                public boolean shouldSkipClass(Class<?> aClass) {
                    String name = aClass.getName();
                    return name.startsWith("com.alipay") || name.startsWith("com.wechat.pay");
                }
            })
            .setPrettyPrinting()
            .create();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public CommonMain(Logger logger, File dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public Configuration getConfig() {
        return config;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public Logger getLogger() {
        return logger;
    }

    public abstract S getServer();

    public void reloadConfig() {
        try {
            File file = new File(getDataFolder(), "config.json");
            if (file.exists()) {
                String configRaw = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                config = gson.fromJson(configRaw, Configuration.class);
            } else {
                config = new Configuration();
            }
            config.postLoad(getDataFolder());
            String configRaw = gson.toJson(config);
            FileUtils.writeStringToFile(file, configRaw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("加载配置文件时出现异常", e);
            if (config == null) {
                config = new Configuration();
                config.postLoad(getDataFolder());
            }
        }
    }
}
