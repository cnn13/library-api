package ru.lab15.library;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Управляемые часы для тестов: позволяют «перемотать» время и проверить просрочку. */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.now();

    public void advance(Duration d) {
        now = now.plus(d);
    }

    public void reset() {
        now = Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
