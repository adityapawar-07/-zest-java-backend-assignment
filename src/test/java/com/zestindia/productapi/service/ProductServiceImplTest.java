package com.zestindia.productapi.service;

import com.zestindia.productapi.dto.ItemResponse;
import com.zestindia.productapi.dto.ProductRequest;
import com.zestindia.productapi.dto.ProductResponse;
import com.zestindia.productapi.exception.ResourceNotFoundException;
import com.zestindia.productapi.model.Item;
import com.zestindia.productapi.model.Product;
import com.zestindia.productapi.repository.ItemRepository;
import com.zestindia.productapi.repository.ProductRepository;
import com.zestindia.productapi.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link ProductServiceImpl}. Collaborators
 * (repositories, NotificationService) are mocked with Mockito so these
 * tests exercise only the service's own logic, with no Spring context
 * and no database involved.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1);
        product.setProductName("Wireless Mouse");
        product.setCreatedBy("admin");
        product.setCreatedOn(LocalDateTime.now());
    }

    @Nested
    @DisplayName("createProduct")
    class CreateProduct {

        @Test
        @DisplayName("saves the product and fires an async creation notification")
        void createsProductAndNotifies() {
            ProductRequest request = new ProductRequest();
            request.setProductName("Wireless Mouse");
            request.setCreatedBy("admin");

            when(productRepository.save(any(Product.class))).thenReturn(product);

            ProductResponse response = productService.createProduct(request);

            assertThat(response.getId()).isEqualTo(1);
            assertThat(response.getProductName()).isEqualTo("Wireless Mouse");
            assertThat(response.getCreatedBy()).isEqualTo("admin");

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertThat(captor.getValue().getProductName()).isEqualTo("Wireless Mouse");
            assertThat(captor.getValue().getCreatedBy()).isEqualTo("admin");

            verify(notificationService).notifyProductCreated(1, "Wireless Mouse");
        }
    }

    @Nested
    @DisplayName("getProductById")
    class GetProductById {

        @Test
        @DisplayName("returns the mapped product when it exists")
        void returnsProductWhenFound() {
            when(productRepository.findById(1)).thenReturn(Optional.of(product));

            ProductResponse response = productService.getProductById(1);

            assertThat(response.getId()).isEqualTo(1);
            assertThat(response.getProductName()).isEqualTo("Wireless Mouse");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the product is missing")
        void throwsWhenNotFound() {
            when(productRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(99))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAllProducts")
    class GetAllProducts {

        @Test
        @DisplayName("delegates to the repository and maps each page element")
        void returnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
            when(productRepository.findAll(pageable)).thenReturn(page);

            Page<ProductResponse> result = productService.getAllProducts(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getProductName()).isEqualTo("Wireless Mouse");
        }
    }

    @Nested
    @DisplayName("updateProduct")
    class UpdateProduct {

        @Test
        @DisplayName("updates name and modifiedBy, then saves")
        void updatesExistingProduct() {
            ProductRequest request = new ProductRequest();
            request.setProductName("Ergo Mouse");
            request.setCreatedBy("admin");
            request.setModifiedBy("admin");

            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.updateProduct(1, request);

            assertThat(response.getProductName()).isEqualTo("Ergo Mouse");
            assertThat(response.getModifiedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when updating a missing product")
        void throwsWhenMissing() {
            ProductRequest request = new ProductRequest();
            request.setProductName("Ergo Mouse");
            request.setCreatedBy("admin");

            when(productRepository.findById(42)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(42, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProduct {

        @Test
        @DisplayName("deletes an existing product and fires an async deletion notification")
        void deletesExistingProduct() {
            when(productRepository.findById(1)).thenReturn(Optional.of(product));

            productService.deleteProduct(1);

            verify(productRepository).delete(product);
            verify(notificationService).notifyProductDeleted(1);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when deleting a missing product")
        void throwsWhenMissing() {
            when(productRepository.findById(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(7))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).delete(any());
            verify(notificationService, never()).notifyProductDeleted(anyInt());
        }
    }

    @Nested
    @DisplayName("getItemsForProduct")
    class GetItemsForProduct {

        @Test
        @DisplayName("returns mapped items when the parent product exists")
        void returnsItemsForExistingProduct() {
            Item item = new Item();
            item.setId(5);
            item.setProduct(product);
            item.setQuantity(20);

            when(productRepository.findById(1)).thenReturn(Optional.of(product));
            when(itemRepository.findByProductId(1)).thenReturn(List.of(item));

            List<ItemResponse> items = productService.getItemsForProduct(1);

            assertThat(items).hasSize(1);
            assertThat(items.get(0).getId()).isEqualTo(5);
            assertThat(items.get(0).getProductId()).isEqualTo(1);
            assertThat(items.get(0).getQuantity()).isEqualTo(20);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the parent product is missing")
        void throwsWhenParentMissing() {
            when(productRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getItemsForProduct(404))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(itemRepository, never()).findByProductId(anyInt());
        }
    }
}
