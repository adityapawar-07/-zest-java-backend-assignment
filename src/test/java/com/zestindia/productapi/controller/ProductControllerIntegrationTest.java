package com.zestindia.productapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void obtainTokens() throws Exception {
        adminToken = login("admin", "admin123");
        userToken = login("user", "user123");
    }

    private String login(String username, String password) throws Exception {
        String body = """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private int createProductAsAdmin(String name) throws Exception {
        String body = """
                {"productName": "%s", "createdBy": "admin"}
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
    }

    @Test
    @DisplayName("unauthenticated requests are rejected with 401")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("ADMIN can create a product; response echoes the submitted data and a generated id")
    void adminCanCreateProduct() throws Exception {
        String body = """
                {"productName": "Wireless Mouse", "createdBy": "admin"}
                """;

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productName").value("Wireless Mouse"))
                .andExpect(jsonPath("$.createdBy").value("admin"));
    }

    @Test
    @DisplayName("USER role gets 403 when trying to create a product (read-only role)")
    void userCannotCreateProduct() throws Exception {
        String body = """
                {"productName": "Wireless Mouse", "createdBy": "user"}
                """;

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("creating a product with a blank name returns 400 with validation details")
    void createProductValidatesInput() throws Exception {
        String body = """
                {"productName": "", "createdBy": "admin"}
                """;

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasItem(containsString("productName"))));
    }

    @Test
    @DisplayName("both ADMIN and USER can read a product by id")
    void bothRolesCanReadProduct() throws Exception {
        int id = createProductAsAdmin("Mechanical Keyboard");

        mockMvc.perform(get("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Mechanical Keyboard"));

        mockMvc.perform(get("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("fetching a non-existent product returns 404 with the standard error shape")
    void getMissingProductReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", 999_999)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    @Test
    @DisplayName("product listing is paginated")
    void listProductsIsPaginated() throws Exception {
        createProductAsAdmin("Product A");
        createProductAsAdmin("Product B");
        createProductAsAdmin("Product C");

        mockMvc.perform(get("/api/v1/products")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("ADMIN can update a product; USER gets 403 on the same endpoint")
    void updateProductRespectsRole() throws Exception {
        int id = createProductAsAdmin("Old Name");

        String updateBody = """
                {"productName": "New Name", "createdBy": "admin", "modifiedBy": "admin"}
                """;

        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("New Name"))
                .andExpect(jsonPath("$.modifiedBy").value("admin"));
    }

    @Test
    @DisplayName("ADMIN can delete a product; it then 404s on subsequent GET")
    void deleteProductRemovesIt() throws Exception {
        int id = createProductAsAdmin("Disposable Product");

        mockMvc.perform(delete("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("USER gets 403 attempting to delete a product")
    void userCannotDeleteProduct() throws Exception {
        int id = createProductAsAdmin("Protected Product");

        mockMvc.perform(delete("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN can add items to a product, and both roles can list them")
    void addAndListItems() throws Exception {
        int productId = createProductAsAdmin("Product With Items");

        String itemBody = """
                {"quantity": 25}
                """;

        mockMvc.perform(post("/api/v1/products/{id}/items", productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.quantity").value(25));

        mockMvc.perform(get("/api/v1/products/{id}/items", productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].quantity").value(25));
    }

    @Test
    @DisplayName("an invalid/garbage bearer token is treated as unauthenticated (401), not a 500")
    void invalidTokenIsRejectedGracefully() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
