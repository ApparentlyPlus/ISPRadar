package com.ispradar.backend.util.http;

import java.util.Set;

public enum HttpStatusCode {

    OK(200),
    FOUND(302),
    BAD_REQUEST(400),
    FORBIDDEN(403),
    NOT_FOUND(404),
    INTERNAL_SERVER_ERROR(500);

    private static final Set<Integer> SESSION_RESET_CODES = Set.of(
        FOUND.code,
        FORBIDDEN.code
    );

    private final int code;

    HttpStatusCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean matches(int status) {
        return this.code == status;
    }

    public static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    public static boolean isError(int status){
        return status >= 400;
    }

    public static boolean requiresSessionReset(int status) {
        return SESSION_RESET_CODES.contains(status);
    }
}