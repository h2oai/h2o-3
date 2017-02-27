package water.etl.prims.operators;

import water.fvec.Frame;

/**
 * Created by markc on 2/24/17.
 */
public class Operators {
    private Operators() {}
    public static Frame Eq(Frame fr, double d) {
        return Eq.get(fr,d);
    }
    public static Frame Eq(Frame fr, String s) {
        return Eq.get(fr,s);
    }
}
