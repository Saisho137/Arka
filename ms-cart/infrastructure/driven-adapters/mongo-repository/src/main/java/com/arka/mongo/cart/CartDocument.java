package com.arka.mongo.cart;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@Document(collection = "carts")
public class CartDocument {
    @Id
    private UUID id;
    private String customerId;
    private List<CartItemDocument> items;
    private String status;
    private Instant createdAt;
    private Instant lastModifiedAt;
}
