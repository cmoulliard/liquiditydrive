package com.euroclear.server;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class Volume {
    @JsonProperty("volumeEur") private BigDecimal volumeEur;

    public Volume() {}

    public Volume(BigDecimal volumeEur) {
        this.volumeEur = volumeEur;
    }

    public BigDecimal getVolumeEur() { return volumeEur; }
    public void setVolumeEur(BigDecimal volumeEur) { this.volumeEur = volumeEur; }
}
