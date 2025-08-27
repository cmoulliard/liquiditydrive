package com.euroclear.util;

import org.jboss.logging.Logger;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Calculation {
    private static final Logger logger = Logger.getLogger(Calculation.class);

    public static Stream<LocalDate> eachBusinessDay(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        return start.datesUntil(end.plusDays(1))
            .filter(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY &&
                date.getDayOfWeek() != DayOfWeek.SUNDAY)
            .filter(date -> holidays == null || !holidays.contains(date));
    }

    public static void processingDuration(Instant startTime) {
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        // Get the total duration in milliseconds for higher precision
        long totalMillis = duration.toMillis();

        // Use TimeUnit for clear and readable conversions
        long minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(totalMillis) % 60;
        long millis = totalMillis % 1000;

        // Update the format to include milliseconds (mm:ss.SSS)
        String formattedTime = String.format("%02d:%02d.%03d", minutes, seconds, millis);
        logger.infof("Process took " + formattedTime);
    }
}
