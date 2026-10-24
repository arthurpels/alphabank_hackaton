package com.alphabank.backend.service.impl;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.DemoTimeDto;
import com.alphabank.backend.service.DemoTimeService;
import com.alphabank.backend.service.InfoRequestTimeoutService;
import com.alphabank.backend.util.DemoClock;

@Service
public class DemoTimeServiceImpl implements DemoTimeService {

    private final DemoClock clock;
    private final InfoRequestTimeoutService timeoutService;

    public DemoTimeServiceImpl(DemoClock clock, InfoRequestTimeoutService timeoutService) {
        this.clock = clock;
        this.timeoutService = timeoutService;
    }

    @Override
    public DemoTimeDto now() {
        return time(List.of());
    }

    @Override
    public DemoTimeDto advance(int days) {
        clock.advance(Duration.ofDays(days));
        return time(timeoutService.cancelExpired());
    }

    @Override
    public DemoTimeDto reset() {
        clock.reset();
        return time(List.of());
    }

    private DemoTimeDto time(List<UUID> cancelled) {
        return new DemoTimeDto(OffsetDateTime.now(clock), clock.offset().toDays(), cancelled);
    }
}
