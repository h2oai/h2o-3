package hex.pdp;

import hex.PartialDependence;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.TwoDimTable;

public class PartialDependenceTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void prostateBinary() {
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    try {
      // Frame
      fr = parse_test_file("smalldata/prostate/prostate.csv");
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
      fr = parse_test_file("smalldata/prostate/prostate.csv");
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


  @Test public void prostateBinaryPickCols() {
    Frame fr=null;
    GBMModel model=null;
    PartialDependence partialDependence = null;
    try {
      // Frame
      fr = parse_test_file("smalldata/prostate/prostate.csv");
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

      Assert.assertTrue(partialDependence._partial_dependence_data.length == 2);

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
      fr = parse_test_file("smalldata/prostate/prostate.csv");
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
      fr = parse_test_file("smalldata/prostate/prostate.csv");
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
        fr = parse_test_file("smalldata/junit/weather.csv");

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
