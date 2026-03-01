package com.stocat.match.api.exception;

import com.stocat.match.exception.ErrorCode;
import com.stocat.match.exception.ErrorDomain;

public enum MatchErrorCode implements ErrorCode {
    SYMBOL_MISMATCH(ErrorDomain.MATCH_API.offset() + 1, "종목 불일치입니다."),
    SYMBOL_NOT_REGISTERED(ErrorDomain.MATCH_API.offset() + 2, "등록되지 않은 종목입니다."),
    ORDER_CANCEL_FAILED(ErrorDomain.MATCH_API.offset() + 3, "주문 취소에 실패했습니다.");

    private final int code;
    private final String message;

    MatchErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}