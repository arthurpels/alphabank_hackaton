package com.alphabank.backend.enums;

public enum TransitionInitiator {
    CLIENT, UNDERWRITER, SYSTEM;

    public static TransitionInitiator of(UserRole role) {
        return role == UserRole.UNDERWRITER ? UNDERWRITER : CLIENT;
    }
}
