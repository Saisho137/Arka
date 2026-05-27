package com.arka.model.commons.exception;

public class EmptyCartException extends DomainException {

    public EmptyCartException(String cartId) {
        super("Cart is empty: " + cartId);
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }

    @Override
    public String getCode() {
        return "EMPTY_CART";
    }
}
