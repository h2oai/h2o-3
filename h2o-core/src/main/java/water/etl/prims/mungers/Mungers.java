package water.etl.prims.mungers;


import water.fvec.Frame;
import water.fvec.Vec;

public final class Mungers{
    private Mungers() {}
    public static Frame OneHotEncode(Frame fr) {
        return fr;
    }
    /** Return the same frame with a selection of rows according to a boolean vec
     *
     */
    public static Frame Rows(Frame fr, Vec v) {
        return fr;
    }
    public static Frame Rows(Frame fr, Frame frSingleCol) {
        return frSingleCol;
    }
}
