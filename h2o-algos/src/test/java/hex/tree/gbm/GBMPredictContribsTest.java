package hex.tree.gbm;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.TreeSHAP;
import hex.util.NaiveTreeSHAP;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.MRTask;
import water.MemoryManager;
import water.Scope;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;

import static hex.genmodel.utils.DistributionFamily.gaussian;

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
      final NaiveTreeSHAP<double[], SharedTreeNode, SharedTreeNode> naiveTreeSHAP = new NaiveTreeSHAP<>(_nodes, _nodes, 0, 0);

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
        Assert.assertArrayEquals(naiveContribs, ArrayUtils.toDouble(contribs), 1e-6);
      }
    }
  }
  
}
