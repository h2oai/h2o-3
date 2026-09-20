package hex.tree.xgboost;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.utils.DistributionFamily;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.ExtensionManager;

import static org.junit.Assert.*;

/**
 * MOJO consistency test for GH-16851: a MOJO exported from a remove_offset_effects XGBoost model must
 * advertise no offset column, score WITHOUT an offset input, and reproduce the in-cluster restricted
 * predictions exactly (margin-trained booster must keep the explicit ZERO-margin path — a no-margin
 * predict would add base_score and silently diverge).
 */
public class XGBoostRemoveOffsetMojoTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    Assume.assumeTrue("XGBoost was not loaded!",
            ExtensionManager.getInstance().isCoreExtensionsEnabled(XGBoostExtension.NAME));
  }

  @Test
  public void mojoScoresWithoutOffsetAndMatchesInCluster() throws Exception {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._response_column = "AGE";
      parms._offset_column = "offset";
      parms._remove_offset_effects = true;
      parms._distribution = DistributionFamily.gaussian;
      parms._ntrees = 10;
      parms._max_depth = 4;
      parms._seed = 42;
      XGBoostModel ro = (XGBoostModel) Scope.track_generic(new XGBoost(parms).trainModel().get());

      Frame inCluster = Scope.track(ro.score(train));

      MojoModel mojo = ro.toMojo();
      assertNull("remove_offset MOJO must not advertise an offset column", mojo.getOffsetName());
      assertFalse("remove_offset MOJO must not require an offset input", mojo.requiresOffset());

      // score through the easy wrapper WITHOUT the offset column present — with the offset removed this
      // routes through the plain (formerly throwing) score0 path and must equal in-cluster predictions
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
        // GH-16851: a caller can still pass an offset explicitly (EasyPredictModelWrapper routes to
        // score0(row, offset, preds) when offset != 0); a remove_offset model must ignore it.
        assertEquals("MOJO must ignore a caller-supplied offset (row " + r + ")", mojoPred,
                wrapper.predictRegression(row, train.vec("offset").at(r)).value, 0.0);
      }
    } finally {
      Scope.exit();
    }
  }
}
