package com.stocat.match.api.order.controller;

import java.math.BigDecimal;

public record OrderCancelResponse(BigDecimal cancelledQuantity) {
}