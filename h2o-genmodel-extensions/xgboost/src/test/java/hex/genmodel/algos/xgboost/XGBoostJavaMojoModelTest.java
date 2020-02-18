package hex.genmodel.algos.xgboost;

import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.PredictContributions;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.*;

public class XGBoostJavaMojoModelTest {

  @Test
  public void testObjFunction() { // make sure we have implementation for all supported obj functions
    for (XGBoostMojoModel.ObjectiveType type : XGBoostMojoModel.ObjectiveType.values()) {
      assertNotNull(type.getId());
      assertFalse(type.getId().isEmpty());
      // check we have an implementation of ObjFunction
      assertNotNull(XGBoostJavaMojoModel.getObjFunction(type.getId()));
    }
  }

  @Test
  public void testPredictContributionsSerialization() throws Exception {
    MojoReaderBackend readerBackend = MojoReaderBackendFactory.createReaderBackend(
            XGBoostJavaMojoModelTest.class.getResource("xgboost_java.zip"),
            MojoReaderBackendFactory.CachingStrategy.MEMORY);
    XGBoostJavaMojoModel mojo = (XGBoostJavaMojoModel) MojoModel.load(readerBackend);
    PredictContributions pc = mojo.makeContributionsPredictor();
    assertNotNull(pc);
    assertTrue(deserialize(serialize(pc)) instanceof PredictContributions);
  }

  private static byte[] serialize(Object o) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ObjectOutput out = new ObjectOutputStream(bos)) {
      out.writeObject(o);
    }
    return bos.toByteArray();
  }

  private static Object deserialize(byte[] bs) throws Exception {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bs)) {
      ObjectInput in = new ObjectInputStream(bis);
      return in.readObject();
    }
  }


}
