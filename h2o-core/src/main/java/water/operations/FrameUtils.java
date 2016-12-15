package water.operations;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Val;
import water.util.VecUtils;

public class FrameUtils {
    public static Frame convertAllColumnsToString(Frame ary) {
        Vec[] nvecs = new Vec[ary.numCols()];
        Vec vv;
        for (int c = 0; c < nvecs.length; ++c) {
            vv = ary.vec(c);
            try {
                nvecs[c] = vv.toStringVec();
            } catch (Exception e) {
                VecUtils.deleteVecs(nvecs, c);
                throw e;
            }
        }
        return new Frame(ary._names, nvecs);
    }

    public static Frame isNA(Val val) {
        Frame fr = val.getFrame();
        String[] newNames = new String[fr.numCols()];
        for (int i = 0; i < newNames.length; i++) {
            newNames[i] = "isNA(" + fr.name(i) + ")";
        }
        return new MRTask() {
            @Override
            public void map(Chunk cs[], NewChunk ncs[]) {
                for (int col = 0; col < cs.length; col++) {
                    Chunk c = cs[col];
                    NewChunk nc = ncs[col];
                    for (int i = 0; i < c._len; i++)
                        nc.addNum(c.isNA(i) ? 1 : 0);
                }
            }
        }.doAll(fr.numCols(), Vec.T_NUM, fr).outputFrame(newNames, null);
    }
}
