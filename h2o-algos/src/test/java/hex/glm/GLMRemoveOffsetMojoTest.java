package hex.glm;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.glm.GLMModel.GLMParameters;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.*;

/**
 * MOJO consistency test for GH-16851, GLM side. GlmMojoModel gained an {@code _offsetRemoved} guard
 * (eta += offset is skipped) but had no test; the GBM/GAM/XGBoost equivalents did. A MOJO exported from a
 * remove_offset_effects GLM must advertise no offset column, score without one, reproduce the in-cluster
 * restricted predictions, and ignore an offset a caller passes explicitly.
 */
public class GLMRemoveOffsetMojoTest extends TestUtil {

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

      GLMParameters parms = new GLMParameters(GLMParameters.Family.gaussian);
      parms._train = train._key;
      parms._response_column = "AGE";
      parms._offset_column = "offset";
      parms._remove_offset_effects = true;
      parms._ignored_columns = new String[]{"ID"};
      parms._lambda = new double[]{0};
      GLMModel ro = (GLMModel) Scope.track_generic(new GLM(parms).trainModel().get());

      Frame inCluster = Scope.track(ro.score(train));

      MojoModel mojo = ro.toMojo();
      assertNull("remove_offset MOJO must not advertise an offset column", mojo.getOffsetName());
      assertFalse("remove_offset MOJO must not require an offset input", mojo.requiresOffset());

      EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper((GenModel) mojo);
      for (long r = 0; r < train.numRows(); r++) {
        RowData row = new RowData();
        for (String col : train.names()) {
          if (col.equals("offset")) continue; // deliberately absent - MOJO must not need it
          row.put(col, train.vec(col).at(r));
        }
        double mojoPred = wrapper.predictRegression(row).value;
        assertEquals("MOJO prediction must match in-cluster restricted prediction (row " + r + ")",
                inCluster.vec(0).at(r), mojoPred, 1e-6);
        // A caller can still pass an offset explicitly (EasyPredictModelWrapper routes to
        // score0(row, offset, preds) when offset != 0); a remove_offset model must ignore it.
        assertEquals("MOJO must ignore a caller-supplied offset (row " + r + ")", mojoPred,
                wrapper.predictRegression(row, train.vec("offset").at(r)).value, 0.0);
      }
    } finally {
      Scope.exit();
    }
  }
}
