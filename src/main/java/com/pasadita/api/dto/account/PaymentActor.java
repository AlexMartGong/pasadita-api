package com.pasadita.api.dto.account;

import com.pasadita.api.utils.SecurityUtils;
import org.springframework.security.core.Authentication;

/**
 * The authenticated user behind a payment operation. The employee who records, modifies or cancels a payment (and the
 * one written to its history) is always resolved from here, never from the request body.
 */
public record PaymentActor(String username, boolean admin) {

    public static PaymentActor from(Authentication authentication) {
        return new PaymentActor(authentication.getName(), SecurityUtils.isAdmin(authentication));
    }
}
