package com.alphabank.backend.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

public class DemoClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    public DemoClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private DemoClock(Clock base, AtomicReference<Duration> offset) {
        this.base = base;
        this.offset = offset;
    }

    public void advance(Duration duration) {
        offset.accumulateAndGet(duration, Duration::plus);
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    public Duration offset() {
        return offset.get();
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new DemoClock(base.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
