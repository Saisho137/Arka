package com.arka.model.commons.exception;

public class CartNotFoundException extends DomainException {

    public CartNotFoundException(String cartId) {
        super("Cart not found: " + cartId);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "CART_NOT_FOUND";
    }
}
