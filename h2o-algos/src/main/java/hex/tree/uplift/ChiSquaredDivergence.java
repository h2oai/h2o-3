package hex.tree.uplift;

public class ChiSquaredDivergence extends EuclideanDistance {

    @Override
    public double node(double prCT1, double prCT0) {
        double diff = prCT1 - prCT0;
        double diffSquared = diff * diff;
        return (diffSquared / prCT0) + (diffSquared / (1 - prCT0));
    }

}
