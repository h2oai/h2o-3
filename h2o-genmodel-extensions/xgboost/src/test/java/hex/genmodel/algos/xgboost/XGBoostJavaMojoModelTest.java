package hex.genmodel.algos.xgboost;

import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.PredictContributions;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.RegressionModelPrediction;
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

  @Test
  public void testLeafNodeAssignments() throws Exception {
    MojoReaderBackend readerBackend = MojoReaderBackendFactory.createReaderBackend(
        getClass().getResource("xgboost_java.zip"),
        MojoReaderBackendFactory.CachingStrategy.MEMORY);
    XGBoostJavaMojoModel mojo = (XGBoostJavaMojoModel) MojoModel.load(readerBackend);
    double[] doubles = new double[]{1, 2, 3, 4, 5, 6, 7};
    SharedTreeMojoModel.LeafNodeAssignments res = mojo.getLeafNodeAssignments(doubles);
    assertNotNull(res._nodeIds);
    assertNotNull(res._paths);
    String[] paths = mojo.getDecisionPath(doubles);
    assertArrayEquals(paths, res._paths);
    RowData data = new RowData();
    for (int i = 0; i< doubles.length; i++) data.put(mojo._names[i], doubles[i]);
    EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(
        new EasyPredictModelWrapper.Config().setModel(mojo).setEnableLeafAssignment(true)
    );
    RegressionModelPrediction res2 = (RegressionModelPrediction) wrapper.predict(data);
    assertNotNull(res2.leafNodeAssignmentIds);
    assertNotNull(res2.leafNodeAssignments);
    assertArrayEquals(res._nodeIds, res2.leafNodeAssignmentIds);
    assertArrayEquals(res._paths, res2.leafNodeAssignments);
  }

  @Test
  public void testConvertWithWeights() throws Exception {
    MojoReaderBackend readerBackend = MojoReaderBackendFactory.createReaderBackend(
            XGBoostJavaMojoModelTest.class.getResource("xgboost_java.zip"),
            MojoReaderBackendFactory.CachingStrategy.MEMORY);
    XGBoostJavaMojoModel mojo = (XGBoostJavaMojoModel) MojoModel.load(readerBackend);
    SharedTreeGraph graph = mojo.convert(0, null);
    int expectedWeight = 380; // prostate dataset, 380 rows
    assertEquals(graph.subgraphArray.get(0).rootNode.getWeight(), expectedWeight, 0);
    double actualWeight = 0;
    for (SharedTreeNode node : graph.subgraphArray.get(0).nodesArray) {
      actualWeight += node.getWeight();
    }
    assertEquals(expectedWeight, actualWeight, 0);
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
