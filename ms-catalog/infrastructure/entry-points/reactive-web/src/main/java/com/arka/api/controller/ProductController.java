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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST Controller for Product operations.
 * Follows the Controller → Handler → UseCase pattern (§4.2).
 * Controller is thin - only HTTP concerns, delegates to ProductHandler.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalog management endpoints")
@RequiredArgsConstructor
public class ProductController {

    private final ProductHandler productHandler;

    @PostMapping
    @Operation(summary = "Create a new product", description = "Registers a new product in the catalog with initial stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "SKU already exists")
    })
    public Mono<ResponseEntity<ProductResponse>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productHandler.create(request);
    }

    @GetMapping
    @Operation(summary = "List active products", description = "Returns a paginated list of active products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    })
    public Flux<ProductResponse> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productHandler.listActive(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns a single product by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Mono<ResponseEntity<ProductResponse>> getProduct(@PathVariable UUID id) {
        return productHandler.getById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Updates an existing product's information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Mono<ResponseEntity<ProductResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return productHandler.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate product", description = "Soft deletes a product by marking it as inactive")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Mono<ResponseEntity<Void>> deactivateProduct(@PathVariable UUID id) {
        return productHandler.deactivate(id);
    }
}
