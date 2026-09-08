package hex.coxph;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;
import static water.TestUtil.*;

/**
 * MOJO consistency test for GH-16851, CoxPH side. CoxPHMojoModel gained an {@code _offsetRemoved} guard
 * (the lp offset term is zeroed) but had no test, and CoxPH is the algo where in-cluster and MOJO offset
 * handling are two independent implementations - CoxPHScore appends a 0.0 coefficient, while the MOJO
 * zeroes the term in score0. That makes this the most valuable of the missing MOJO tests.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class CoxPHRemoveOffsetMojoTest {

  @Test
  public void mojoScoresWithoutOffsetAndMatchesInCluster() throws Exception {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/coxph_test/heart.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._train = train._key;
      parms._start_column = "start";
      parms._stop_column = "stop";
      parms._response_column = "event";
      parms._offset_column = "offset";
      parms._remove_offset_effects = true;
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;
      CoxPHModel ro = (CoxPHModel) Scope.track_generic(new CoxPH(parms).trainModel().get());

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
        double mojoLp = wrapper.predictCoxPH(row).value;
        assertEquals("MOJO lp must match in-cluster restricted lp (row " + r + ")",
                inCluster.vec(0).at(r), mojoLp, 1e-6);
        // A caller can still pass an offset explicitly; a remove_offset model must ignore it, otherwise
        // the MOJO silently disagrees with the cluster.
        assertEquals("MOJO must ignore a caller-supplied offset (row " + r + ")", mojoLp,
                wrapper.predictCoxPH(row, train.vec("offset").at(r)).value, 0.0);
      }
    } finally {
      Scope.exit();
    }
  }
}
