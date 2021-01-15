package hex.tree.uplift;

import static water.util.MathUtils.log2;

public class KLDivergence extends Divergence {
    
    public double metric(double prCT1, double prCT0) {
        return prCT1 * log2(prCT1 / prCT0);
    }
    
    @Override
    public double node(double prCT1, double prCT0) {
        return metric(prCT1, prCT0) + metric(1 - prCT1, 1 - prCT0);
    }
    
    @Override
    public double norm(
        double prCT1, double prCT0, 
        double prLCT1, double prLCT0
    ) {
        double klCT = node(prCT1, prCT0);
        double entCT =  -(prCT1 * log2(prCT1) + prCT0 * log2(prCT0));
        double entCT1 = -(prLCT1 * log2(prLCT1) + (1 - prLCT1) * log2((1 - prLCT1)));
        double entCT0 = -(prLCT0 * log2(prLCT0) + (1 - prLCT0) * log2((1 - prLCT0)));
        return klCT * entCT + entCT1 * prCT1 + entCT0 * prCT0 + 0.5;
    }
    
}
