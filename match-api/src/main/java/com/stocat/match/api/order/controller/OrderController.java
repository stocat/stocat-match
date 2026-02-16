package com.stocat.match.api.order.controller;

import com.stocat.match.api.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "주문 생성")
    @ApiResponse(responseCode = "200", description = "주문 생성 성공")
    @ApiResponse(responseCode = "400", description = "유효성 검증 실패, 등록되지 않은 종목 등")
    public ResponseEntity<Void> createOrder(@Valid @RequestBody OrderRequest request) {
        orderService.addOrder(request.toOrder());
        return ResponseEntity.ok().build();
    }
}
