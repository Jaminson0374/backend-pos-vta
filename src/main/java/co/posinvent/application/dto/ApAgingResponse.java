package co.posinvent.application.dto;

import java.math.BigDecimal;

public record ApAgingResponse(
        AgingBucket current,
        AgingBucket days1to30,
        AgingBucket days31to60,
        AgingBucket days61to90,
        AgingBucket days91Plus,
        BigDecimal totalOutstanding
) {
    public record AgingBucket(int count, BigDecimal total) {
        public static AgingBucket empty() {
            return new AgingBucket(0, BigDecimal.ZERO);
        }

        public static AgingBucket of(int count, BigDecimal total) {
            return new AgingBucket(count, total != null ? total : BigDecimal.ZERO);
        }
    }

    public static ApAgingResponse empty() {
        return new ApAgingResponse(
                AgingBucket.empty(),
                AgingBucket.empty(),
                AgingBucket.empty(),
                AgingBucket.empty(),
                AgingBucket.empty(),
                BigDecimal.ZERO
        );
    }
}
