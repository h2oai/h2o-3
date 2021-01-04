package hex.pdp;

import hex.PartialDependence;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.TwoDimTable;
import static org.junit.Assert.assertTrue;

public class PartialDependenceTest extends TestUtil {
  static double _tot = 1e-10;
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void prostateBinary() {
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    try {
      // Frame
      fr = parseTestFile("smalldata/prostate/prostate.csv");
      for (String s : new String[]{"RACE","GLEASON","DPROS","DCAPS","CAPSULE"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(fr);

      // Model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[]{"ID"};
      parms._response_column = "CAPSULE";
      model = new GBM(parms).trainModel().get();

      // PartialDependence
      partialDependence = new PartialDependence(Key.<PartialDependence>make());
//      partialDependence._cols = model._output._names;
      partialDependence._nbins = 10;
      partialDependence._model_id = (Key) model._key;
      partialDependence._frame_id = fr._key;

      partialDependence.execImpl().get();
      for (TwoDimTable t : partialDependence._partial_dependence_data)
        Log.info(t);

    } finally {
      if (fr!=null) fr.remove();
      if (model!=null) model.remove();
      if (partialDependence !=null) partialDependence.remove();
    }
  }

  /**
   * This test will repeat the test in prostateBinary but with weights applied to the final prediction.
   * I will run the pdp with constant weights and without weights.  They should arrive at the same answer.
   */
  @Test public void prostateBinaryWeights() {
    Scope.enter();
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    PartialDependence partialDependenceW = null;
    try {
      // Frame
      fr = parseTestFile("smalldata/prostate/prostate.csv");
      for (String s : new String[]{"RACE","GLEASON","DPROS","DCAPS","CAPSULE"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
      }
      Scope.track(fr);

      Vec orig = fr.anyVec();
      Vec[] weights = new Vec[1];
      weights[0] = orig.makeCon(2.0); // constant weight, should give same answer as normal
      fr.add(new String[]{"weights"}, weights);
      Scope.track(orig);
      Scope.track(weights[0]);
      DKV.put(fr);
      Scope.track(orig);
      Scope.track(weights[0]);

      // Model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[]{"ID"};
      parms._response_column = "CAPSULE";
      model = new GBM(parms).trainModel().get();

      // PartialDependence
      partialDependence = new PartialDependence(Key.<PartialDependence>make());
      partialDependence._nbins = 10;
      partialDependence._model_id = (Key) model._key;
      partialDependence._cols = new String[]{"AGE", "RACE"};
      partialDependence._frame_id = fr._key;
      partialDependence.execImpl().get();

      partialDependenceW = new PartialDependence(Key.<PartialDependence>make());
      partialDependenceW._nbins = 10;
      partialDependenceW._model_id = (Key) model._key;
      partialDependenceW._cols = new String[]{"AGE", "RACE"};
      partialDependenceW._weight_column_index = fr.numCols()-1;
      partialDependenceW._frame_id = fr._key;
      partialDependenceW.execImpl().get();
      Scope.track_generic(model);
      Scope.track_generic(partialDependence);
      Scope.track_generic(partialDependenceW);
      assert equalTwoDimTables(partialDependence._partial_dependence_data[0], partialDependenceW._partial_dependence_data[0], 1e-10):
              "pdp with constant weight and without weight generated different answers for column AGE.";
      assert equalTwoDimTables(partialDependence._partial_dependence_data[1], partialDependenceW._partial_dependence_data[1], 1e-10):
              "pdp with constant weight and without weight generated different answers for column RACE.";
    } finally {
      Scope.exit();
    }
  }

  /**
   * This test will test the 2d pdp implementation for 2 sets of column pairs.  In order to reduce the test runtime, 
   * I will choose to specify a few split-points.
   */
  @Test public void prostate2Dpdp() {
    Scope.enter();
    try {
      // Frame
      Frame fr = parseTestFile("smalldata/prostate/prostate.csv");
      for (String s : new String[]{"RACE","GLEASON","DPROS","DCAPS","CAPSULE"}) { // convert to enum columns
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(fr);  // remember to put in DKF after change, dah!
      Scope.track(fr);
      
      // Build model Model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[]{"ID"};
      parms._response_column = "CAPSULE";
      GBMModel model = new GBM(parms).trainModel().get();
      
      // PartialDependence with only 1D pdp
      PartialDependence partialDependence1 = new PartialDependence(Key.<PartialDependence>make());
      partialDependence1._nbins = 10;
      partialDependence1._model_id = (Key) model._key;
      partialDependence1._cols = new String[]{"RACE", "VOL"};
      partialDependence1._frame_id = fr._key;
      partialDependence1.execImpl().get();
      
      // PartialDependence with 1D and 2D pdp
      PartialDependence partialDependence = new PartialDependence(Key.<PartialDependence>make());
      partialDependence._nbins = 10;
      partialDependence._model_id = (Key) model._key;
      partialDependence._cols = new String[]{"RACE", "VOL"};
      partialDependence._col_pairs_2dpdp = new String[2][];
      partialDependence._col_pairs_2dpdp[0] = new String[]{"AGE", "RACE"};
      partialDependence._col_pairs_2dpdp[1] = new String[]{"AGE", "PSA"};
      partialDependence._user_cols = new String[]{"AGE", "PSA"};
      partialDependence._num_user_splits = new int[] {3,3};
      partialDependence._user_splits_present = true;
      partialDependence._user_splits = new double[]{65, 61, 72, 1.4, 6.7, 20};
      partialDependence._frame_id = fr._key;
      partialDependence.execImpl().get();
      
      Scope.track_generic(model);
      Scope.track_generic(partialDependence);
      Scope.track_generic(partialDependence1);
      
      // Compare 1d pdp results with and without 2D pdp.  They should be the same.
      assert equalTwoDimTables(partialDependence._partial_dependence_data[0], partialDependence1._partial_dependence_data[0], 1e-10):
              "pdp from 1d pdp only and pdp from 2d pdp differ for col RACE.";
      assert equalTwoDimTables(partialDependence._partial_dependence_data[1], partialDependence1._partial_dependence_data[1], 1e-10):
              "pdp from 1d pdp only and pdp from 2d pdp differ for col VOL.";
      
      // manually generate the 2d-pdp point here and compare with our 2D pdp, they should equal
      double[] ageSplit = new double[]{65, 61, 72};
      double[] psaSplit = new double[]{1.4, 6.7, 20};
      double[] raceSplit = new double[]{0,1,2};
      double[] tstats = new double[3];
      
      // check the pair AGE and RACE first
      assertCorrect2Dpdp(fr, partialDependence._partial_dependence_data[2].getCellValues(), "AGE", "RACE", 
              false, true, ageSplit, raceSplit, model, _tot, tstats);  // check out the first 2d pdp betwee AGE and RACE
      assertCorrect2Dpdp(fr, partialDependence._partial_dependence_data[3].getCellValues(), "AGE", "PSA",
              false, false, ageSplit, psaSplit, model, _tot, tstats);  // check out the first 2d pdp betwee AGE and PSA
    } finally {
      Scope.exit();
    }
  }
  
  public void assertCorrect2Dpdp(Frame fr, IcedWrapper[][] cellVs, String col, String col2, boolean cat, boolean cat2,
                                 double[] colVals, double[] col2Vals, GBMModel model, double tot, double[] tstats) {
    for (int index=0; index < cellVs.length; index++) {
      int counter1 = index / col2Vals.length;
      int counter2 = index % col2Vals.length;
      assert colVals[counter1]==Double.valueOf(cellVs[index][0].toString());
      assert col2Vals[counter2]==Double.valueOf(cellVs[index][1].toString());
      grab2DStats(tstats, fr, colVals[counter1], col2Vals[counter2], col, col2, cat, cat2, model);
      // compare value from my manual to 2d pdp calculation
      assertTrue(Math.abs(tstats[0]-Double.valueOf(cellVs[index][2].toString()))<tot);
      assertTrue(Math.abs(tstats[1]-Double.valueOf(cellVs[index][3].toString()))<tot);
      assertTrue(Math.abs(tstats[2]-Double.valueOf(cellVs[index][4].toString()))<tot);
    }
  }

  public void grab2DStats(double[] tstats, Frame fr, double value, double value2, String col, String col2, 
                          boolean cat, boolean cat2, GBMModel model) {
    Scope.enter();
    try {
      final Frame tfr = fr._key.get().deepCopy(Key.make().toString());
      Scope.track(tfr);
      Frame test = new Frame(tfr.names(), tfr.vecs());
      Vec orig = test.remove(col);
      Vec cons = orig.makeCon(value);
      if (cat) cons.setDomain(tfr.vec(col).domain());
      test.add(col, cons);

      Vec cons2 = null;
      Vec orig2 = test.remove(col2);
      cons2 = orig2.makeCon(value2);
      if (cat2) cons2.setDomain(tfr.vec(col2).domain());
      test.add(col2, cons2);
      Scope.track(test);
      Scope.track(cons);
      Scope.track(orig);
      Scope.track(cons2);
      Scope.track(orig2);
      
      Frame preds = model.score(test);
      Scope.track(preds);
      tstats[0] = preds.vec(2).mean();
      tstats[1] = preds.vec(2).sigma();
      tstats[2] = tstats[1] / Math.sqrt(preds.numRows());
    } finally {
      Scope.exit();
    }
  }
  
  /**
   * This test will repeat the test in prostateBinary but with a row index passed in.
   */
  @Test public void prostateBinaryRow() {
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    try {
      // Frame
      fr = parseTestFile("smalldata/prostate/prostate.csv");
      for (String s : new String[]{"RACE","GLEASON","DPROS","DCAPS","CAPSULE"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(fr);

      // Model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[]{"ID"};
      parms._response_column = "CAPSULE";
      model = new GBM(parms).trainModel().get();

      // PartialDependence
      partialDependence = new PartialDependence(Key.<PartialDependence>make());
//      partialDependence._cols = model._output._names;
      partialDependence._row_index = 1;
      partialDependence._nbins = 10;
      partialDependence._model_id = (Key) model._key;
      partialDependence._frame_id = fr._key;

      partialDependence.execImpl().get();
      for (TwoDimTable t : partialDependence._partial_dependence_data)
        Log.info(t);

    } finally {
      if (fr!=null) fr.remove();
      if (model!=null) model.remove();
      if (partialDependence !=null) partialDependence.remove();
    }
  }

  @Test public void prostateBinaryPickCols() {
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    try {
      // Frame
      fr = parseTestFile("smalldata/prostate/prostate.csv");
      for (String s : new String[]{"RACE","GLEASON","DPROS","DCAPS","CAPSULE"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(fr);

      // Model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[]{"ID"};
      parms._response_column = "CAPSULE";
      model = new GBM(parms).trainModel().get();

      // PartialDependence
      partialDependence = new PartialDependence(Key.<PartialDependence>make());
      partialDependence._cols = new String[]{"DPROS", "GLEASON"}; //pick columns manually
      partialDependence._nbins = 10;
      partialDependence._model_id = (Key) model._key;
      partialDependence._frame_id = fr._key;

      partialDependence.execImpl().get();
      for (TwoDimTable t : partialDependence._partial_dependence_data)
        Log.info(t);

      assertTrue(partialDependence._partial_dependence_data.length == 2);

    } finally {
      if (fr!=null) fr.remove();
      if (model!=null) model.remove();
      if (partialDependence !=null) partialDependence.remove();
    }
  }

  /**
   * This test will test the pdp for regression outputs without weights and with constant weights.  The pdp generated
   * here should be the same.  In addition, I generated PDP for one numeric column and one enum column.
   */
  @Test public void prostateRegressionWeighted() {
    Scope.enter();
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    PartialDependence partialDependenceW = null;
    try {
      // Frame
      fr = parseTestFile("smalldata/prostate/prostate.csv");
      for (String s : new String[]{"RACE","GLEASON","DPROS","DCAPS","CAPSULE"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
      }
      Scope.track(fr);

      Vec orig = fr.anyVec();
      Vec[] weights = new Vec[1];
      weights[0] = orig.makeCon(2.0); // constant weight, should give same answer as normal
      fr.add(new String[]{"weights"}, weights);
      Scope.track(orig);
      Scope.track(weights[0]);
      DKV.put(fr);

      // Model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[]{"ID"};
      parms._response_column = "AGE";
      model = new GBM(parms).trainModel().get();

      Scope.track_generic(model);
      // PartialDependence without weight
      partialDependence = new PartialDependence(Key.<PartialDependence>make());
      partialDependence._nbins = 10;
      partialDependence._model_id = (Key) model._key;
      partialDependence._frame_id = fr._key;
      partialDependence._cols = new String[]{"AGE", "RACE"};
      partialDependence.execImpl().get();
      Scope.track_generic(partialDependence);
      // partialDependence with weights
      partialDependenceW = new PartialDependence(Key.<PartialDependence>make());
      partialDependenceW._nbins = 10;
      partialDependenceW._model_id = (Key) model._key;
      partialDependenceW._frame_id = fr._key;
      partialDependenceW._weight_column_index = fr.numCols()-1;
      partialDependenceW._cols = new String[]{"AGE", "RACE"};
      partialDependenceW.execImpl().get();
      Scope.track_generic(partialDependenceW);

      assert equalTwoDimTables(partialDependence._partial_dependence_data[0], partialDependenceW._partial_dependence_data[0], 1e-10):
              "pdp with constant weight and without weight generated different answers for column AGE.";
      assert equalTwoDimTables(partialDependence._partial_dependence_data[1], partialDependenceW._partial_dependence_data[1], 1e-10):
              "pdp with constant weight and without weight generated different answers for column RACE.";
    } finally {
      Scope.exit();
    }
  }

  @Test public void prostateRegression() {
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    try {
      // Frame
      fr = parseTestFile("smalldata/prostate/prostate.csv");
      for (String s : new String[]{"RACE","GLEASON","DPROS","DCAPS","CAPSULE"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(fr);

      // Model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[]{"ID"};
      parms._response_column = "AGE";
      model = new GBM(parms).trainModel().get();

      // PartialDependence
      partialDependence = new PartialDependence(Key.<PartialDependence>make());
      partialDependence._nbins = 10;
      partialDependence._model_id = (Key) model._key;
      partialDependence._frame_id = fr._key;

      partialDependence.execImpl().get();
      for (TwoDimTable t : partialDependence._partial_dependence_data)
        Log.info(t);

    } finally {
      if (fr!=null) fr.remove();
      if (model!=null) model.remove();
      if (partialDependence !=null) partialDependence.remove();
    }
  }

  @Test public void weatherBinary() {
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    try {
      // Frame
        fr = parseTestFile("smalldata/junit/weather.csv");

      // Model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[]{"Date","RISK_MM", "EvapMM",};
      parms._response_column = "RainTomorrow";
      model = new GBM(parms).trainModel().get();

      // PartialDependence
      partialDependence = new PartialDependence(Key.<PartialDependence>make());
      partialDependence._nbins = 33;
      partialDependence._cols = new String[]{"Sunshine","MaxWindPeriod","WindSpeed9am"};
      partialDependence._model_id = (Key) model._key;
      partialDependence._frame_id = fr._key;

      partialDependence.execImpl().get();
      for (TwoDimTable t : partialDependence._partial_dependence_data)
        Log.info(t);

    } finally {
      if (fr!=null) fr.remove();
      if (model!=null) model.remove();
      if (partialDependence !=null) partialDependence.remove();
    }
  }

}
