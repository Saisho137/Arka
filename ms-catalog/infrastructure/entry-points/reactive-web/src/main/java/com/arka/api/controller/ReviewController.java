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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final ProductHandler productHandler;

    @PostMapping
    @Operation(summary = "Add a review to a product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Mono<ResponseEntity<ProductResponse>> addReview(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody AddReviewRequest request) {
        return productHandler.addReview(productId, request);
    }
}
