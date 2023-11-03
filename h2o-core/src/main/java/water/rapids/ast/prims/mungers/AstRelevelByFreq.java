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

import java.util.Arrays;

public class AstRelevelByFreq extends AstPrimitive<AstRelevelByFreq> {

    @Override
    public String[] args() {
        return new String[]{"frame", "weights", "topn"};
    }

    @Override
    public int nargs() {
        return 1 + 3;
    } // (relevel.by.freq frame weights topn)

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
        final double topN = asts[3].exec(env).getNum();
        if ((topN != -1 && topN <= 0) || (int) topN != topN) {
            throw new IllegalArgumentException("TopN argument needs to be a positive integer number, got: " + topN);
        }
        Frame result = new Frame(f);
        for (int i = 0; i < result.numCols(); i++) {
            Vec v = result.vec(i);
            if (!v.isCategorical()) {
                continue;
            }
            v = v.makeCopy();
            result.replace(i, v);
            relevelByFreq(v, weights, (int) topN);
        }
        return new ValFrame(result);
    }

    static void relevelByFreq(Vec v, Vec weights, int topN) {
        double[] levelWeights = VecUtils.collectDomainWeights(v, weights);
        int[] newDomainOrder = ArrayUtils.seq(0, levelWeights.length); 
        ArrayUtils.sort(newDomainOrder, levelWeights, 0,-1 );
        if ((topN != -1) && (topN < newDomainOrder.length - 1)) {
            newDomainOrder = takeTopNMostFrequentDomain(newDomainOrder, topN);
        }
        String[] domain = v.domain();
        String[] newDomain = v.domain().clone();
        for (int i = 0; i < newDomainOrder.length; i++) {
            newDomain[i] = domain[newDomainOrder[i]];
        }
        // new domain order != mapping of levels
        new RemapDomain(getMapping(newDomainOrder)).doAll(v);
        v.setDomain(newDomain);
        DKV.put(v);
    }

    /**
     * Create mapping from reordered domain list.
     * @param domainOrder sorted domain by count/weights DESC
     * @return mapping from the old level to the new level
     */
    static int[] getMapping(int[] domainOrder){
        int[] mapping = new int[domainOrder.length];
        for (int i = 0; i < domainOrder.length; i++) {
            mapping[domainOrder[i]] = i;
        }
        return mapping;
    }

    /**
     * Take the top N domains and sort rest of the indexes ASC 
     * @param domainOrder domain order already ordered by frequency DESC
     * @param topN number of top N domains to keep unsorted
     * @return new domain order where top N domains are untouched and rest of the domains are sorted ASC
     */
    static int[] takeTopNMostFrequentDomain(int[] domainOrder, final int topN) {
        int domainSize = domainOrder.length;
        int[] newDomainOrder = new int[domainSize];
        int[] topNidxs = new int[topN];
        for (int i = 0; i < topN; i++) {
            int topIdx = domainOrder[i];
            topNidxs[i] = topIdx;
            newDomainOrder[i] = topIdx;
        }
        Arrays.sort(topNidxs);
        int pos = topN;
        for (int i = 0; i < domainSize; i++) {
            if (Arrays.binarySearch(topNidxs, i) >= 0)
                continue;
            newDomainOrder[pos++] = i;
        }
        assert pos == domainSize;
        return newDomainOrder;
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
                int newLevel = _mapping[level];
                c.set(row, newLevel);
            }
        }
    }
}
