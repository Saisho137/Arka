package com.arka.model.commons.exception;

public class CartItemNotFoundException extends DomainException {

    public CartItemNotFoundException(String sku) {
        super("Cart item not found: " + sku);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "CART_ITEM_NOT_FOUND";
    }
}
