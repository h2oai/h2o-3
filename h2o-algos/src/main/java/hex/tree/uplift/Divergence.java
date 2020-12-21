package hex.tree.uplift;

public abstract class Divergence {
    
    public abstract double node(
        double prCT1,
        double prCT0
    );

    public double split(
        double prL, double prLY1CT1, double prLY1CT0,
        double prR, double prRY1CT1, double prRY1CT0
    ) {
        double klL = node(prLY1CT1, prLY1CT0);
        double klR = node(prRY1CT1, prRY1CT0);
        return prL * klL + prR * klR;
    }

    public double gain(
        double prY1CT1, double prY1CT0,
        double prL, double prLY1CT1, double prLY1CT0,
        double prR, double prRY1CT1, double prRY1CT0
    ) {
        return split(prL, prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0) - node(prY1CT1, prY1CT0);
    }
    
    abstract double norm(
        double prCT1, double prCT0,
        double prLCT1, double prLCT0
    );

    public double value(
        double prY1CT1, double prY1CT0,
        double prL, double prLY1CT1, double prLY1CT0,
        double prR, double prRY1CT1, double prRY1CT0,
        double prCT1, double prCT0,
        double prLCT1, double prLCT0
    ) {
        return
            gain(prY1CT1, prY1CT0, prL,  prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0)
                /
            norm(prCT1, prCT0, prLCT1, prLCT0);
    }
}
