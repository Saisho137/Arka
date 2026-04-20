package com.arka.api.controller;

import com.arka.api.dto.AddReviewRequest;
import com.arka.api.dto.ProductResponse;
import com.arka.api.handler.ProductHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST Controller for Product Review operations.
 * Follows the Controller → Handler → UseCase pattern (§4.2).
 * Controller is thin - only HTTP concerns, delegates to ProductHandler.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@Tag(name = "Reviews", description = "Product review management endpoints")
@RequiredArgsConstructor
public class ReviewController {

    private final ProductHandler productHandler;

    @PostMapping
    @Operation(summary = "Add a review to a product", description = "Adds a customer review to an existing product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Review added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Mono<ResponseEntity<ProductResponse>> addReview(
            @PathVariable UUID productId,
            @Valid @RequestBody AddReviewRequest request) {
        return productHandler.addReview(productId, request);
    }
}
