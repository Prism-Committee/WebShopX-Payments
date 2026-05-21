package com.webshopx.payments.backend.data;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class ClientInfo<C extends ClientInfo<C>> {
    public static class Order<C extends ClientInfo<C>> {
        C client;
        String id;
        String type;
        String playerName;
        String money;
        String currency;
        TimerTask task = null;
        Runnable cancelAction = null;

        private Order(C client, String id, String type, String playerName, String money, String currency) {
            this.client = client;
            this.id = id;
            this.type = type;
            this.playerName = playerName;
            this.money = money;
            this.currency = currency;
        }

        public TimerTask getTask() {
            return task;
        }

        public void setTask(TimerTask task) {
            this.task = task;
        }

        public Runnable getCancelAction() {
            return cancelAction;
        }

        public void setCancelAction(Runnable cancelAction) {
            this.cancelAction = cancelAction;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getMoney() {
            return money;
        }

        public String getCurrency() {
            return currency;
        }

        public C getClient() {
            return client;
        }

        public void remove() {
            getClient().removeOrder(this);
        }
    }

    private final Map<String, Order<C>> orders = new HashMap<>();
    private static final Set<String> locked = new HashSet<>();
    private final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public abstract boolean isOpen();

    @SuppressWarnings({"unchecked"})
    protected C $this() {
        return (C) this;
    }

    public Order<C> createOrder(String id, String type, String playerName, String money, String currency) {
        Order<C> order = new Order<>($this(), id, type, playerName, money, currency);
        addOrder(order);
        return order;
    }

    public String nextOrderId() {
        String id;
        LocalDateTime time = LocalDateTime.now();
        String base = time.format(format);
        int i = 1;
        do {
            id = String.format("%s%03d", base, i++);
        } while ((orders.containsKey(id) || locked.contains(id)) && i <= 999);
        if (i >= 1000) return null;
        locked.add(id);
        return id;
    }

    public boolean addOrder(Order<C> order) {
        if (orders.containsKey(order.id)) return false;
        orders.put(order.id, order);
        return true;
    }

    @Nullable
    public Order<C> getOrder(String orderId) {
        return orders.get(orderId);
    }

    @Nullable
    public Order<C> getOrderByPlayer(String playerName) {
        for (Order<C> order : orders.values()) {
            if (order.getPlayerName().equals(playerName)) {
                return order;
            }
        }
        return null;
    }

    @Nullable
    public Order<C> removeOrder(Order<C> order) {
        TimerTask task = order.getTask();
        if (task != null) {
            order.setTask(null);
            task.cancel();
        }
        return removeOrder(order.id);
    }

    @Nullable
    public Order<C> removeOrder(String orderId) {
        Order<C> order = orders.remove(orderId);
        if (order != null) {
            TimerTask task = order.getTask();
            if (task != null) {
                task.cancel();
            }
            locked.remove(orderId);
        }
        return order;
    }
}
