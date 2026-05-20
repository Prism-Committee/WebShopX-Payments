package com.webshopx.payments.func;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import com.webshopx.payments.map.IMapSource;
import com.webshopx.payments.packets.common.IPacket;
import com.webshopx.payments.packets.common.IResponsePacket;

import java.util.function.Consumer;

/**
 * 这个接口仅供内部使用，随时可能会修改
 */
@ApiStatus.Internal
public interface IPaymentManager {
    void putProcess(Player player, String tag);
    boolean isProcess(Player player);
    PaymentSession remove(Player player);

    <R extends IResponsePacket> void sendPacket(Player player, IPacket<R> packet, Consumer<R> resp);

    default void requireScan(Player player, IMapSource source, String orderId, long outdateTime, Consumer<Double> done) {
        byte[] colors = source.generate(this);
        requireScan(player, colors, orderId, outdateTime, done);
    }
    void requireScan(Player player, byte[] colors, String orderId, long outdateTime, Consumer<Double> done);

    byte getMapDarkColor();
    byte getMapLightColor();
    byte[] getMapDarkPattern();
    byte[] getMapLightPattern();

}
