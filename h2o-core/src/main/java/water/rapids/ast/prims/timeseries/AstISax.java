package water.rapids.ast.prims.timeseries;

import com.sun.xml.internal.bind.v2.runtime.reflect.opt.FieldAccessor_Double;
import org.apache.commons.math3.distribution.NormalDistribution;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMap;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.IcedHashMapGeneric;
import water.util.IcedInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The iSAX 2.0 algorithm is a time series indexing strategy that reduces the dimensionality of a time series along the time axis.
 * For example, if a time series had 1000 unique values with data across 500 rows, reduce this data set to a time series that
 * uses 100 unique values, across 10 buckets along the time span.
 *
 * @author markchan & navdeepgill
 */
public class AstISax extends AstPrimitive {
    //protected IcedHashMap<IcedInt,IcedInt[]> _domain_hm = new IcedHashMap<>();
    protected double[][] _domain_hm = null;
    @Override
    public String[] args() { return new String[]{"ary", "numWords", "maxCardinality"}; }

    @Override
    public int nargs() { return 1 + 3; } // (isax x breaks)

    @Override
    public String str() { return "isax"; }

    @Override
    public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        // stack is [ ..., ary, numWords, maxCardinality]
        Frame f = stk.track(asts[1].exec(env)).getFrame();
        AstRoot n = asts[2];
        AstRoot mc = asts[3];

        for(Vec v : f.vecs()){
            if(!v.isNumeric()){
                throw new IllegalArgumentException("iSAX only applies to numeric columns");
            }
        }

        int numWords;
        int maxCardinality;
        AstISax.ISaxTask isaxt;

        numWords = (int) n.exec(env).getNum();
        maxCardinality = (int) mc.exec(env).getNum();




        ArrayList<String> columns = new ArrayList<String>();
        for (int i = 0; i < numWords; i++) {
            columns.add("c"+i);
        }
        Frame fr2 = new AstISax.ISaxTask(numWords, maxCardinality)
                .doAll(numWords, Vec.T_NUM, f).outputFrame(null, columns.toArray(new String[numWords]), null);

        _domain_hm = new double[numWords][maxCardinality];
        for (double[] r : _domain_hm) Arrays.fill(r,Double.NaN);
        // see if we can reduce the cardinality by checking all unique tokens in all series in a word
        for (int i=0; i<fr2.numCols(); i++) {
            List<IcedInt> intlist = new ArrayList<>();
            String[] domains = fr2.vec(i).toCategoricalVec().domain();
            for (int j = 0; j < domains.length; j++){
                _domain_hm[i][j] = Double.valueOf(domains[j]);
                //intlist.add(new IcedInt(Integer.valueOf(s)));
            }
            //_domain_hm.put(new IcedInt(i),intlist.toArray(new IcedInt[domains.length]));
        }

        int[] maxCards = new int[numWords];
        // get the cardinalities of each word
        for (int i = 0; i < numWords; i++) {
            int cnt = 0;
            for (double d : _domain_hm[i]) {
                if (Double.isNaN(d)) break;
                else cnt++;
            }
            maxCards[i] = cnt;
        }


        Frame fr2_reduced = new AstISax.ISaxReduceCard(_domain_hm,maxCardinality).doAll(numWords, Vec.T_NUM,fr2)
                .outputFrame(null,columns.toArray(new String[numWords]),null);
        Frame fr3 = new AstISax.ISaxStringTask(maxCards).doAll(1,Vec.T_STR,fr2_reduced)
                .outputFrame(null,new String[]{"isax_index"},null);

        fr2.delete(); //Not needed anymore
        fr3.add(fr2_reduced);
        return new ValFrame(fr3);
    }

    public static class ISaxReduceCard extends MRTask<AstISax.ISaxReduceCard> {
        double[][] _domain_hm;
        int maxCardinality;
        ISaxReduceCard(double[][] dm, int mc) {
            _domain_hm = dm;
            maxCardinality = mc;
        }

        @Override
        public void map(Chunk cs[], NewChunk nc[]){
            for (int i = 0; i<cs.length; i++) {
                boolean ltMaxCardFlag =  Double.isNaN(ArrayUtils.sum(_domain_hm[i]));
                for (int j = 0; j<cs[i].len(); j++) {
                    int idxOf;
                    if (ltMaxCardFlag) {
                        idxOf = Arrays.binarySearch(_domain_hm[i],(int) cs[i].at8(j));
                    } else {
                        idxOf = (int) cs[i].at8(j);
                    }
                    nc[i].addNum(idxOf);

                }
            }
        }
    }
    public static class ISaxStringTask extends MRTask<AstISax.ISaxStringTask> {
        int[] maxCards;
        ISaxStringTask(int[] mc) { maxCards = mc; }

        @Override
        public void map(Chunk cs[], NewChunk nc[]) {
            int csize = cs[0].len();
            for (int c_i = 0; c_i < csize; c_i++) {
                StringBuffer sb = new StringBuffer("");
                for (int cs_i = 0; cs_i < cs.length; cs_i++) {
                    sb.append(cs[cs_i].at8(c_i) + "^" + maxCards[cs_i] + "_");
                }
                nc[0].addStr(sb.toString().substring(0,sb.length()-1));
            }

        }

    }

    public static class ISaxTask extends MRTask<AstISax.ISaxTask> {
        private int nw;
        private int mc;
        private static NormalDistribution nd = new NormalDistribution();
        private ArrayList<Double> probBoundaries; // for tokenizing iSAX

        ISaxTask(int numWords, int maxCardinality) {
            nw = numWords;
            mc = maxCardinality;
            // come up with NormalDist boundaries
            double step = 1.0 / mc;
            probBoundaries = new ArrayList<Double>(); //cumulative dist function boundaries R{0-1}
            for (int i = 0; i < mc; i++) {
                probBoundaries.add(nd.inverseCumulativeProbability(i*step));
            }
        }
        @Override
        public void map(Chunk cs[],NewChunk[] nc) {
            int step = cs.length/nw;
            int chunkSize = cs[0].len();
            int w_i = 0; //word iterator
            double[] seriesSums = new double[chunkSize];
            double[] seriesCounts = new double[chunkSize];
            double[] seriesSSE = new double[chunkSize];
            double[][] chunkMeans = new double[chunkSize][nw];
            // Loop by words in the time series
            for (int i = 0; i < cs.length; i+=step) {
                // Loop by each series in the chunk
                for (int j = 0; j < chunkSize; j++) {
                    double mySum = 0.0;
                    double myCount = 0.0;
                    // Loop through all the data in the chunk for the given series in the given subset (word)
                    for (Chunk c : ArrayUtils.subarray(cs,i,i+step)) {
                        if (c != null) {
                            // Calculate mean and sigma in one pass
                            double oldMean = myCount < 1 ? 0.0 : mySum/myCount;
                            mySum += c.atd(j);
                            seriesSums[j] += c.atd(j);
                            myCount++;
                            seriesCounts[j] += 1;
                            seriesSSE[j] += (c.atd(j) - oldMean) * (c.atd(j) - mySum/myCount);
                        }
                    }
                    chunkMeans[j][w_i] = mySum / myCount;
                }
                w_i++;
                if (w_i>= nw) break;
            }
            //
            for (int w = 0; w < nw; w++) {
                for (int i = 0; i < chunkSize; i++) {
                    double seriesMean = seriesSums[i] / seriesCounts[i];
                    double seriesStd = Math.sqrt(seriesSSE[i] / (seriesCounts[i] - 1));
                    double zscore = (chunkMeans[i][w] - seriesMean) / seriesStd;
                    int p_i = 0;
                    while (probBoundaries.get(p_i + 1) < zscore) {
                        p_i++;
                        if (p_i == mc - 1) break;
                    }
                    nc[w].addNum(p_i,0);
                }
            }
        }
    }


}
