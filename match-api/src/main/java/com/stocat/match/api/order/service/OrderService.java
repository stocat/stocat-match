package com.stocat.match.api.order.service;

import com.stocat.match.api.engine.StockSessionManager;
import com.stocat.match.api.exception.MatchErrorCode;
import com.stocat.match.domain.order.Order;
import com.stocat.match.domain.order.OrderRepository;
import com.stocat.match.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final StockSessionManager stockSessionManager;

    public void addOrder(Order order) {
        if (!stockSessionManager.isSymbolRegistered(order.symbol())) {
            throw new ApiException(MatchErrorCode.SYMBOL_NOT_REGISTERED,
                    Map.of("symbol", order.symbol()));
        }

        orderRepository.addOrder(order)
                .subscribe();
    }
}
