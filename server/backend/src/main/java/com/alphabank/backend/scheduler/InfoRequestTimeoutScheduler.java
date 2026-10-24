package com.alphabank.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.alphabank.backend.service.InfoRequestTimeoutService;

@Component
public class InfoRequestTimeoutScheduler {

    private final InfoRequestTimeoutService timeoutService;

    public InfoRequestTimeoutScheduler(InfoRequestTimeoutService timeoutService) {
        this.timeoutService = timeoutService;
    }

    @Scheduled(fixedDelayString = "${app.info-request.check-interval}")
    public void cancelExpired() {
        timeoutService.cancelExpired();
    }
}
