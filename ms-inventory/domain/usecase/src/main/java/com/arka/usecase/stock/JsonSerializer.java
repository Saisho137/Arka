package com.arka.usecase.stock;

@FunctionalInterface
public interface JsonSerializer {
    String serialize(Object payload);
}
