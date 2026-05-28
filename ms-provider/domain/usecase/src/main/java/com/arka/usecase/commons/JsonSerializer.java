package com.arka.usecase.commons;

@FunctionalInterface
public interface JsonSerializer {
    String serialize(Object payload);
}
