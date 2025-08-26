package com.euroclear.server;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The main JAX-RS resource that defines the /liquidity endpoint for Quarkus.
 */
@Path("/liquidity")
public class LiquidityEndpoint {

    private final Random random = new Random();

    /**
     * Handles GET requests to /liquidity.
     * @param isin The ISIN code for the security (e.g., BE0000345555).
     * @param date The reference date in yyyy-MM-dd format.
     * @return A Response containing the randomly generated liquidity data as JSON.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLiquidityData(
        @QueryParam("isin") String isin,
        @QueryParam("date") String date) {

        if (isin == null || isin.trim().isEmpty() || date == null || date.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"'isin' and 'date' query parameters are required.\"}")
                .build();
        }

        LocalDate parsedDate = LocalDate.parse(date);

        // --- Generate Random Data ---
        double compositeScore = random.nextDouble() * 1;
        double turnoverScore = random.nextDouble() * 1;

        AggregatedHoldingData holdingData = new AggregatedHoldingData(
            randomValue(1_000_000, 500_000_000),
            randomValue(1_000_000, 500_000_000),
            randomValue(1_000_000, 500_000_000),
            random.nextDouble() * 100,
            randomValue(100_000, 5_000_000),
            randomValue(100_000, 5_000_000),
            randomValue(50_000, 1_000_000),
            randomValue(50_000, 1_000_000)
        );

        AggregatedTransactionData transactionData = new AggregatedTransactionData(
            new Volume(randomValue(100_000, 10_000_000)),
            new Volume(randomValue(100_000, 10_000_000)),
            new Volume(randomValue(100_000, 10_000_000)),
            random.nextInt(500) + 50,
            randomValue(10_000, 500_000),
            randomValue(100, 200),
            randomValue(5_000, 200_000),
            randomValue(100, 200),
            randomValue(100, 200)
        );

        List<Transaction> transactions = new ArrayList<>();
        int transactionCount = random.nextInt(10) + 1; // 1 to 10 transactions
        for (int i = 0; i < transactionCount; i++) {
            transactions.add(new Transaction(
                UUID.randomUUID().toString(),
                parsedDate.atStartOfDay().atOffset(ZoneOffset.UTC),
                parsedDate.plusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC),
                "Settle",
                "Settled",
                randomValue(1000, 50000),
                "Unit",
                randomValue(100_000, 5_000_000),
                "EUR",
                randomValue(100, 110),
                "CP",
                "CP"
            ));
        }

        // --- Build the Response Object ---
        LiquidityResponse responsePayload = new LiquidityResponse(
            parsedDate.toString() + "T00:00:00",
            formatDouble(compositeScore),
            formatDouble(compositeScore * 0.95),
            formatDouble(compositeScore * 1.05),
            formatDouble(turnoverScore),
            isin,
            "A", "T1", "H01.50", "N", "N",
            parsedDate.plusYears(1).toString() + "T00:00:00",
            parsedDate.plusYears(1).minusDays(1).toString() + "T00:00:00",
            parsedDate.toString() + "T00:00:00",
            parsedDate.plusYears(1).toString() + "T00:00:00",
            "ANNUA", "FIX",
            formatDouble(random.nextDouble() * 5),
            formatDouble(random.nextDouble() * 10),
            holdingData,
            transactionData,
            transactions
        );

        return Response.ok(responsePayload).build();
    }

    // Helper method to generate a random BigDecimal
    private BigDecimal randomValue(double min, double max) {
        return BigDecimal.valueOf(min + (random.nextDouble() * (max - min)))
            .setScale(4, RoundingMode.HALF_UP);
    }

    // Helper method to format a double to a string with 10 decimal places
    private String formatDouble(double value) {
        return String.format("%.10f", value);
    }
}


// --- Data Model Classes (Converted to standard JavaBeans with private fields and getters/setters) ---

