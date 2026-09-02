package com.zestindia.productapi.service.impl;

import com.zestindia.productapi.dto.ItemResponse;
import com.zestindia.productapi.dto.ProductRequest;
import com.zestindia.productapi.dto.ProductResponse;
import com.zestindia.productapi.exception.ResourceNotFoundException;
import com.zestindia.productapi.model.Item;
import com.zestindia.productapi.model.Product;
import com.zestindia.productapi.repository.ItemRepository;
import com.zestindia.productapi.repository.ProductRepository;
import com.zestindia.productapi.service.NotificationService;
import com.zestindia.productapi.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ItemRepository itemRepository;
    private final NotificationService notificationService;

    public ProductServiceImpl(ProductRepository productRepository, ItemRepository itemRepository,
                               NotificationService notificationService) {
        this.productRepository = productRepository;
        this.itemRepository = itemRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        product.setProductName(request.getProductName());
        product.setCreatedBy(request.getCreatedBy());

        Product saved = productRepository.save(product);
        notificationService.notifyProductCreated(saved.getId(), saved.getProductName());
        return toResponse(saved);
    }

    @Override
    public ProductResponse getProductById(Integer id) {
        Product product = findProductOrThrow(id);
        return toResponse(product);
    }

    @Override
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Integer id, ProductRequest request) {
        Product product = findProductOrThrow(id);
        product.setProductName(request.getProductName());
        if (request.getModifiedBy() != null) {
            product.setModifiedBy(request.getModifiedBy());
        }
        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProduct(Integer id) {
        Product product = findProductOrThrow(id);
        productRepository.delete(product);
        notificationService.notifyProductDeleted(id);
    }

    @Override
    public List<ItemResponse> getItemsForProduct(Integer productId) {
        // Ensure the product exists before listing its items
        findProductOrThrow(productId);

        return itemRepository.findByProductId(productId).stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());
    }

    private Product findProductOrThrow(Integer id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductName(),
                product.getCreatedBy(),
                product.getCreatedOn(),
                product.getModifiedBy(),
                product.getModifiedOn()
        );
    }

    private ItemResponse toItemResponse(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getQuantity()
        );
    }
}
