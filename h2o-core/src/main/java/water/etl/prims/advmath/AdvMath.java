package water.etl.prims.advmath;

import water.fvec.Frame;
import water.fvec.Vec;

public final class AdvMath {
    private AdvMath() {}
    public static Frame StratifiedSplit(Frame sourceFr, String stratColName, double testFrac, long seed) {
        return StratifiedSplit.get(sourceFr,stratColName,testFrac,seed);
    }




}
