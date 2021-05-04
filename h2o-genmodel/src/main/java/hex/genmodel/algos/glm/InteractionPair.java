package hex.genmodel.algos.glm;

import java.io.Serializable;

public class InteractionPair implements Serializable {
    public final String columnA;
    public final String columnB;

    public InteractionPair(String columnA, String columnB) {
        this.columnA = columnA;
        this.columnB = columnB;
    }
}
