package com.zestindia.productapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ItemRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "quantity must be zero or greater")
    private Integer quantity;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
