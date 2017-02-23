package water.etl.prims.advmath;

import water.rapids.vals.ValFrame;
import water.fvec.Frame;
import water.fvec.Vec;

public final class AdvMath {
    private AdvMath() {}
    public static ValFrame StratifiedSplit(Frame sourceFr, Vec stratCol, double testFrac, long seed) {
        return StratifiedSplit.get(sourceFr,stratCol,testFrac,seed);
    }




}
