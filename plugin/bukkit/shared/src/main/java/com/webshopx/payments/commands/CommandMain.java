package com.webshopx.payments.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.mrxiaom.pluginbase.func.AutoRegister;
import top.mrxiaom.pluginbase.utils.Pair;
import com.webshopx.payments.Messages;
import com.webshopx.payments.PluginCommon;
import com.webshopx.payments.func.AbstractModule;
import com.webshopx.payments.func.PaymentAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@AutoRegister
public class CommandMain extends AbstractModule implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_ADMIN = "webshopxpayments.admin";

    public CommandMain(PluginCommon plugin) {
        super(plugin);
        registerCommand("wsxpay", this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            Messages.commands__help.tm(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(sub)) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                Messages.no_permission.tm(sender);
                return true;
            }
            plugin.reloadConfig();
            Messages.commands__reload.tm(sender);
            return true;
        }
        if ("status".equals(sub)) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                Messages.no_permission.tm(sender);
                return true;
            }
            Messages.commands__status.tm(sender,
                    Pair.of("%backend_connected%", String.valueOf(PaymentAPI.inst().isConnected())),
                    Pair.of("%provider_registered%", String.valueOf(plugin.getPaymentEventBridge() != null)));
            return true;
        }
        Messages.commands__help.tm(sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return Collections.emptyList();
        List<String> values = new ArrayList<>();
        values.add("help");
        if (sender.hasPermission(PERMISSION_ADMIN)) {
            values.add("reload");
            values.add("status");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        values.removeIf(value -> !value.startsWith(prefix));
        return values;
    }
}
