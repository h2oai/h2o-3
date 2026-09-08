package hex.deeplearning;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.utils.DistributionFamily;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.*;

/**
 * MOJO consistency test for GH-16851 on Deep Learning.
 * <p>
 * The DL MOJO never applies an offset at all ({@code DeeplearningMojoModel.score0} ignores its {@code offset}
 * argument) and destandardizes as {@code out/mul + sub}. In-cluster, a remove_offset model predicts
 * {@code out/mul} — the zero offset is pushed through DL's {@code (offset - sub) * mul} term, cancelling the
 * response-mean. So without dropping {@code _normrespsub} the MOJO would sit exactly {@code mean(response)}
 * away from the cluster. This pins that they agree.
 */
public class DeepLearningRemoveOffsetMojoTest extends TestUtil {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void mojoScoresWithoutOffsetAndMatchesInCluster() throws Exception {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 1.0 + 0.5 * (i % 7)); // strictly positive
      train.add("offset", offset);
      DKV.put(train);

      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = train._key;
      parms._response_column = "AGE";
      parms._offset_column = "offset";
      parms._remove_offset_effects = true;
      parms._distribution = DistributionFamily.gaussian;
      parms._hidden = new int[]{8, 8};
      parms._epochs = 20;
      parms._reproducible = true;
      parms._seed = 42;
      parms._stopping_rounds = 0;
      parms._overwrite_with_best_model = false;
      DeepLearningModel ro = (DeepLearningModel) Scope.track_generic(new DeepLearning(parms).trainModel().get());

      Frame inCluster = Scope.track(ro.score(train));

      MojoModel mojo = ro.toMojo();
      assertNull("remove_offset MOJO must not advertise an offset column", mojo.getOffsetName());
      assertFalse("remove_offset MOJO must not require an offset input", mojo.requiresOffset());

      EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper((GenModel) mojo);
      for (long r = 0; r < train.numRows(); r++) {
        RowData row = new RowData();
        for (String col : train.names()) {
          if (col.equals("offset")) continue; // deliberately absent — MOJO must not need it
          row.put(col, train.vec(col).at(r));
        }
        double mojoPred = wrapper.predictRegression(row).value;
        assertEquals("MOJO prediction must match in-cluster restricted prediction (row " + r + ")",
                inCluster.vec(0).at(r), mojoPred, 1e-5);
        // a caller-supplied offset must be ignored, or the MOJO silently disagrees with the cluster
        double mojoPredWithOffset = wrapper.predictRegression(row, train.vec("offset").at(r)).value;
        assertEquals("MOJO must ignore a caller-supplied offset (row " + r + ")",
                mojoPred, mojoPredWithOffset, 0.0);
      }
    } finally {
      Scope.exit();
    }
  }
}
