package com.zestindia.productapi.controller;

import com.zestindia.productapi.dto.ItemRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import com.zestindia.productapi.dto.ItemResponse;
import com.zestindia.productapi.exception.ResourceNotFoundException;
import com.zestindia.productapi.model.Item;
import com.zestindia.productapi.model.Product;
import com.zestindia.productapi.repository.ItemRepository;
import com.zestindia.productapi.repository.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@Validated
@RequestMapping("/api/v1/products/{productId}/items")
public class ItemController {

    private final ProductRepository productRepository;
    private final ItemRepository itemRepository;

    public ItemController(ProductRepository productRepository, ItemRepository itemRepository) {
        this.productRepository = productRepository;
        this.itemRepository = itemRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ItemResponse> addItem(@PathVariable @Positive(message = "id must be positive") Integer productId,
                                                 @Valid @RequestBody ItemRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Item item = new Item();
        item.setProduct(product);
        item.setQuantity(request.getQuantity());
        Item saved = itemRepository.save(item);

        ItemResponse response = new ItemResponse(saved.getId(), productId, saved.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
