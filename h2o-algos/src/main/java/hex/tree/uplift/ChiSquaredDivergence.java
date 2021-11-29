package hex.tree.uplift;

public class ChiSquaredDivergence extends EuclideanDistance {

    @Override
    public double metric(double prCT1, double prCT0) {
        return ((prCT1 - prCT0) * (prCT1 - prCT0)) / (prCT0 == 0 ? Divergence.ZERO_TO_DIVIDE : prCT0);
    }

}
