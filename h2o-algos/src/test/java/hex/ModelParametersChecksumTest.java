package hex;

import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import hex.tree.drf.DRFModel;
import water.*;
import water.fvec.Frame;

import static org.junit.Assert.assertEquals;

/**
 * Tests to catch problems with hash collisions on model parameters.
 */
public class ModelParametersChecksumTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }


  @Test
  public void tesChecksumCache(){
    GLMModel model = null;
    Frame fr = parse_test_file("smalldata/glm_test/prostate_cat_replaced.csv");
    try{
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._lambda_search = true;
      params._nfolds = 3;
      params._standardize = false;
      GLM glm = new GLM(params);
      model = glm.trainModel().get();
      final long checksum = glm._parms.checksum();
      final Key<Model>[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, Model.class) && ((Model)k._key.get())._parms.checksum() == checksum;
        }
      }).keys();
      assertEquals(1,modelKeys.length);
      assertEquals(model._key,modelKeys[0]);
    } finally {
      fr.delete();
      if(model != null) {
        for(Key k:model._output._cross_validation_models)
          Keyed.remove(k);
        model.delete();
      }
    }
  }
  @Test
  public void testPubDev2075() {
    Frame fr = null;

    try {
      fr = parse_test_file("smalldata/junit/cars_20mpg.csv");
      fr.replace(fr.find("cylinders"), fr.vec("cylinders").toCategoricalVec()).remove();

      DRFModel.DRFParameters p1 = new DRFModel.DRFParameters();
      p1._train = fr._key;
      p1._response_column = "economy_20mpg";
      p1._ignored_columns = new String[]{"name", "columns", "cylinders"};
      p1._ntrees = 2;
      p1._max_depth = 5;
      p1._nbins = 6;
      p1._mtries = 2;
      p1._seed = 8887264963748798740L;

      DRFModel.DRFParameters p2 = new DRFModel.DRFParameters();
      p2._train = fr._key;
      p2._response_column = "economy_20mpg";
      p2._ignored_columns = new String[]{"name", "columns", "cylinders"};
      p2._ntrees = 5;
      p2._max_depth = 1;
      p2._nbins = 3;
      p2._mtries = 4;
      p2._seed = 8887264963748798740L;

      Assert.assertNotEquals(p1.checksum(), p2.checksum());
    } finally {
      if (fr != null) {
        fr.delete();
      }
    }

  }

}
