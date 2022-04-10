package water.rapids.prims;

import hex.Model;
import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;

import java.util.Arrays;

public class AstPredictedVsActualByVar extends AstPrimitive<AstPredictedVsActualByVar> {

    @Override
    public String[] args() {
        return new String[]{"model"};
    }

    @Override
    public int nargs() {
        return 1 + 4;
    } // (predicted.vs.actual.by.var model frame variable predicted)

    @Override
    public String str() {
        return "predicted.vs.actual.by.var";
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        Model<?, ?, ?> model = (Model<?, ?, ?>) stk.track(asts[1].exec(env)).getModel();
        if (!model.isSupervised()) {
            throw new IllegalArgumentException("Only supervised models are supported for calculating predicted v actual");
        }
        if (model._output.isMultinomialClassifier()) {
            throw new IllegalArgumentException("Multinomial classification models are not supported by predicted v actual");
        }
        Frame frame = stk.track(asts[2].exec(env)).getFrame();
        String variable = stk.track(asts[3].exec(env)).getStr();
        if (frame.vec(variable) == null) {
            throw new IllegalArgumentException("Frame doesn't contain column '" + variable + "'.");
        }
        Frame preds = stk.track(asts[4].exec(env)).getFrame();
        if (frame.numRows() != preds.numRows()) {
            throw new IllegalArgumentException("Input frame and frame of predictions need to have same number of columns.");
        }
        Vec predicted = preds.vec(0);
        Vec actual = frame.vec(model._output.responseName());
        Vec weights = frame.vec(model._output.weightsName());
        if ((actual.domain() != predicted.domain()) && !Arrays.equals(actual.domain(), predicted.domain())) { // null or equals
            throw new IllegalArgumentException("Actual and predicted need to have identical domain.");
        }
        Vec varVec = frame.vec(variable);
        Vec[] vs = new Vec[]{predicted, actual, varVec};
        if (weights != null) {
            vs = ArrayUtils.append(vs, weights);
        }
        PredictedVsActualByVar pva = new PredictedVsActualByVar(varVec).doAll(vs);
        String[] domainExt = ArrayUtils.append(varVec.domain(), null); // last one for NA
        Vec[] resultVecs = new Vec[]{
                Vec.makeVec(domainExt, Vec.newKey()),
                Vec.makeVec(pva._preds, Vec.newKey()),
                Vec.makeVec(pva._acts, Vec.newKey())
        };
        Frame result = new Frame(new String[]{variable, preds.name(0), "actual"}, resultVecs);
        return new ValFrame(result);
    }

    static class PredictedVsActualByVar extends MRTask<PredictedVsActualByVar> {
        private final int _s;

        private double[] _preds;
        private double[] _acts;
        private double[] _weights;

        public PredictedVsActualByVar(Vec varVec) {
            _s = varVec.domain().length + 1;
        }

        @Override
        public void map(Chunk[] cs) {
            _preds = new double[_s];
            _acts = new double[_s];
            _weights = new double[_s];
            Chunk predChunk = cs[0];
            Chunk actChunk = cs[1];
            Chunk varChunk = cs[2];
            Chunk weightChunk = cs.length == 4 ? cs[3] : new C0DChunk(1, predChunk._len);
            for (int i = 0; i < actChunk._len; i++) {
                if (actChunk.isNA(i) || weightChunk.atd(i) == 0)
                    continue;
                int level = varChunk.isNA(i) ? _s - 1 : (int) varChunk.atd(i);
                double weight = weightChunk.atd(i);
                _preds[level] += weight * predChunk.atd(i);
                _acts[level] += weight * actChunk.atd(i);
                _weights[level] += weight;
            }
        }

        @Override
        public void reduce(PredictedVsActualByVar mrt) {
            _preds = ArrayUtils.add(_preds, mrt._preds);
            _acts = ArrayUtils.add(_acts, mrt._acts);
            _weights = ArrayUtils.add(_weights, mrt._weights);
        }

        @Override
        protected void postGlobal() {
            for (int i = 0; i < _weights.length; i++) {
                if (_weights[i] == 0)
                    continue;
                _preds[i] /= _weights[i];
                _acts[i] /= _weights[i];
            }
        }
    }

}
