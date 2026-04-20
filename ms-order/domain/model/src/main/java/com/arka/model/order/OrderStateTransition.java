package com.arka.model.order;

import java.util.Map;
import java.util.Set;

public final class OrderStateTransition {

    private OrderStateTransition() {}

    // PENDIENTE_RESERVA → CONFIRMADO | CANCELADO
    // CONFIRMADO        → EN_DESPACHO | CANCELADO
    // EN_DESPACHO       → ENTREGADO
    // ENTREGADO, CANCELADO: terminal (no outgoing)
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
        "PENDIENTE_RESERVA", Set.of("CONFIRMADO", "CANCELADO"),
        "CONFIRMADO",        Set.of("EN_DESPACHO", "CANCELADO"),
        "EN_DESPACHO",       Set.of("ENTREGADO")
    );

    private static final Set<String> TERMINAL_STATES = Set.of("ENTREGADO", "CANCELADO");

    public static boolean isValidTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) return false;
        return VALID_TRANSITIONS.getOrDefault(from.value(), Set.of()).contains(to.value());
    }

    public static boolean isTerminal(OrderStatus status) {
        if (status == null) return false;
        return TERMINAL_STATES.contains(status.value());
    }
}
