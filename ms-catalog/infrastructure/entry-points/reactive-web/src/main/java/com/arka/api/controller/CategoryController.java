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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryHandler categoryHandler;

    @PostMapping
    @Operation(summary = "Create a new category")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Category name already exists")
    })
    public Mono<ResponseEntity<CategoryResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return categoryHandler.create(request);
    }

    @GetMapping
    @Operation(summary = "List all categories")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categories retrieved successfully")
    })
    public Flux<CategoryResponse> listCategories() {
        return categoryHandler.listAll();
    }
}
