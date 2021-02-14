package hex.tree.gbm;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.TreeSHAP;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.RegressionModelPrediction;
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
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;

public class GBMPredictContribsTest extends TestUtil {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void testPredictContribsGaussian() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parse_test_file("smalldata/junit/titanic_alt.csv"));
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._distribution = gaussian;
      parms._response_column = "age";
      parms._ntrees = 5;
      parms._max_depth = 4;
      parms._min_rows = 1;
      parms._nbins = 50;
      parms._learn_rate = .2f;
      parms._score_each_iteration = true;
      parms._seed = 42;

      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();
      Scope.track_generic(gbm);

      Frame adapted = new Frame(fr);
      gbm.adaptTestForTrain(adapted, true, false);
      
      for (int i = 0; i < parms._ntrees; i++) {
        new CheckTreeSHAPTask(gbm, i).doAll(adapted);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testScoreContributionsGaussian() throws IOException, PredictException  {
    try {
      Scope.enter();
      Frame fr = Scope.track(parse_test_file("smalldata/junit/titanic_alt.csv"));
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._distribution = gaussian;
      parms._response_column = "age";
      parms._ntrees = 5;
      parms._max_depth = 4;
      parms._min_rows = 1;
      parms._nbins = 50;
      parms._learn_rate = .2f;
      parms._score_each_iteration = true;
      parms._seed = 42;

      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();
      Scope.track_generic(gbm);

      Frame contributions = gbm.scoreContributions(fr, Key.<Frame>make("contributions_titanic"));
      Scope.track(contributions);

      Frame contribsAggregated = new RowSumTask().doAll(Vec.T_NUM, contributions).outputFrame();
      Scope.track(contribsAggregated);

      assertTrue(gbm.testJavaScoring(fr, contribsAggregated, 1e-6));

      // Now test MOJO scoring
      EasyPredictModelWrapper.Config cfg = new EasyPredictModelWrapper.Config()
              .setModel(gbm.toMojo())
              .setEnableContributions(true);
      EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(cfg);
      assertArrayEquals(contributions.names(), wrapper.getContributionNames());

      for (long row = 0; row < fr.numRows(); row++) {
        RowData rd = toRowData(fr, gbm._output._names, row);
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

  private static class CheckTreeSHAPTask extends MRTask<CheckTreeSHAPTask> {
    final GBMModel _model;
    final int _tree;

    transient SharedTreeNode[] _nodes;

    private CheckTreeSHAPTask(GBMModel model, int tree) {
      _model = model;
      _tree = tree;
    }

    @Override
    protected void setupLocal() {
      SharedTreeSubgraph tree = _model.getSharedTreeSubgraph(_tree, 0);
      _nodes = tree.nodesArray.toArray(new SharedTreeNode[0]);
    }

    @Override
    public void map(Chunk[] cs) {
      final TreeSHAP<double[], SharedTreeNode, SharedTreeNode> treeSHAP = new TreeSHAP<>(_nodes, _nodes, 0);
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
        Assert.assertEquals(expValPred, contribPred, 1e-6);

        // compare naive and actual contributions
        Assert.assertArrayEquals(naiveContribs, ArrayUtils.toDouble(contribs), 1e-5);
      }
    }
  }
  
  private static class RowSumTask extends MRTask<RowSumTask> {
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
  
}
