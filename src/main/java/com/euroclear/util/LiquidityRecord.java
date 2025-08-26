package com.euroclear.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LiquidityRecord {
    private static List<String> headerCols = new ArrayList<>();

    // Column names
    public static final String COL_REQUESTED_ISIN = "RequestedISIN";
    public static final String COL_REQUESTED_DATE = "RequestedDate";

    // CSV delimiter
    public static final char DELIM = ';';

    // Expand field candidates for transaction data
    public static final String[] EXPAND_BASE_CANDIDATES = {
        "transactions"
    };

    // Specific transaction fields to extract
    public static final String[] EXPAND_FIELDS = {
        "transactionId",
        "transactionDate",
        "settlementDate",
        "transactionType",
        "transactionStatus",
        "quantity",
        "quantityUnit",
        "cashAmount",
        "cashSettlementCurrency",
        "grossSettlementPrice",
        "receivingPartyType",
        "deliveringPartyType"
    };

    public static void populateHeaders() {
        headerCols.add(COL_REQUESTED_ISIN);
        headerCols.add(COL_REQUESTED_DATE);
        headerCols.addAll(Arrays.asList(FIXED_PATHS));
        headerCols.addAll(Arrays.stream(EXPAND_FIELDS)
            .map(f -> "transaction." + f)
            .collect(Collectors.toList()));
    }

    public static String headerLine() {
        return headerCols.stream()
            .map(Parsing::escapeCSV)
            .collect(Collectors.joining(String.valueOf(DELIM)));
    }

    public static String[] FIXED_PATHS = {
        "referenceDate",
        "compositeLiquidityScore",
        "concentrationWeightedLiquidityScore",
        "holdingScore",
        "turnoverScore",
        "isin",
        "amountType",
        "ecbTiering",
        "ecbHaircut",
        "optionFlag",
        "zeroCouponFlag",
        "couponPaymentDate",
        "couponRecordDate",
        "interestPeriodStartDate",
        "interestPeriodEndDate",
        "couponPaymentFrequency",
        "rateType",
        "couponInterestRate",
        "couponGrossAmount",

        // Holding aggregates (leafs)
        "aggregatedHoldingData.freeFloatHolding",
        "aggregatedHoldingData.freeFloatHoldingEur",
        "aggregatedHoldingData.freeFloatMarketValueEur",
        "aggregatedHoldingData.concentration",
        "aggregatedHoldingData.averageHoldingSizeEur",
        "aggregatedHoldingData.averageHoldingSizeMarketValueEur",
        "aggregatedHoldingData.medianHoldingSizeEur",
        "aggregatedHoldingData.medianHoldingSizeMarketValueEur",

        // SMA 30 (leafs)
        "aggregatedHoldingData.simpleMovingAverage['30'].concentration",
        "aggregatedHoldingData.simpleMovingAverage['30'].concentrationDelta",
        "aggregatedHoldingData.simpleMovingAverage['30'].freeFloatHolding",
        "aggregatedHoldingData.simpleMovingAverage['30'].freeFloatHoldingDelta",
        "aggregatedHoldingData.simpleMovingAverage['30'].averageHoldingSize",
        "aggregatedHoldingData.simpleMovingAverage['30'].averageHoldingSizeDelta",
        "aggregatedHoldingData.simpleMovingAverage['30'].medianHoldingSize",
        "aggregatedHoldingData.simpleMovingAverage['30'].medianHoldingSizeDelta",

        // Transaction aggregates (leafs)
        "aggregatedTransactionData.totalVolCurrentBD.volumeEur",
        "aggregatedTransactionData.totalVolNext1BD.volumeEur",
        "aggregatedTransactionData.totalVolNext2BD.volumeEur",
        "aggregatedTransactionData.transactionCount",
        "aggregatedTransactionData.averageTicketSizeEur",
        "aggregatedTransactionData.averageGrossSettlementPriceEur",
        "aggregatedTransactionData.medianTicketSizeEur",
        "aggregatedTransactionData.medianGrossSettlementPriceEur",
        "aggregatedTransactionData.settlementVWAPEur",

        // Transaction SMA 30 (leafs)
        "aggregatedTransactionData.simpleMovingAverage['30'].concentration",
        "aggregatedTransactionData.simpleMovingAverage['30'].concentrationDelta",
        "aggregatedTransactionData.simpleMovingAverage['30'].volume",
        "aggregatedTransactionData.simpleMovingAverage['30'].volumeDelta",
        "aggregatedTransactionData.simpleMovingAverage['30'].averageTicketSize",
        "aggregatedTransactionData.simpleMovingAverage['30'].averageTicketSizeDelta",
        "aggregatedTransactionData.simpleMovingAverage['30'].medianTicketSize",
        "aggregatedTransactionData.simpleMovingAverage['30'].medianTicketSizeDelta",
        "aggregatedTransactionData.simpleMovingAverage['30'].averageGrossSettlementPrice",
        "aggregatedTransactionData.simpleMovingAverage['30'].averageGrossSettlementPriceDelta",
        "aggregatedTransactionData.simpleMovingAverage['30'].medianGrossSettlementPrice",
        "aggregatedTransactionData.simpleMovingAverage['30'].medianGrossSettlementPriceDelta",
        "aggregatedTransactionData.simpleMovingAverage['30'].settlementVWAP",
        "aggregatedTransactionData.simpleMovingAverage['30'].settlementVWAPDelta"
    };

}
