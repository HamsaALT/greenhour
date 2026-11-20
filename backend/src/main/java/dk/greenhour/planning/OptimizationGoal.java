package dk.greenhour.planning;

public enum OptimizationGoal {
    PRICE,
    CO2,
    /** Weighs price and CO2 equally after scaling both to the range of the available options. */
    BALANCED;

    boolean needsCo2() {
        return this != PRICE;
    }
}
