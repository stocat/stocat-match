package com.stocat.match.api.order.service;

import com.stocat.match.api.engine.StockSessionManager;
import com.stocat.match.api.exception.MatchErrorCode;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderRepository;
import com.stocat.match.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final StockSessionManager stockSessionManager;

    public Mono<Void> addOrder(Order order) {
        if (!stockSessionManager.isSymbolRegistered(order.symbol())) {
            return Mono.error(new ApiException(MatchErrorCode.SYMBOL_NOT_REGISTERED,
                    Map.of("symbol", order.symbol())));
        }

        return orderRepository.addOrder(order);
    }

    public Mono<BigDecimal> cancelOrder(Long orderId) {
        return orderRepository.remove(orderId)
                .switchIfEmpty(Mono.error(new ApiException(MatchErrorCode.ORDER_CANCEL_FAILED,
                        Map.of("orderId", orderId))));
    }
}