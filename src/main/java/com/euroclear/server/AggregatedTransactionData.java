package com.euroclear.server;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class AggregatedTransactionData {
    @JsonProperty("totalVolCurrentBD") private Volume totalVolCurrentBD;
    @JsonProperty("totalVolNext1BD") private Volume totalVolNext1BD;
    @JsonProperty("totalVolNext2BD") private Volume totalVolNext2BD;
    @JsonProperty("transactionCount") private int transactionCount;
    @JsonProperty("averageTicketSizeEur") private BigDecimal averageTicketSizeEur;
    @JsonProperty("averageGrossSettlementPriceEur") private BigDecimal averageGrossSettlementPriceEur;
    @JsonProperty("medianTicketSizeEur") private BigDecimal medianTicketSizeEur;
    @JsonProperty("medianGrossSettlementPriceEur") private BigDecimal medianGrossSettlementPriceEur;
    @JsonProperty("settlementVWAPEur") private BigDecimal settlementVWAPEur;

    public AggregatedTransactionData(){}

    public AggregatedTransactionData(Volume totalVolCurrentBD, Volume totalVolNext1BD, Volume totalVolNext2BD, int transactionCount, BigDecimal averageTicketSizeEur, BigDecimal averageGrossSettlementPriceEur, BigDecimal medianTicketSizeEur, BigDecimal medianGrossSettlementPriceEur, BigDecimal settlementVWAPEur) {
        this.totalVolCurrentBD = totalVolCurrentBD;
        this.totalVolNext1BD = totalVolNext1BD;
        this.totalVolNext2BD = totalVolNext2BD;
        this.transactionCount = transactionCount;
        this.averageTicketSizeEur = averageTicketSizeEur;
        this.averageGrossSettlementPriceEur = averageGrossSettlementPriceEur;
        this.medianTicketSizeEur = medianTicketSizeEur;
        this.medianGrossSettlementPriceEur = medianGrossSettlementPriceEur;
        this.settlementVWAPEur = settlementVWAPEur;
    }

    public Volume getTotalVolCurrentBD() { return totalVolCurrentBD; }
    public void setTotalVolCurrentBD(Volume totalVolCurrentBD) { this.totalVolCurrentBD = totalVolCurrentBD; }
    public Volume getTotalVolNext1BD() { return totalVolNext1BD; }
    public void setTotalVolNext1BD(Volume totalVolNext1BD) { this.totalVolNext1BD = totalVolNext1BD; }
    public Volume getTotalVolNext2BD() { return totalVolNext2BD; }
    public void setTotalVolNext2BD(Volume totalVolNext2BD) { this.totalVolNext2BD = totalVolNext2BD; }
    public int getTransactionCount() { return transactionCount; }
    public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
    public BigDecimal getAverageTicketSizeEur() { return averageTicketSizeEur; }
    public void setAverageTicketSizeEur(BigDecimal averageTicketSizeEur) { this.averageTicketSizeEur = averageTicketSizeEur; }
    public BigDecimal getAverageGrossSettlementPriceEur() { return averageGrossSettlementPriceEur; }
    public void setAverageGrossSettlementPriceEur(BigDecimal averageGrossSettlementPriceEur) { this.averageGrossSettlementPriceEur = averageGrossSettlementPriceEur; }
    public BigDecimal getMedianTicketSizeEur() { return medianTicketSizeEur; }
    public void setMedianTicketSizeEur(BigDecimal medianTicketSizeEur) { this.medianTicketSizeEur = medianTicketSizeEur; }
    public BigDecimal getMedianGrossSettlementPriceEur() { return medianGrossSettlementPriceEur; }
    public void setMedianGrossSettlementPriceEur(BigDecimal medianGrossSettlementPriceEur) { this.medianGrossSettlementPriceEur = medianGrossSettlementPriceEur; }
    public BigDecimal getSettlementVWAPEur() { return settlementVWAPEur; }
    public void setSettlementVWAPEur(BigDecimal settlementVWAPEur) { this.settlementVWAPEur = settlementVWAPEur; }
}
