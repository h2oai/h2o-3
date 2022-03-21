package hex.tree.xgboost;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.Frame;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class XGBoostConcurrentContribsTest extends TestUtil {

    private static final int REPEAT_N = 3;

    @Parameterized.Parameters
    public static Object[] repeated() { // emulating multiple runs
        return new Object[REPEAT_N];
    }

    @Parameterized.Parameter
    public Object run;

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testConcurrent() throws Exception {
        Assume.assumeTrue(H2O.getCloudSize() == 1); // don't test in multi-node, we are just testing MOJO
        Frame fr = null;
        XGBoostModel model = null;
        try {
            fr = parseTestFile("./smalldata/logreg/prostate.csv");
            fr.toCategoricalCol("CAPSULE");
            XGBoostModel.XGBoostParameters p = new XGBoostModel.XGBoostParameters();
            p._ignored_columns = new String[]{"ID"};
            p._train = fr._key;
            p._response_column = "CAPSULE";

            model = new XGBoost(p).trainModel().get();
            MojoModel mojo = model.toMojo();
            EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(
                    new EasyPredictModelWrapper.Config()
                            .setModel(mojo)
                            .setEnableContributions(true)
            );
            
            Frame featureFr = fr.subframe(mojo.features());
            RowData[] rowData = new RowData[(int) featureFr.numRows()];
            for (int i = 0; i < rowData.length; i++) {
                RowData row = new RowData();
                for (String feat : featureFr.names()) {
                    if (!featureFr.vec(feat).isNA(i)) {
                        double value = featureFr.vec(feat).at(i);
                        row.put(feat, value);
                    }
                }
                rowData[i] = row;
            }
            
            ExecutorService executor = Executors.newFixedThreadPool(4);

            // the idea is to have multiple independent scores running and using the same Wrapper instance
            // each scorer runs first generates reference predictions
            // and in subsequent runs compares with the reference - correct implementation won't change the results
            List<Callable<Integer>> runnables = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                runnables.add(new MojoScorer(wrapper, rowData));
            }
            for (Future<Integer> future : executor.invokeAll(runnables)) {
                assertNotNull(future.get());
            }
        } finally {
            if (fr != null)
                fr.delete();
            if (model != null)
                model.delete();
        }
    }

    private static class MojoScorer implements Callable<Integer> {

        private static final int N_ATTEMPTS = 50; 

        private final EasyPredictModelWrapper _wrapper; // shared
        private final RowData[] _data; // private copy

        private MojoScorer(EasyPredictModelWrapper wrapper, RowData[] data) {
            _wrapper = wrapper;
            _data = new RowData[data.length];
            for (int i = 0; i < data.length; i++) {
                _data[i] = (RowData) data[i].clone();
            }
        }

        public Integer call() {
            int compCnt = 0;
            float[][] contributions = new float[_data.length][];
            try {
                for (int attempt = 0; attempt < N_ATTEMPTS; attempt++) {
                    for (int i = 0; i < _data.length; i++) {
                        BinomialModelPrediction p = _wrapper.predictBinomial(_data[i]);
                        if (attempt == 0) {
                            contributions[i] = p.contributions;
                        } else {
                            compCnt++;
                            assertArrayEquals(contributions[i], p.contributions, 0);
                        }
                    }
                }
                return compCnt;
            } catch (PredictException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
