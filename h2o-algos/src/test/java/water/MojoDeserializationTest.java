package water;

import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.attributes.parameters.KeyValue;
import hex.genmodel.attributes.parameters.ModelParameter;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.parse_test_file;
import static water.TestUtil.toMojo;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class MojoDeserializationTest {
    
    @Test
    public void testMonotoneConstraintDeserialization() {
        Scope.enter();
        try {
            Frame f = Scope.track(parse_test_file("smalldata/logreg/prostate.csv"));
            f.replace(f.find("CAPSULE"), f.vec("CAPSULE").toNumericVec());
            DKV.put(f);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._response_column = "CAPSULE";
            parms._train = f._key;
            parms._monotone_constraints = new hex.KeyValue[] {new hex.KeyValue("AGE", -1d)};
            parms._ignored_columns = new String[]{"ID"};
            parms._ntrees = 10;
            parms._seed = 42;

            GBMModel model = new GBM(parms).trainModel().get();
            Scope.track_generic(model);
            GbmMojoModel mojo = (GbmMojoModel) toMojo(model, "testMonotoneConstraintDeserialization", true);
            ModelParameter[] paramsFromMojo = mojo._modelAttributes.getModelParameters();
            boolean found = false;
            for (ModelParameter p : paramsFromMojo) {
                if (p.name.equals("monotone_constraints")) {
                    found = true;
                    Object[] value = (Object[]) p.getActualValue();
                    assertEquals(1, value.length);
                    KeyValue kv = (KeyValue) value[0]; 
                    assertEquals("AGE", kv.key);
                    assertEquals(-1, kv.value, 0d);
                }
            }
            assertTrue("monotone constraints not found in mojo params", found);
        } finally {
            Scope.exit();
        }
    }
}
