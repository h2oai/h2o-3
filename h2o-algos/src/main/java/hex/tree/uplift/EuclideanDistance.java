package hex.tree.uplift;

public class EuclideanDistance extends Divergence {

    @Override
    public double metric(double prCT1, double prCT0) {
        return (prCT1 - prCT0) * (prCT1 - prCT0);
    }

    @Override
    public double norm(
        double prCT1, double prCT0,
        double prLCT1, double prLCT0
    ) {
        double nodeCT = node(prLCT1, prLCT0);
        double giniCT = 2 * prCT1 * (1 - prCT1);
        double giniCT1 = 2 * prLCT1 * (1 - prLCT1);
        double giniCT0 = 2 * prLCT0 * (1 - prLCT0);
        return giniCT * nodeCT + giniCT1 * prCT1 + giniCT0 * prCT0 + 0.5;
    }
}
