package com.arka.r2dbc.orderstatehistory;

import com.arka.model.order.OrderStateHistory;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OrderStateHistoryDTOMapper {

    static OrderStateHistory toDomain(OrderStateHistoryDTO data) {
        return OrderStateHistory.builder()
                .id(data.id())
                .orderId(data.orderId())
                .previousStatus(data.previousStatus())
                .newStatus(data.newStatus())
                .changedBy(data.changedBy())
                .reason(data.reason())
                .createdAt(data.createdAt())
                .build();
    }

    static OrderStateHistoryDTO toDTO(OrderStateHistory domain) {
        return OrderStateHistoryDTO.builder()
                .id(domain.id())
                .orderId(domain.orderId())
                .previousStatus(domain.previousStatus())
                .newStatus(domain.newStatus())
                .changedBy(domain.changedBy())
                .reason(domain.reason())
                .createdAt(domain.createdAt())
                .build();
    }
}
