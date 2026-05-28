package com.arka.model.order;

import net.jqwik.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStateTransitionPropertyTest {

    private static final List<String> ALL_STATES = List.of(
            "PENDIENTE_RESERVA", "PENDIENTE_PAGO", "CONFIRMADO",
            "EN_DESPACHO", "ENTREGADO", "CANCELADO");

    private static final List<String> TERMINAL_STATES = List.of("ENTREGADO", "CANCELADO");

    @Provide
    Arbitrary<OrderStatus> allStatuses() {
        return Arbitraries.of(ALL_STATES).map(OrderStatus::fromValue);
    }

    @Provide
    Arbitrary<OrderStatus> terminalStatuses() {
        return Arbitraries.of(TERMINAL_STATES).map(OrderStatus::fromValue);
    }

    @Property
    void terminalStatesHaveNoOutgoingTransitions(
            @ForAll("terminalStatuses") OrderStatus terminal,
            @ForAll("allStatuses") OrderStatus target) {
        assertThat(OrderStateTransition.isValidTransition(terminal, target)).isFalse();
    }

    @Property
    void terminalStatesAreRecognized(@ForAll("terminalStatuses") OrderStatus terminal) {
        assertThat(OrderStateTransition.isTerminal(terminal)).isTrue();
    }

    @Property
    void nonTerminalStatesAreNotTerminal(@ForAll("allStatuses") OrderStatus status) {
        if (!TERMINAL_STATES.contains(status.value())) {
            assertThat(OrderStateTransition.isTerminal(status)).isFalse();
        }
    }

    @Property
    void fromValueRoundtrip(@ForAll("allStatuses") OrderStatus status) {
        OrderStatus roundtripped = OrderStatus.fromValue(status.value());
        assertThat(roundtripped.value()).isEqualTo(status.value());
    }

    @Property
    void noSelfTransitions(@ForAll("allStatuses") OrderStatus status) {
        assertThat(OrderStateTransition.isValidTransition(status, status)).isFalse();
    }

    @Property
    void nullInputsReturnFalse(@ForAll("allStatuses") OrderStatus status) {
        assertThat(OrderStateTransition.isValidTransition(null, status)).isFalse();
        assertThat(OrderStateTransition.isValidTransition(status, null)).isFalse();
    }
}
