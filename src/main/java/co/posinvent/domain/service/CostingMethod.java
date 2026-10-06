package co.posinvent.domain.service;

/**
 * Canonical global costing methods. Deterministic resolution of the configured
 * value to a strategy; any null, blank, or unrecognized value falls back to
 * {@link #FIFO} (the base method, regulatory-safe per §COSTEO / NIC 2).
 */
public enum CostingMethod {

    FIFO,
    WEIGHTED_AVG;

    /**
     * Resolves a canonical costing method from its string form.
     *
     * @param value the raw configured value, may be null or blank
     * @return the matching enum constant, or {@link #FIFO} when the value is
     *         null, blank, or does not match a known method
     */
    public static CostingMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            return FIFO;
        }
        String normalized = value.trim();
        for (CostingMethod method : values()) {
            if (method.name().equalsIgnoreCase(normalized)) {
                return method;
            }
        }
        return FIFO;
    }
}
