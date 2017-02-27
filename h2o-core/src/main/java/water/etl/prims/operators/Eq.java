package water.etl.prims.operators;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.MathUtils;
import water.util.StringUtils;

public final class Eq {
    private Eq() {}
    public static Frame get(Frame fr, final double d) {

            return new MRTask() {
                @Override
                public void map(Chunk[] chks, NewChunk[] cress) {
                    for (int c = 0; c < chks.length; c++) {
                        Chunk chk = chks[c];
                        NewChunk cres = cress[c];
                        BufferedString bStr = new BufferedString();
                        if (chk.vec().isString())
                            for (int i = 0; i < chk._len; i++)
                                cres.addNum(str_op(chk.atStr(bStr, i), Double.isNaN(d) ? null : new BufferedString(String.valueOf(d))));
                        else if (!chk.vec().isNumeric()) cres.addZeros(chk._len);
                        else
                            for (int i = 0; i < chk._len; i++)
                                cres.addNum(op(chk.atd(i), d));
                    }
                }
            }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame();

    }

    public static Frame get(Frame fr, final String s) {

        return new MRTask() {
            @Override
            public void map(Chunk[] chks, NewChunk[] cress) {
                for (int c = 0; c < chks.length; c++) {
                    Chunk chk = chks[c];
                    NewChunk cres = cress[c];
                    BufferedString bStr = new BufferedString();
                    if (chk.vec().isString())
                        for (int i = 0; i < chk._len; i++)
                            cres.addNum(str_op(chk.atStr(bStr, i), new BufferedString(s)));
                    else if (!chk.vec().isNumeric()) cres.addZeros(chk._len);
                    else
                        for (int i = 0; i < chk._len; i++)
                            cres.addNum(op(chk.atd(i), Double.valueOf(s)));
                }
            }
        }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame();

    }

    private static double op(double l, double r) {
        return MathUtils.equalsWithinOneSmallUlp(l, r) ? 1 : 0;
    }

    private static double str_op(BufferedString l, BufferedString r) {
        if (StringUtils.isNullOrEmpty(l))
            return StringUtils.isNullOrEmpty(r) ? 1 : 0;
        else
            return l.equals(r) ? 1 : 0;
    }
}
