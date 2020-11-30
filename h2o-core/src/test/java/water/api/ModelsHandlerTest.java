package water.api;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.Job;
import water.Scope;
import water.TestUtil;
import water.api.schemas3.ModelImportV3;
import water.api.schemas3.ModelsV3;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

public class ModelsHandlerTest extends TestUtil {

  @Rule
  public ExpectedException ee = ExpectedException.none();
  
  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testImportModelWithException() {
    ModelImportV3 importSpec = new ModelImportV3();
    importSpec.dir = "/definitely/invalid/directory";
    // the message should show what went wrong
    ee.expectMessage("Illegal argument: dir of function: importModel: water.api.FSIOException: FS IO Failure: \n" +
            " accessed path : file:/definitely/invalid/directory msg: File not found");
    new ModelsHandler().importModel(3, importSpec);
  }

  @Test
  public void testListAllModelsDoesNotFailWhenQuantileModelExist() {
    QuantileModel m = null;
    try {
      Scope.enter();
      final Frame train = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3, 4, 0))
              .withDataForCol(1, ar("A", "B", "C", "A", "B"))
              .withChunkLayout(1, 0, 0, 2, 1, 0, 1)
              .build());

      QuantileModel.QuantileParameters params = new QuantileModel.QuantileParameters();
      params._train = train._key;
      params._combine_method = QuantileModel.CombineMethod.INTERPOLATE;
      params._probs = new double[2];
      Job<QuantileModel> job = new Quantile(params).trainModel();
      m = job.get();
      new ModelsHandler().list(3, new ModelsV3());
    } finally {
      if (m != null) m.delete();
      Scope.exit();
    }
  }
}
