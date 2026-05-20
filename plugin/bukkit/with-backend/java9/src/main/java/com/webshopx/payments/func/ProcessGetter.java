package com.webshopx.payments.func;

import top.mrxiaom.pluginbase.func.AutoRegister;
import com.webshopx.payments.PluginCommon;
import com.webshopx.payments.WsxPay;
import com.webshopx.payments.backend.BukkitMain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AutoRegister
public class ProcessGetter extends AbstractModule {
    public ProcessGetter(PluginCommon plugin) {
        super(plugin);
        BukkitMain backend = ((WsxPay) plugin).getBackend();
        backend.getServer().setJava9ProcessGetter(() -> {
            List<String> list = new ArrayList<>();
            ProcessHandle.allProcesses().forEach(it -> {
                Optional<String> command = it.info().command();
                command.ifPresent(list::add);
            });
            return list;
        });
    }
}
