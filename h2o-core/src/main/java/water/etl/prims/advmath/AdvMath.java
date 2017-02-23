package water.etl.prims.advmath;

import water.fvec.Frame;
import water.fvec.Vec;

public final class AdvMath {
    private AdvMath() {}
    public static Frame StratifiedSplit(Frame sourceFr, Vec stratCol, double testFrac, long seed) {
        return StratifiedSplit.get(sourceFr,stratCol,testFrac,seed);
    }




}
