package hex.tree.drf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.util.Log;

import static org.junit.Assert.*;

public class DRFConcurrentTest extends TestUtil  {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testBuildSingle() {
    Scope.enter();
    try {
      Frame fr = parseTestFile(Key.make("prostate_single.hex"), "smalldata/logreg/prostate.csv");
      fr.remove("ID").remove();
      Scope.track(fr);
      DKV.put(fr);
      buildXValDRF(fr, "AGE");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testBuildConcurrent() {
    Scope.enter();
    try {
      Frame fr = parseTestFile(Key.make("prostate_concurrent.hex"), "smalldata/logreg/prostate.csv");
      Scope.track(fr);
      fr.remove("ID").remove();
      DKV.put(fr);
      TrainSingleFun fun = new TrainSingleFun(fr);
      H2O.submitTask(new LocalMR(fun, 100)).join();
    } finally {
      Scope.exit();
    }
  }

  private static class TrainSingleFun extends MrFun<TrainSingleFun> {
    private final Frame _train;
    private TrainSingleFun(Frame train) { _train = train; }
    public TrainSingleFun() { _train = null; }
    @Override
    protected void map(int id) {
      assert _train != null;
      buildXValDRF(_train, "AGE");
    }
  }

  private static void buildXValDRF(Frame train, String response) {
    DRFModel model = null;
    try {
      Scope.enter();
      // Configure DRF
      DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
      drf._train = train._key;
      drf._response_column = response;
      drf._seed = (1L<<32)|2;
      drf._nfolds = 5;
      drf._ntrees = 7;

      model = new DRF(drf).trainModel().get();

      assertNotNull(model);
      Log.info(model._output);

      assertEquals(5, model._output._cross_validation_models.length);
    } finally {
      if (model != null) {
        model.delete();
        model.deleteCrossValidationModels();
        model.deleteCrossValidationPreds();
      }
      Scope.exit();
    }
  }

}
