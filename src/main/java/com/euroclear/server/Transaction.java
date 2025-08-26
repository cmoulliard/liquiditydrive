package com.euroclear.server;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Transaction {
    @JsonProperty("transactionId") private String transactionId;
    @JsonProperty("transactionDate") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") private OffsetDateTime transactionDate;
    @JsonProperty("settlementDate") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") private OffsetDateTime settlementDate;
    @JsonProperty("transactionType") private String transactionType;
    @JsonProperty("transactionStatus") private String transactionStatus;
    @JsonProperty("quantity") private BigDecimal quantity;
    @JsonProperty("quantityUnit") private String quantityUnit;
    @JsonProperty("cashAmount") private BigDecimal cashAmount;
    @JsonProperty("cashSettlementCurrency") private String cashSettlementCurrency;
    @JsonProperty("grossSettlementPrice") private BigDecimal grossSettlementPrice;
    @JsonProperty("receivingPartyType") private String receivingPartyType;
    @JsonProperty("deliveringPartyType") private String deliveringPartyType;

    public Transaction() {}

    public Transaction(String transactionId, OffsetDateTime transactionDate, OffsetDateTime settlementDate, String transactionType, String transactionStatus, BigDecimal quantity, String quantityUnit, BigDecimal cashAmount, String cashSettlementCurrency, BigDecimal grossSettlementPrice, String receivingPartyType, String deliveringPartyType) {
        this.transactionId = transactionId;
        this.transactionDate = transactionDate;
        this.settlementDate = settlementDate;
        this.transactionType = transactionType;
        this.transactionStatus = transactionStatus;
        this.quantity = quantity;
        this.quantityUnit = quantityUnit;
        this.cashAmount = cashAmount;
        this.cashSettlementCurrency = cashSettlementCurrency;
        this.grossSettlementPrice = grossSettlementPrice;
        this.receivingPartyType = receivingPartyType;
        this.deliveringPartyType = deliveringPartyType;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public OffsetDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(OffsetDateTime transactionDate) { this.transactionDate = transactionDate; }
    public OffsetDateTime getSettlementDate() { return settlementDate; }
    public void setSettlementDate(OffsetDateTime settlementDate) { this.settlementDate = settlementDate; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public String getTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(String transactionStatus) { this.transactionStatus = transactionStatus; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getQuantityUnit() { return quantityUnit; }
    public void setQuantityUnit(String quantityUnit) { this.quantityUnit = quantityUnit; }
    public BigDecimal getCashAmount() { return cashAmount; }
    public void setCashAmount(BigDecimal cashAmount) { this.cashAmount = cashAmount; }
    public String getCashSettlementCurrency() { return cashSettlementCurrency; }
    public void setCashSettlementCurrency(String cashSettlementCurrency) { this.cashSettlementCurrency = cashSettlementCurrency; }
    public BigDecimal getGrossSettlementPrice() { return grossSettlementPrice; }
    public void setGrossSettlementPrice(BigDecimal grossSettlementPrice) { this.grossSettlementPrice = grossSettlementPrice; }
    public String getReceivingPartyType() { return receivingPartyType; }
    public void setReceivingPartyType(String receivingPartyType) { this.receivingPartyType = receivingPartyType; }
    public String getDeliveringPartyType() { return deliveringPartyType; }
    public void setDeliveringPartyType(String deliveringPartyType) { this.deliveringPartyType = deliveringPartyType; }
}
