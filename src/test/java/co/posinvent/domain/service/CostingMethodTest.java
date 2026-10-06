package co.posinvent.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CostingMethodTest {

    @Test
    void fromString_parsesFifo() {
        assertThat(CostingMethod.fromString("FIFO")).isEqualTo(CostingMethod.FIFO);
    }

    @Test
    void fromString_parsesWeightedAvg() {
        assertThat(CostingMethod.fromString("WEIGHTED_AVG")).isEqualTo(CostingMethod.WEIGHTED_AVG);
    }

    @Test
    void fromString_isCaseInsensitive() {
        assertThat(CostingMethod.fromString("fifo")).isEqualTo(CostingMethod.FIFO);
        assertThat(CostingMethod.fromString("weighted_avg")).isEqualTo(CostingMethod.WEIGHTED_AVG);
    }

    @Test
    void fromString_nullFallsBackToFifo() {
        assertThat(CostingMethod.fromString(null)).isEqualTo(CostingMethod.FIFO);
    }

    @Test
    void fromString_blankFallsBackToFifo() {
        assertThat(CostingMethod.fromString("")).isEqualTo(CostingMethod.FIFO);
        assertThat(CostingMethod.fromString("   ")).isEqualTo(CostingMethod.FIFO);
    }

    @Test
    void fromString_unknownFallsBackToFifo() {
        assertThat(CostingMethod.fromString("UNKNOWN")).isEqualTo(CostingMethod.FIFO);
        assertThat(CostingMethod.fromString("PEPS")).isEqualTo(CostingMethod.FIFO);
        assertThat(CostingMethod.fromString("PROMEDIO_PONDERADO")).isEqualTo(CostingMethod.FIFO);
    }
}
