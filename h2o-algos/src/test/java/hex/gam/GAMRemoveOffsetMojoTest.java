package hex.gam;

import hex.ModelMetrics;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.glm.GLMModel.GLMParameters.Family;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.*;

/**
 * MOJO consistency test for GH-16851 on a NON-tree algo (GAM). A MOJO exported from a
 * remove_offset_effects GAM must advertise no offset column, score WITHOUT an offset input, and reproduce
 * the in-cluster restricted predictions. This exercises the generic scoring-contract change
 * (Model.offsetColumn() returns null for remove_offset models) through GAM's MOJO, which the tree/XGBoost
 * MOJO tests do not cover.
 */
public class GAMRemoveOffsetMojoTest extends TestUtil {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void mojoScoresWithoutOffsetAndMatchesInCluster() throws Exception {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      GAMModel.GAMParameters parms = new GAMModel.GAMParameters();
      parms._train = train._key;
      parms._response_column = "AGE";
      parms._offset_column = "offset";
      parms._remove_offset_effects = true;
      parms._ignored_columns = new String[]{"ID", "PSA", "CAPSULE"};
      parms._family = Family.gaussian;
      parms._gam_columns = new String[][]{{"PSA"}};
      parms._num_knots = new int[]{5};
      parms._lambda = new double[]{0};
      GAMModel ro = (GAMModel) Scope.track_generic(new GAM(parms).trainModel().get());

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
                inCluster.vec(0).at(r), mojoPred, 1e-6);
      }
    } finally {
      Scope.exit();
    }
  }
}
