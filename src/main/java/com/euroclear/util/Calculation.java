package com.euroclear.util;

import com.euroclear.LiquidityDriveClient;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class Calculation {
    private static final Logger logger = Logger.getLogger(Calculation.class);

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
