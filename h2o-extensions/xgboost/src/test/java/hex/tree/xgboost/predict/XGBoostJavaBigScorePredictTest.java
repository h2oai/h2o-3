package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.tree.xgboost.XGBoostModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
public class XGBoostJavaBigScorePredictTest extends TestUtil  {

    @Test
    public void testFindUsedColumns_onlySomeUsed() {
        XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
        parms._weights_column = "CAPSULE";
        parms._max_depth = 3;
        final int nTrees = 5;
        final int maxUsed = (2 << parms._max_depth) * nTrees; // upper bound for number of used columns
        
        boolean[] used = checkFindUsedColumns(parms, nTrees, maxUsed + 100); // add some extra random columns
        assertTrue(countTrues(used) > 0); // enough trees so that at least one column gets used
        assertTrue(countTrues(used) < maxUsed); // but not more than the theoretical upper bound
    }

    @Test
    public void testFindUsedColumns_noneUsed() {
        XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
        parms._min_rows = 10_000;

        boolean[] used = checkFindUsedColumns(parms, 1, 0);
        // min_rows is too high - we cannot make a split => no columns can be used
        assertEquals(0, countTrues(used));
    }

    @Test
    public void testFindUsedColumns_allUsed() {
        XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();

        boolean[] used = checkFindUsedColumns(parms, 50, 0);
        assertNull(used); // null means everything is used
    }

    private boolean[] checkFindUsedColumns(XGBoostModel.XGBoostParameters parms, final int N, final int nRandCols) {
        Scope.enter();
        try {
            Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            parms._train = tfr._key;
            parms._response_column = "AGE";
            parms._ignored_columns = new String[]{"ID"};
            parms._seed = 42;
            parms._ntrees = N;
            
            for (int i = 0; i < nRandCols; i++) {
                Vec randVec = tfr.anyVec().makeRand(42L + i);
                tfr.add("Rand" + i, randVec);
            }
            Scope.track(tfr);
            DKV.put(tfr);

            XGBoostModel model = Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
            assertNotNull(model);

            boolean[] usedExpected = new boolean[model._output.nfeatures()];
            for (int i = 0; i < N; i++) {
                SharedTreeGraph t = model.convert(i, null);
                for (SharedTreeNode node : t.subgraphArray.get(0).getNodes()) {
                    if (!node.isLeaf())
                        usedExpected[node.getSplitIndex()] = true;
                }
            }

            Predictor predictor = PredictorFactory.makePredictor(model.model_info()._boosterBytes);
            boolean[] usedActual = XGBoostJavaBigScorePredict.findUsedColumns(predictor.getBooster(),
                    model.model_info().dataInfo(), model._output.nfeatures());

            if (countTrues(usedExpected) == usedExpected.length) {
                assertNull(usedActual);
            } else {
                assertArrayEquals(usedExpected, usedActual);
            }
            return usedActual;
        } finally {
            Scope.exit();
        }
    }

    int countTrues(boolean[] bs) {
        int cnt = 0;
        for (boolean b : bs) {
            if (b)
                cnt++;
        }
        return cnt;
    }

}
