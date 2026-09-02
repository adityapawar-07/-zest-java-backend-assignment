package com.zestindia.productapi.service;

import com.zestindia.productapi.dto.ItemResponse;
import com.zestindia.productapi.dto.ProductRequest;
import com.zestindia.productapi.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Integer id);

    Page<ProductResponse> getAllProducts(Pageable pageable);

    ProductResponse updateProduct(Integer id, ProductRequest request);

    void deleteProduct(Integer id);

    List<ItemResponse> getItemsForProduct(Integer productId);
}
