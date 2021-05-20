package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.util.FVec;
import hex.DataInfo;
import hex.Model;
import hex.tree.xgboost.XGBoostOutput;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.VecUtils;

public abstract class AssignLeafNodeTask extends MRTask<AssignLeafNodeTask> {

    protected final Predictor _p;
    protected final String[] _names;
    private final DataInfo _di;
    private final boolean _sparse;
    private byte _resultType;

    protected AssignLeafNodeTask(DataInfo di, XGBoostOutput output, byte[] boosterBytes, byte resultType) {
        this._p = PredictorFactory.makePredictor(boosterBytes, null, false);
        this._di = di;
        this._sparse = output._sparse;
        this._names = makeNames(output._ntrees, output.nclasses());
        this._resultType = resultType;
    }

    protected abstract void assignNodes(final FVec input, final NewChunk[] outs);
    
    private String[] makeNames(int ntrees, int nclass) {
        nclass = nclass > 2 ? nclass : 1;
        String[] names = new String[ntrees * nclass];
        for (int t = 0; t < ntrees; t++) {
            for (int c = 0; c < nclass; c++) {
                names[t*nclass + c] = "T" + (t+1) + ".C" + (c+1);
            }
        }
        return names;
    }

    @Override
    public void map(Chunk chks[], NewChunk[] idx) {
        MutableOneHotEncoderFVec inputVec = new MutableOneHotEncoderFVec(_di, _sparse);
        double[] input = new double[chks.length];
        for (int row = 0; row < chks[0]._len; row++) {
            for (int i = 0; i < chks.length; i++)
                input[i] = chks[i].atd(row);
            inputVec.setInput(input);
            assignNodes(inputVec, idx);
        }
    }

    public Frame execute(Frame adaptFrm, Key<Frame> destKey) {
        return doAll(_names.length, _resultType, adaptFrm).outputFrame(destKey, _names, null);
    }

    public static AssignLeafNodeTask make(
        DataInfo di, XGBoostOutput output, byte[] boosterBytes, Model.LeafNodeAssignment.LeafNodeAssignmentType type) {
        switch (type) {
            case Path:
                return new AssignTreePathTask(di, output, boosterBytes);
            case Node_ID:
                return new AssignLeafNodeIdTask(di, output, boosterBytes);
            default:
                throw new UnsupportedOperationException("Unknown leaf node assignment type: " + type);
        }
    }

    static class AssignTreePathTask extends AssignLeafNodeTask {

        public AssignTreePathTask(DataInfo di, XGBoostOutput output, byte[] boosterBytes) {
            super(di, output, boosterBytes, Vec.T_STR);
        }

        @Override
        protected void assignNodes(FVec input, NewChunk[] outs) {
            String[] leafPaths = _p.predictLeafPath(input);
            for (int i = 0; i < leafPaths.length; i++) {
                outs[i].addStr(leafPaths[i]);
            }
        }

        @Override
        public Frame execute(Frame adaptFrm, Key<Frame> destKey) {
            Frame res = super.execute(adaptFrm, destKey);
            // convert to categorical
            Vec vv;
            Vec[] nvecs = new Vec[res.vecs().length];
            for(int c = 0; c < res.vecs().length; c++) {
                vv = res.vec(c);
                try {
                    nvecs[c] = vv.toCategoricalVec();
                } catch (Exception e) {
                    VecUtils.deleteVecs(nvecs, c);
                    throw e;
                }
            }
            res.delete();
            res = new Frame(destKey, _names, nvecs);
            DKV.put(res);
            return res;
        }
    }

    static class AssignLeafNodeIdTask extends AssignLeafNodeTask {

        public AssignLeafNodeIdTask(DataInfo di, XGBoostOutput output, byte[] boosterBytes) {
            super(di, output, boosterBytes, Vec.T_NUM);
        }

        @Override
        protected void assignNodes(FVec input, NewChunk[] outs) {
            int[] leafIdx = _p.getBooster().predictLeaf(input, 0);
            for (int i = 0; i < leafIdx.length; i++) {
                outs[i].addNum(leafIdx[i]);
            }
        }

    }

}
