package com.arka.model.order;

import com.arka.model.commons.exception.InvalidOrderStatusException;

public sealed interface OrderStatus permits
        OrderStatus.PendingReserve,
        OrderStatus.PendingPayment,
        OrderStatus.Confirmed,
        OrderStatus.InShipment,
        OrderStatus.Delivered,
        OrderStatus.Cancelled {

    String value();

    static OrderStatus fromValue(String value) {
        return switch (value) {
            case "PENDIENTE_RESERVA" -> new PendingReserve();
            case "PENDIENTE_PAGO"    -> new PendingPayment();
            case "CONFIRMADO"        -> new Confirmed();
            case "EN_DESPACHO"       -> new InShipment();
            case "ENTREGADO"         -> new Delivered();
            case "CANCELADO"         -> new Cancelled();
            default -> throw new InvalidOrderStatusException(
                    "Unknown order status: '" + value + "'. Valid values: PENDIENTE_RESERVA, PENDIENTE_PAGO, CONFIRMADO, EN_DESPACHO, ENTREGADO, CANCELADO");
        };
    }

    record PendingReserve() implements OrderStatus {
        @Override
        public String value() {
            return "PENDIENTE_RESERVA";
        }
    }

    record PendingPayment() implements OrderStatus {
        @Override
        public String value() {
            return "PENDIENTE_PAGO";
        }
    }

    record Confirmed() implements OrderStatus {
        @Override
        public String value() {
            return "CONFIRMADO";
        }
    }

    record InShipment() implements OrderStatus {
        @Override
        public String value() {
            return "EN_DESPACHO";
        }
    }

    record Delivered() implements OrderStatus {
        @Override
        public String value() {
            return "ENTREGADO";
        }
    }

    record Cancelled() implements OrderStatus {
        @Override
        public String value() {
            return "CANCELADO";
        }
    }
}
