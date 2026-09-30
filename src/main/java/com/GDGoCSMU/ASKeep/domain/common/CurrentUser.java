package com.GDGoCSMU.ASKeep.domain.common;

import com.GDGoCSMU.ASKeep.global.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    public static Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ((LoginUser) authentication.getPrincipal()).userId();
    }
}
