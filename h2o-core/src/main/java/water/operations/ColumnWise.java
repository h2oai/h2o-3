package water.operations;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.vals.ValFrame;

public class ColumnWise {

    /**
     * Compute column-wise means (i.e. means of each column), and return a frame having a single row.
     */
    public static ValFrame mean(Frame fr, final boolean na_rm) {
        Frame res = new Frame();

        Vec vec1 = Vec.makeCon(null, 0);
        assert vec1.length() == 1;

        for (int i = 0; i < fr.numCols(); i++) {
            Vec v = fr.vec(i);
            boolean valid = (v.isNumeric() || v.isTime() || v.isBinary()) && v.length() > 0 && (na_rm || v.naCnt() == 0);
            Vec newvec = vec1.makeCon(valid ? v.mean() : Double.NaN, v.isTime() ? Vec.T_TIME : Vec.T_NUM);
            res.add(fr.name(i), newvec);
        }

        vec1.remove();
        return new ValFrame(res);
    }
}
