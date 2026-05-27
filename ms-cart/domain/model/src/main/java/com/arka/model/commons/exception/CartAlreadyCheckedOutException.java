package com.arka.model.commons.exception;

public class CartAlreadyCheckedOutException extends DomainException {

    public CartAlreadyCheckedOutException(String cartId) {
        super("Cart already checked out: " + cartId);
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "CART_ALREADY_CHECKED_OUT";
    }
}
