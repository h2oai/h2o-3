package hex.tree.drf;

import hex.Model;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.TreeSHAP;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import hex.genmodel.easy.prediction.RegressionModelPrediction;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import water.test.util.NaiveTreeSHAP;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.io.IOException;

import static hex.genmodel.utils.DistributionFamily.gaussian;
import static hex.genmodel.utils.DistributionFamily.bernoulli;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;

/**
 * @author Navdeep Gill
 */
@RunWith(Enclosed.class)
public class DRFPredictContribsTest {

    public static class DRFPredictContribsNormalTest extends TestUtil {

        @BeforeClass
        public static void stall() {
            stall_till_cloudsize(1);
        }

        @Test
        public void testPredictContribsGaussian() {
            try {
                Scope.enter();
                Frame fr = Scope.track(parseTestFile("smalldata/junit/titanic_alt.csv"));
                DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
                parms._train = fr._key;
                parms._distribution = gaussian;
                parms._response_column = "age";
                parms._ntrees = 5;
                parms._max_depth = 4;
                parms._min_rows = 1;
                parms._nbins = 50;
                parms._score_each_iteration = true;
                parms._seed = 42;

                DRF job = new DRF(parms);
                DRFModel drf = job.trainModel().get();
                Scope.track_generic(drf);

                Frame adapted = new Frame(fr);
                drf.adaptTestForTrain(adapted, true, false);

                for (int i = 0; i < parms._ntrees; i++) {
                    new hex.tree.drf.DRFPredictContribsTest.CheckTreeSHAPTask(drf, i).doAll(adapted);
                }
            } finally {
                Scope.exit();
            }
        }

        @Test
        public void testScoreContributionsGaussian() throws IOException, PredictException {
            try {
                Scope.enter();
                Frame fr = Scope.track(parseTestFile("smalldata/junit/titanic_alt.csv"));
                DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
                parms._train = fr._key;
                parms._distribution = gaussian;
                parms._response_column = "age";
                parms._ntrees = 5;
                parms._max_depth = 4;
                parms._min_rows = 1;
                parms._nbins = 50;
                parms._score_each_iteration = true;
                parms._seed = 42;

                DRF job = new DRF(parms);
                DRFModel drf = job.trainModel().get();
                Scope.track_generic(drf);

                Frame contributions = drf.scoreContributions(fr, Key.<Frame>make("contributions_regression_titanic"));
                Scope.track(contributions);

                Frame contribsAggregated = new DRFPredictContribsTest.RowSumTask()
                        .doAll(Vec.T_NUM, contributions)
                        .outputFrame(null, new String[]{"predict"}, null);
                Scope.track(contribsAggregated);

                assertTrue(drf.testJavaScoring(fr, contribsAggregated, 1e-5));

                // Now test MOJO scoring
                EasyPredictModelWrapper.Config cfg = new EasyPredictModelWrapper.Config()
                        .setModel(drf.toMojo(true))
                        .setEnableContributions(true);
                EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(cfg);
                assertArrayEquals(contributions.names(), wrapper.getContributionNames());

                for (long row = 0; row < fr.numRows(); row++) {
                    RowData rd = toRowData(fr, drf._output._names, row);
                    RegressionModelPrediction pr = wrapper.predictRegression(rd);
                    for (int c = 0; c < contributions.numCols(); c++) {
                        assertArrayEquals("Contributions should match, row=" + row,
                                toNumericRow(contributions, row), ArrayUtils.toDouble(pr.contributions), 0);
                    }
                }
            } finally {
                Scope.exit();
            }
        }

        @Test
        public void testPredictContribsBinomial() {
            try {
                Scope.enter();
                Frame fr = Scope.track(parseTestFile("smalldata/junit/titanic_alt.csv"));
                int ci = fr.find("survived"); // Change survived to categorical
                fr.toCategoricalCol(ci);

                DRFModel.DRFParameters parms = new DRFModel.DRFParameters();

                parms._train = fr._key;
                parms._distribution = bernoulli;
                parms._response_column = "survived";
                parms._ntrees = 5;
                parms._max_depth = 4;
                parms._min_rows = 1;
                parms._nbins = 50;
                parms._score_each_iteration = true;
                parms._seed = 42;

                DRF job = new DRF(parms);
                DRFModel drf = job.trainModel().get();
                Scope.track_generic(drf);

                Frame adapted = new Frame(fr);
                drf.adaptTestForTrain(adapted, true, false);

                for (int i = 0; i < parms._ntrees; i++) {
                    new hex.tree.drf.DRFPredictContribsTest.CheckTreeSHAPTask(drf, i).doAll(adapted);
                }
            } finally {
                Scope.exit();
            }
        }

        @Test
        public void testScoreContributionsBinomial() throws IOException, PredictException {
            try {
                Scope.enter();
                Frame fr = Scope.track(parseTestFile("smalldata/junit/titanic_alt.csv"));
                int ci = fr.find("survived"); // Change survived to categorical
                fr.toCategoricalCol(ci);

                DRFModel.DRFParameters parms = new DRFModel.DRFParameters();

                parms._train = fr._key;
                parms._distribution = bernoulli;
                parms._response_column = "survived";
                parms._ntrees = 5;
                parms._max_depth = 8;
                parms._min_rows = 1;
                parms._nbins = 50;
                parms._score_each_iteration = true;
                parms._seed = 42;

                DRF job = new DRF(parms);
                DRFModel drf = job.trainModel().get();
                Scope.track_generic(drf);

                Frame contributions = drf.scoreContributions(fr, Key.<Frame>make("contributions_binomial_titanic"));
                Scope.track(contributions);

                Frame predsFromContribs = new DRFPredictContribsTest.BinomAggregateTask()
                        .doAll(new byte[]{Vec.T_CAT, Vec.T_NUM, Vec.T_NUM}, contributions)
                        .outputFrame(null, new String[]{"predict", "p0", "p1"}, new String[][]{new String[]{"0", "1"}, null, null});
                Scope.track(predsFromContribs);

                Model.JavaScoringOptions options = new Model.JavaScoringOptions();
                if (!Boolean.getBoolean("reproduce.PUBDEV-8264")) { // FIXME - works only by chance - fails on full data
                    options._fraction = 0.1;
                }
                options._abs_epsilon = 1e-5;
                assertTrue(drf.testJavaScoring(fr, predsFromContribs, 1e-5, options));

                // Now test MOJO scoring
                EasyPredictModelWrapper.Config cfg = new EasyPredictModelWrapper.Config()
                        .setModel(drf.toMojo(true))
                        .setEnableContributions(true);
                EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(cfg);
                assertArrayEquals(contributions.names(), wrapper.getContributionNames());

                for (long row = 0; row < fr.numRows(); row++) {
                    RowData rd = toRowData(fr, drf._output._names, row);
                    BinomialModelPrediction pr = wrapper.predictBinomial(rd);
                    for (int c = 0; c < contributions.numCols(); c++) {
                        assertArrayEquals("Contributions should match, row=" + row,
                                toNumericRow(contributions, row), ArrayUtils.toDouble(pr.contributions), 0);
                    }
                }
            } finally {
                Scope.exit();
            }
        }

        @Test(expected = UnsupportedOperationException.class)
        public void testScoreContributionsBinomialDoubleTreesFail() {
            try {
                Scope.enter();
                Frame fr = Scope.track(parseTestFile("smalldata/junit/titanic_alt.csv"));
                fr.toCategoricalCol("survived");

                DRFModel.DRFParameters parms = new DRFModel.DRFParameters();

                parms._train = fr._key;
                parms._response_column = "survived";
                parms._ntrees = 1;
                parms._max_depth = 4;
                parms._binomial_double_trees = true;
                parms._seed = 42;

                DRF job = new DRF(parms);
                DRFModel drf = job.trainModel().get();
                Scope.track_generic(drf);

                drf.scoreContributions(fr, Key.make("contributions_binomial_titanic"));
            } finally {
                Scope.exit();
            }
        }

        @Test(expected = IOException.class)
        public void testScoreContributionsBinomialDoubleTreesMojoFail() throws IOException {
            try {
                Scope.enter();
                Frame fr = Scope.track(parseTestFile("smalldata/junit/titanic_alt.csv"));
                fr.toCategoricalCol("survived");

                DRFModel.DRFParameters parms = new DRFModel.DRFParameters();

                parms._train = fr._key;
                parms._response_column = "survived";
                parms._ntrees = 1;
                parms._max_depth = 4;
                parms._binomial_double_trees = true;
                parms._seed = 42;

                DRFModel drf = Scope.track_generic(new DRF(parms).trainModel().get());

                new EasyPredictModelWrapper.Config()
                        .setModel(drf.toMojo())
                        .setEnableContributions(true);
            } finally {
                Scope.exit();
            }
        }
    }

    private static class CheckTreeSHAPTask extends MRTask<DRFPredictContribsTest.CheckTreeSHAPTask> {
        final DRFModel _model;
        final int _tree;

        transient SharedTreeNode[] _nodes;

        private CheckTreeSHAPTask(DRFModel model, int tree) {
            _model = model;
            _tree = tree;
        }

        @Override
        protected void setupLocal() {
            SharedTreeSubgraph tree = _model.getSharedTreeSubgraph(_tree, 0);
            _nodes = tree.getNodes();
        }

        @Override
        public void map(Chunk[] cs) {
            final TreeSHAP<double[], SharedTreeNode, SharedTreeNode> treeSHAP = new TreeSHAP<>(_nodes);
            final NaiveTreeSHAP<double[], SharedTreeNode, SharedTreeNode> naiveTreeSHAP = new NaiveTreeSHAP<>(_nodes, _nodes, 0);

            final double[] row = MemoryManager.malloc8d(cs.length);
            final float[] contribs = MemoryManager.malloc4f(cs.length);
            final double[] naiveContribs = MemoryManager.malloc8d(cs.length);
            for (int i = 0; i < cs[0]._len; i++) {
                for (int j = 0; j < cs.length; j++) {
                    row[j] = cs[j].atd(i);
                    contribs[j] = 0;
                    naiveContribs[j] = 0;
                }

                treeSHAP.calculateContributions(row, contribs);

                // calculate the same using Naive implementation
                double expValPred = naiveTreeSHAP.calculateContributions(row, naiveContribs);
                double contribPred = ArrayUtils.sum(naiveContribs);
                // consistency check of naive output
                Assert.assertEquals(expValPred, contribPred, 1e-5);

                // compare naive and actual contributions
                Assert.assertArrayEquals(naiveContribs, ArrayUtils.toDouble(contribs), 1e-5);
            }
        }
    }

    private static class RowSumTask extends MRTask<DRFPredictContribsTest.RowSumTask> {
        @Override
        public void map(Chunk[] cs, NewChunk nc) {
            for (int i = 0; i < cs[0]._len; i++) {
                double sum = 0;
                for (Chunk c : cs)
                    sum += c.atd(i);
                nc.addNum(sum);
            }
        }
    }

    private static class BinomAggregateTask extends MRTask<DRFPredictContribsTest.BinomAggregateTask> {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int i = 0; i < cs[0]._len; i++) {
                double p1 = 0;
                for (Chunk c : cs)
                    p1 += c.atd(i);
                ncs[0].addNA();
                ncs[1].addNum(1-p1);
                ncs[2].addNum(p1);
            }
        }
    }


}
