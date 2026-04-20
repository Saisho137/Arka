package com.arka.api.controller;

import com.arka.api.dto.CreateProductRequest;
import com.arka.api.dto.ProductResponse;
import com.arka.api.dto.UpdateProductRequest;
import com.arka.api.handler.ProductHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductHandler productHandler;

    @PostMapping
    @Operation(summary = "Create a new product")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "SKU already exists")
    })
    public Mono<ResponseEntity<ProductResponse>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productHandler.create(request);
    }

    @GetMapping
    @Operation(summary = "List active products")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    })
    public Flux<ProductResponse> listProducts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return productHandler.listActive(page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Mono<ResponseEntity<ProductResponse>> getProduct(@PathVariable("id") UUID id) {
        return productHandler.getById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Mono<ResponseEntity<ProductResponse>> updateProduct(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return productHandler.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Mono<ResponseEntity<Void>> deactivateProduct(@PathVariable("id") UUID id) {
        return productHandler.deactivate(id);
    }
}
