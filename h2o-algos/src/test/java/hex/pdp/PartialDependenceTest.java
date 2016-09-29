package hex.pdp;

import hex.PartialDependence;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
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
