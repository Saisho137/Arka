package com.arka.usecase.order;

@FunctionalInterface
public interface JsonSerializer {
    String serialize(Object payload);
}
