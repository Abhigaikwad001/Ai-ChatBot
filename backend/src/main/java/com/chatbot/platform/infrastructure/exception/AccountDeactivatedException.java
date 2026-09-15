package com.chatbot.platform.infrastructure.exception;

import org.springframework.security.core.AuthenticationException;

public class AccountDeactivatedException extends AuthenticationException {
    public AccountDeactivatedException(String msg) {
        super(msg);
    }
}
