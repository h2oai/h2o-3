package hex.kmeans;

import hex.Model;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.MRTask;
import water.MetricTest;
import water.Scope;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.fp.Function;
import water.util.fp.Function2;



import static hex.genmodel.GenModel.Kmeans_preprocessData;

public class KmeansMetricTest extends MetricTest {
    
    private Function<Model.Parameters, ModelBuilder> kmeansConstructor = parameters -> {
        KMeansModel.KMeansParameters kmeansParameters = (KMeansModel.KMeansParameters)parameters;
        return new KMeans(kmeansParameters);
    };

    @BeforeClass
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIndependentModelMetricsCalculation() {
        Scope.enter();
        try {
            Frame dataset = Scope.track(parseTestFile("smalldata/iris/iris_wheader.csv", new int[]{4}));
            Frame init = Scope.track(ArrayUtils.frame(
                ard(ard(4.9, 3.0, 1.4, 0.2), ard(5.6, 2.5, 3.9, 1.1), ard(6.5, 3.0, 5.2, 2.0))));

            KMeansModel.KMeansParameters params = new KMeansModel.KMeansParameters();
            params._k = 3;
            params._user_points = init._key;
            
            final double tolerance = 0.000001;
            final Function2<Frame, Model, Vec[]> actualVectorsGetter = (frame, model) -> {
                KMeansModel kMeansModel = (KMeansModel)model;
                StandardizationTask standardizationTask  = new StandardizationTask(
                    kMeansModel._output._normSub,
                    kMeansModel._output._normMul,
                    kMeansModel._output._mode);
                standardizationTask.doAll(new byte[] {Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM,}, frame.vecs());
                return AppendableVec.closeAll(standardizationTask.appendables());
            };
            final boolean ignoreTrainingMetrics = false;
            testIndependentlyCalculatedMetrics(dataset, params, kmeansConstructor, actualVectorsGetter, tolerance, ignoreTrainingMetrics);
        } finally {
            Scope.exit();
        }
    }
    
    class StandardizationTask extends MRTask<StandardizationTask> {

        double[] _normSub;
        double[] _normMul;
        int[] _mode;
        
        public StandardizationTask(double[] normSub, double[] normMul, int[] mode) {
            _normSub = normSub;
            _normMul = normMul;
            _mode = mode;
        }
        
        
        @Override
        public void map(Chunk[] chunks, NewChunk[] newChunks) {
            for (int chunkId = 0; chunkId < chunks.length; chunkId++) {
                for (int rowId = 0; rowId < chunks[chunkId]._len; ++rowId) {
                    double value = chunks[chunkId].atd(rowId);
                    double standardized = Kmeans_preprocessData(value, chunkId, _normSub, _normMul, _mode);
                    newChunks[chunkId].addNum(standardized);
                }
            }
        }
    }
}
