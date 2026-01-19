package com.stocat.matchdomain;

import lombok.Getter;

@Getter
public enum Currency {
    KRW(AssetsCategory.KRW),
    USD(AssetsCategory.USD),
    BTC(null),
    USDT(null),
    ;

    private final AssetsCategory category;

    Currency(AssetsCategory category) {
        this.category = category;
    }
}