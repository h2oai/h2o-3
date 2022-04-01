package water.rapids.ast.prims.mungers;

import water.DKV;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;
import water.util.VecUtils;

public class AstRelevelByFreq extends AstPrimitive<AstRelevelByFreq> {

    @Override
    public String[] args() {
        return new String[]{"frame", "weights"};
    }

    @Override
    public int nargs() {
        return 1 + 2;
    } // (relevel.by.freq frame weights)

    @Override
    public String str() {
        return "relevel.by.freq";
    }

    @Override
    public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        final Frame f = stk.track(asts[1].exec(env)).getFrame();
        final String weightsColumn = asts[2].exec(env).getStr();
        final Vec weights = f.vec(weightsColumn);
        if (weightsColumn != null && weights == null) {
            throw new IllegalArgumentException("Frame doesn't contain weights column '" + weightsColumn + "'.");
        }
        Frame result = new Frame(f);
        for (int i = 0; i < result.numCols(); i++) {
            Vec v = result.vec(i);
            if (!v.isCategorical()) {
                continue;
            }
            v = v.makeCopy();
            result.replace(i, v);
            relevelByFreq(v, weights);
        }
        return new ValFrame(result);
    }

    static void relevelByFreq(Vec v, Vec weights) {
        double[] levelWeights = VecUtils.collectDomainWeights(v, weights);
        int[] newDomainOrder = ArrayUtils.seq(0, levelWeights.length); 
        ArrayUtils.sort(newDomainOrder, levelWeights);
        String[] domain = v.domain();
        String[] newDomain = v.domain().clone();
        for (int i = 0; i < newDomainOrder.length; i++) {
            newDomain[i] = domain[newDomainOrder[newDomainOrder.length - i - 1]];
        }
        new RemapDomain(newDomainOrder).doAll(v);
        v.setDomain(newDomain);
        DKV.put(v);
    }

    static class RemapDomain extends MRTask<RemapDomain> {
        private final int[] _mapping;

        public RemapDomain(int[] mapping) {
            _mapping = mapping;
        }

        @Override
        public void map(Chunk c) {
            for (int row = 0; row < c._len; row++) {
                if (c.isNA(row))
                    continue;
                int level = (int) c.atd(row);
                int newLevel = _mapping.length - _mapping[level] - 1;
                c.set(row, newLevel);
            }
        }
    }

}
