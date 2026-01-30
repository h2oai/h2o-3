package hex.genmodel.algos.xgboost;

import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.PredictContributions;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.RegressionModelPrediction;
import org.junit.Test;

import java.io.*;
import java.nio.file.Paths;

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
        for (int i = 0; i < doubles.length; i++) data.put(mojo._names[i], doubles[i]);
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
    
    /*
    Test for XGBoost model trained with offset and predicting with none zero offset. 
     */
    @Test
    public void testXGBWithOffset_NoneZeroOffset() throws Exception {
        String mojofile = String.valueOf(
            Paths.get(
                XGBoostJavaMojoModelTest.class.getClassLoader().getResource("hex/genmodel/algos/xgboost/XGBoostWithOffset.zip").toURI()
            ).toFile()
        );

        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
                .setModel(MojoModel.load(mojofile));
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

        String[][] inputs = new String[][]{
            {"1.0", "5.0", "A"},
            {"2.0", "4.0", "B"},
            {"3.0", "3.0", "A"},
        };
        
        double [] offsets = {0.1, 0.2, 0.3};
        double[] expected = {9.81646, 19.7366, 29.6913};
        double[] preds = new double[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            RowData row = new RowData();
            row.put("numeric1", inputs[i][0]);
            row.put("numeric2", inputs[i][1]);
            row.put("categorical", inputs[i][2]);
            preds[i] = model.predictRegression(row, offsets[i]).value;
        }

        assertArrayEquals(expected, preds, 0.0001);
    }

    /*
    Test for XGBoost model trained with offset and predicting with zero offset. 
     */
    @Test
    public void testXGBWithOffset_ZeroOffset() throws Exception {
        String mojofile = String.valueOf(
            Paths.get(
                XGBoostJavaMojoModelTest.class.getClassLoader().getResource("hex/genmodel/algos/xgboost/XGBoostWithOffset.zip").toURI()
            ).toFile()
        );

        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
                .setModel(MojoModel.load(mojofile));
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

        String[][] inputs = new String[][]{
            {"1.0", "5.0", "A"},
            {"2.0", "4.0", "B"},
            {"3.0", "3.0", "A"},
        };
        
        double[] offsets = {0.0, 0.0, 0.0};
        double[] expected = {9.7164, 19.5366, 29.3913};
        double[] preds = new double[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            RowData row = new RowData();
            row.put("numeric1", inputs[i][0]);
            row.put("numeric2", inputs[i][1]);
            row.put("categorical", inputs[i][2]);
            preds[i] = model.predictRegression(row, offsets[i]).value;
        }

        assertArrayEquals(expected, preds, 0.0001);
    }

    /*
    Test for XGBoost model trained without offset and predicting with zero offset. 
     */
    @Test
    public void testXGBWithoutOffset_ZeroOffset() throws Exception {
        String mojofile = String.valueOf(
            Paths.get(
                XGBoostJavaMojoModelTest.class.getClassLoader().getResource("hex/genmodel/algos/xgboost/XGBoostWithoutOffset.zip").toURI()
            ).toFile()
        );

        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
            .setModel(MojoModel.load(mojofile));
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

        String[][] inputs = new String[][]{
            {"1.0", "5.0", "A"},
            {"2.0", "4.0", "B"},
            {"3.0", "3.0", "A"},
        };

        double[] offsets = {0.0, 0.0, 0.0};
        double[] expected = {9.8089, 19.7527, 29.6871};
        double[] preds = new double[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            RowData row = new RowData();
            row.put("numeric1", inputs[i][0]);
            row.put("numeric2", inputs[i][1]);
            row.put("categorical", inputs[i][2]);
            preds[i] = model.predictRegression(row, offsets[i]).value;
        }

        assertArrayEquals(expected, preds, 0.0001);
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
