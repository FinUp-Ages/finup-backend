package br.com.finup.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.service.CategoryService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
        // TODO: substituir pelo userId vindo do token JWT via Spring Security
        @RequestHeader("X-User-Id") UUID userId,
        @RequestBody @Valid CategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(userId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
        // TODO: substituir pelo userId vindo do token JWT via Spring Security
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID id,
        @RequestBody @Valid CategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.update(userId, id, request));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAvailable(
        // TODO: substituir pelo userId vindo do token JWT via Spring Security
        @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(categoryService.findAvailable(userId));
    }
}