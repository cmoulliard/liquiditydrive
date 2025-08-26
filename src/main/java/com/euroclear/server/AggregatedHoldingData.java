package com.euroclear.server;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class AggregatedHoldingData {
    @JsonProperty("freeFloatHolding") private BigDecimal freeFloatHolding;
    @JsonProperty("freeFloatHoldingEur") private BigDecimal freeFloatHoldingEur;
    @JsonProperty("freeFloatMarketValueEur") private BigDecimal freeFloatMarketValueEur;
    @JsonProperty("concentration") private double concentration;
    @JsonProperty("averageHoldingSizeEur") private BigDecimal averageHoldingSizeEur;
    @JsonProperty("averageHoldingSizeMarketValueEur") private BigDecimal averageHoldingSizeMarketValueEur;
    @JsonProperty("medianHoldingSizeEur") private BigDecimal medianHoldingSizeEur;
    @JsonProperty("medianHoldingSizeMarketValueEur") private BigDecimal medianHoldingSizeMarketValueEur;

    public AggregatedHoldingData(){}

    public AggregatedHoldingData(BigDecimal freeFloatHolding, BigDecimal freeFloatHoldingEur, BigDecimal freeFloatMarketValueEur, double concentration, BigDecimal averageHoldingSizeEur, BigDecimal averageHoldingSizeMarketValueEur, BigDecimal medianHoldingSizeEur, BigDecimal medianHoldingSizeMarketValueEur) {
        this.freeFloatHolding = freeFloatHolding;
        this.freeFloatHoldingEur = freeFloatHoldingEur;
        this.freeFloatMarketValueEur = freeFloatMarketValueEur;
        this.concentration = concentration;
        this.averageHoldingSizeEur = averageHoldingSizeEur;
        this.averageHoldingSizeMarketValueEur = averageHoldingSizeMarketValueEur;
        this.medianHoldingSizeEur = medianHoldingSizeEur;
        this.medianHoldingSizeMarketValueEur = medianHoldingSizeMarketValueEur;
    }

    public BigDecimal getFreeFloatHolding() { return freeFloatHolding; }
    public void setFreeFloatHolding(BigDecimal freeFloatHolding) { this.freeFloatHolding = freeFloatHolding; }
    public BigDecimal getFreeFloatHoldingEur() { return freeFloatHoldingEur; }
    public void setFreeFloatHoldingEur(BigDecimal freeFloatHoldingEur) { this.freeFloatHoldingEur = freeFloatHoldingEur; }
    public BigDecimal getFreeFloatMarketValueEur() { return freeFloatMarketValueEur; }
    public void setFreeFloatMarketValueEur(BigDecimal freeFloatMarketValueEur) { this.freeFloatMarketValueEur = freeFloatMarketValueEur; }
    public double getConcentration() { return concentration; }
    public void setConcentration(double concentration) { this.concentration = concentration; }
    public BigDecimal getAverageHoldingSizeEur() { return averageHoldingSizeEur; }
    public void setAverageHoldingSizeEur(BigDecimal averageHoldingSizeEur) { this.averageHoldingSizeEur = averageHoldingSizeEur; }
    public BigDecimal getAverageHoldingSizeMarketValueEur() { return averageHoldingSizeMarketValueEur; }
    public void setAverageHoldingSizeMarketValueEur(BigDecimal averageHoldingSizeMarketValueEur) { this.averageHoldingSizeMarketValueEur = averageHoldingSizeMarketValueEur; }
    public BigDecimal getMedianHoldingSizeEur() { return medianHoldingSizeEur; }
    public void setMedianHoldingSizeEur(BigDecimal medianHoldingSizeEur) { this.medianHoldingSizeEur = medianHoldingSizeEur; }
    public BigDecimal getMedianHoldingSizeMarketValueEur() { return medianHoldingSizeMarketValueEur; }
    public void setMedianHoldingSizeMarketValueEur(BigDecimal medianHoldingSizeMarketValueEur) { this.medianHoldingSizeMarketValueEur = medianHoldingSizeMarketValueEur; }
}
