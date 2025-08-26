package com.euroclear.util;

import com.euroclear.LiquidityDriveClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

public class Calculation {
    private static final Logger logger = LoggerFactory.getLogger(Calculation.class);

    public static void processingDuration(Instant startTime) {
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        long totalSeconds = duration.getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        String formattedTime = String.format("%02d:%02d", minutes, seconds);
        logger.info("Process took " + formattedTime);
    }
}
