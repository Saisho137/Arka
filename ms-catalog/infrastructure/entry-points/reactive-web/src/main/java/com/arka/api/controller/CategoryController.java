package com.arka.api.controller;

import com.arka.api.dto.CategoryResponse;
import com.arka.api.dto.CreateCategoryRequest;
import com.arka.api.handler.CategoryHandler;
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

/**
 * REST Controller for Category operations.
 * Follows the Controller → Handler → UseCase pattern (§4.2).
 * Controller is thin - only HTTP concerns, delegates to CategoryHandler.
 */
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Product category management endpoints")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryHandler categoryHandler;

    @PostMapping
    @Operation(summary = "Create a new category", description = "Registers a new product category in the catalog")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Category created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Category name already exists")
    })
    public Mono<ResponseEntity<CategoryResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return categoryHandler.create(request);
    }

    @GetMapping
    @Operation(summary = "List all categories", description = "Returns all product categories")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Categories retrieved successfully")
    })
    public Flux<CategoryResponse> listCategories() {
        return categoryHandler.listAll();
    }
}
