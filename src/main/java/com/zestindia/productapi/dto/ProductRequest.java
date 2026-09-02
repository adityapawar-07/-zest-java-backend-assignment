package com.zestindia.productapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProductRequest {

    @NotBlank(message = "productName is required")
    @Size(max = 255, message = "productName must be at most 255 characters")
    private String productName;

    @NotBlank(message = "createdBy is required")
    @Size(max = 100, message = "createdBy must be at most 100 characters")
    private String createdBy;

    @Size(max = 100, message = "modifiedBy must be at most 100 characters")
    private String modifiedBy;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }
}
