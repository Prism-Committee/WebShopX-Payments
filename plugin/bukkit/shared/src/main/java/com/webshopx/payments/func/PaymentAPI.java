package com.webshopx.payments.func;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.mrxiaom.pluginbase.func.AutoRegister;
import com.webshopx.payments.PluginCommon;
import com.webshopx.payments.api.PaymentEventBridge;
import com.webshopx.payments.api.PaymentClient;
import com.webshopx.payments.packets.PacketSerializer;
import com.webshopx.payments.packets.backend.PacketBackendPaymentEvent;
import com.webshopx.payments.packets.common.IPacket;
import com.webshopx.payments.packets.common.NoResponse;

import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@AutoRegister
@SuppressWarnings({"rawtypes", "unused"})
public class PaymentAPI extends AbstractModule {
    private long echo = 0;
    @SuppressWarnings({"deprecation"}) // 兼容旧版本 gson
    private final JsonParser parser = new JsonParser();
    private final Map<Long, Consumer> responseMap = new HashMap<>();
    private final Map<Long, Consumer> directResponseMap = new HashMap<>();
    private final Map<String, Consumer> eventMap = new HashMap<>();
    private final String userAgent;
    private PaymentClient client;

    public PaymentAPI(PluginCommon plugin) {
        super(plugin);
        userAgent = "WSXPay/" + plugin.getDescription().getVersion() + " Minecraft/" + MinecraftVersion.getVersion().name();
        registerListener(PacketBackendPaymentEvent.class, this::onReceivePaymentEvent);
    }

    public String getUserAgent() {
        return userAgent;
    }

    private <T extends IPacket<NoResponse>> void registerListener(Class<T> type, Consumer<T> consumer) {
        eventMap.put(type.getName(), consumer);
    }

    public <T extends IPacket> boolean send(IPacket<T> packet) {
        return send(packet, null);
    }

    public <T extends IPacket> boolean send(IPacket<T> packet, @Nullable Consumer<T> resp) {
        return send(packet, resp, false);
    }

    public <T extends IPacket> CompletableFuture<T> sendFuture(IPacket<T> packet) {
        CompletableFuture<T> future = new CompletableFuture<>();
        boolean sent = send(packet, future::complete, true);
        if (!sent) {
            future.completeExceptionally(new IllegalStateException("backend-not-connected"));
        }
        return future;
    }

    public <T extends IPacket> boolean send(IPacket<T> packet, @Nullable Consumer<T> resp, boolean directCallback) {
        JsonObject json = PacketSerializer.serialize(packet);
        Class<T> respType = packet.getResponsePacket();
        Long echo = (respType == null || resp == null) ? null : this.echo++;
        if (echo != null) {
            json.addProperty("echo", echo);
            if (directCallback) {
                directResponseMap.put(echo, resp);
            } else {
                responseMap.put(echo, resp);
            }
        }
        if (!isConnected()) {
            warn("请求失败: 未连接到后端");
            if (echo != null) {
                directResponseMap.remove(echo);
                responseMap.remove(echo);
            }
            return false;
        }
        plugin.getScheduler().runTaskAsync(() -> client.send(json.toString()));
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    @SuppressWarnings({"deprecation"})
    public void onMessage(String s) {
        JsonObject json = parser.parse(s).getAsJsonObject();
        JsonElement echoProperty = json.get("echo");
        IPacket packet;
        try {
            packet = PacketSerializer.deserialize(json);
        } catch (Throwable t) {
            warn("接收数据包时出现错误", t);
            return;
        }
        if (packet != null) {
            if (echoProperty != null) {
                onMessage(packet, echoProperty.getAsLong());
            } else {
                onMessage(packet, null);
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    public void onMessage(@NotNull IPacket packet, @Nullable Long echo) {
        if (echo != null) {
            Consumer resp = directResponseMap.remove(echo);
            if (resp != null) try {
                resp.accept(packet);
            } catch (Throwable t) {
                warn("接收数据包时出现错误", t);
            }
            if (resp != null) return;
            resp = responseMap.remove(echo);
            if (resp != null) try {
                Consumer response = resp;
                plugin.getScheduler().runTask(() -> response.accept(packet));
            } catch (Throwable t) {
                warn("接收数据包时出现错误", t);
            }
        } else {
            Consumer consumer = eventMap.get(packet.getClass().getName());
            if (consumer != null) {
                plugin.getScheduler().runTask(() -> consumer.accept(packet));
            }
        }
    }

    private void onReceivePaymentEvent(PacketBackendPaymentEvent packet) {
        PaymentEventBridge bridge = plugin.getPaymentEventBridge();
        if (bridge != null) {
            bridge.handleBackendPaymentEvent(packet);
        }
    }

    @Override
    public void reloadConfig(MemoryConfiguration config) {
        reload(null);
    }

    private void reload(@Nullable String url) {
        try {
            client = plugin.handlePaymentReload(this, url);
        } catch (Throwable t) {
            warn("连接后端服务器时出现异常", t);
        }
    }

    @Nullable
    public PaymentClient getClient() {
        return client;
    }

    @Override
    public void onDisable() {
        if (client != null && client.isOpen()) {
            client.close();
            client = null;
        }
    }

    public static PaymentAPI inst() {
        return instanceOf(PaymentAPI.class);
    }
}
