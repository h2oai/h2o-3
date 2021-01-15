package hex.tree.uplift;

public class EuclideanDistance extends Divergence {

    @Override
    public double node(double prCT1, double prCT0) {
        double diff = prCT1 - prCT0;
        double sum = prCT1 + prCT0;
        return diff * diff + sum * sum;
    }

    @Override
    public double norm(
        double prCT1, double prCT0,
        double prLCT1, double prLCT0
    ) {
        double eucliCT = node(prLCT1, prLCT0);
        double giniCT = 2 * prCT1 * (1 - prCT1);
        double giniCT1 = 2 * prLCT1 * (1 - prLCT1);
        double giniCT0 = 2 * prLCT0 * (1 - prLCT0);
        return giniCT * eucliCT + giniCT1 * prCT1 + giniCT0 * prCT0 + 0.5;
    }
}
