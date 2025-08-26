package com.euroclear.server;

import java.util.List;

public class LiquidityResponse {
    private String referenceDate;
    private String compositeLiquidityScore;
    private String concentrationWeightedLiquidityScore;
    private String holdingScore;
    private String turnoverScore;
    private String isin;
    private String amountType;
    private String ecbTiering;
    private String ecbHaircut;
    private String optionFlag;
    private String zeroCouponFlag;
    private String couponPaymentDate;
    private String couponRecordDate;
    private String interestPeriodStartDate;
    private String interestPeriodEndDate;
    private String couponPaymentFrequency;
    private String rateType;
    private String couponInterestRate;
    private String couponGrossAmount;
    private AggregatedHoldingData aggregatedHoldingData;
    private AggregatedTransactionData aggregatedTransactionData;
    private List<Transaction> transactions;

    // No-arg constructor for Jackson
    public LiquidityResponse(){}

    // Constructor to initialize all fields
    public LiquidityResponse(String referenceDate, String compositeLiquidityScore, String concentrationWeightedLiquidityScore, String holdingScore, String turnoverScore, String isin, String amountType, String ecbTiering, String ecbHaircut, String optionFlag, String zeroCouponFlag, String couponPaymentDate, String couponRecordDate, String interestPeriodStartDate, String interestPeriodEndDate, String couponPaymentFrequency, String rateType, String couponInterestRate, String couponGrossAmount, AggregatedHoldingData aggregatedHoldingData, AggregatedTransactionData aggregatedTransactionData, List<Transaction> transactions) {
        this.referenceDate = referenceDate;
        this.compositeLiquidityScore = compositeLiquidityScore;
        this.concentrationWeightedLiquidityScore = concentrationWeightedLiquidityScore;
        this.holdingScore = holdingScore;
        this.turnoverScore = turnoverScore;
        this.isin = isin;
        this.amountType = amountType;
        this.ecbTiering = ecbTiering;
        this.ecbHaircut = ecbHaircut;
        this.optionFlag = optionFlag;
        this.zeroCouponFlag = zeroCouponFlag;
        this.couponPaymentDate = couponPaymentDate;
        this.couponRecordDate = couponRecordDate;
        this.interestPeriodStartDate = interestPeriodStartDate;
        this.interestPeriodEndDate = interestPeriodEndDate;
        this.couponPaymentFrequency = couponPaymentFrequency;
        this.rateType = rateType;
        this.couponInterestRate = couponInterestRate;
        this.couponGrossAmount = couponGrossAmount;
        this.aggregatedHoldingData = aggregatedHoldingData;
        this.aggregatedTransactionData = aggregatedTransactionData;
        this.transactions = transactions;
    }

    // --- Getters and Setters for all fields ---
    public String getReferenceDate() { return referenceDate; }
    public void setReferenceDate(String referenceDate) { this.referenceDate = referenceDate; }
    public String getCompositeLiquidityScore() { return compositeLiquidityScore; }
    public void setCompositeLiquidityScore(String compositeLiquidityScore) { this.compositeLiquidityScore = compositeLiquidityScore; }
    public String getConcentrationWeightedLiquidityScore() { return concentrationWeightedLiquidityScore; }
    public void setConcentrationWeightedLiquidityScore(String concentrationWeightedLiquidityScore) { this.concentrationWeightedLiquidityScore = concentrationWeightedLiquidityScore; }
    public String getHoldingScore() { return holdingScore; }
    public void setHoldingScore(String holdingScore) { this.holdingScore = holdingScore; }
    public String getTurnoverScore() { return turnoverScore; }
    public void setTurnoverScore(String turnoverScore) { this.turnoverScore = turnoverScore; }
    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }
    public String getAmountType() { return amountType; }
    public void setAmountType(String amountType) { this.amountType = amountType; }
    public String getEcbTiering() { return ecbTiering; }
    public void setEcbTiering(String ecbTiering) { this.ecbTiering = ecbTiering; }
    public String getEcbHaircut() { return ecbHaircut; }
    public void setEcbHaircut(String ecbHaircut) { this.ecbHaircut = ecbHaircut; }
    public String getOptionFlag() { return optionFlag; }
    public void setOptionFlag(String optionFlag) { this.optionFlag = optionFlag; }
    public String getZeroCouponFlag() { return zeroCouponFlag; }
    public void setZeroCouponFlag(String zeroCouponFlag) { this.zeroCouponFlag = zeroCouponFlag; }
    public String getCouponPaymentDate() { return couponPaymentDate; }
    public void setCouponPaymentDate(String couponPaymentDate) { this.couponPaymentDate = couponPaymentDate; }
    public String getCouponRecordDate() { return couponRecordDate; }
    public void setCouponRecordDate(String couponRecordDate) { this.couponRecordDate = couponRecordDate; }
    public String getInterestPeriodStartDate() { return interestPeriodStartDate; }
    public void setInterestPeriodStartDate(String interestPeriodStartDate) { this.interestPeriodStartDate = interestPeriodStartDate; }
    public String getInterestPeriodEndDate() { return interestPeriodEndDate; }
    public void setInterestPeriodEndDate(String interestPeriodEndDate) { this.interestPeriodEndDate = interestPeriodEndDate; }
    public String getCouponPaymentFrequency() { return couponPaymentFrequency; }
    public void setCouponPaymentFrequency(String couponPaymentFrequency) { this.couponPaymentFrequency = couponPaymentFrequency; }
    public String getRateType() { return rateType; }
    public void setRateType(String rateType) { this.rateType = rateType; }
    public String getCouponInterestRate() { return couponInterestRate; }
    public void setCouponInterestRate(String couponInterestRate) { this.couponInterestRate = couponInterestRate; }
    public String getCouponGrossAmount() { return couponGrossAmount; }
    public void setCouponGrossAmount(String couponGrossAmount) { this.couponGrossAmount = couponGrossAmount; }
    public AggregatedHoldingData getAggregatedHoldingData() { return aggregatedHoldingData; }
    public void setAggregatedHoldingData(AggregatedHoldingData aggregatedHoldingData) { this.aggregatedHoldingData = aggregatedHoldingData; }
    public AggregatedTransactionData getAggregatedTransactionData() { return aggregatedTransactionData; }
    public void setAggregatedTransactionData(AggregatedTransactionData aggregatedTransactionData) { this.aggregatedTransactionData = aggregatedTransactionData; }
    public List<Transaction> getTransactions() { return transactions; }
    public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }
}
