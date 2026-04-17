package com.arka.model.commons.gateways;

@FunctionalInterface
public interface JsonSerializer {
    String toJson(Object object);
}
