package com.stocat.match.domain.order;

import lombok.Getter;

@Getter
public enum OrderType {
    LIMIT("지정가"), MARKET("시장가");

    private final String description;

    OrderType(String description) {
        this.description = description;
    }
}