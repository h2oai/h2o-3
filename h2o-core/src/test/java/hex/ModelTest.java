package hex;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelTest {

    @Test
    public void testClearModelMetrics() {
        try {
            Scope.enter();
            final int nRows = 1000;
            final double[] feature = new double[nRows];
            final String[] response = new String[nRows];
            for (int i = 0; i < feature.length; i++) {
                feature[i] = i % 7;
                response[i] = i % 3 == 0 ? "A" : "B";
            }
            final Frame train = new TestFrameBuilder()
                    .withName("testFrame")
                    .withColNames("ColA", "Response")
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, feature)
                    .withDataForCol(1, response)
                    .build();
            assertEquals(feature.length, train.numRows());

            ModelBuilderTest.DummyModelParameters parameters = new ModelBuilderTest.DummyModelParameters();
            parameters._train = train._key;
            parameters._makeModel = true;
            parameters._makeModelMetrics = true;
            parameters._response_column = "Response";
            ModelBuilderTest.DummyModelBuilder modelBuilder = new ModelBuilderTest.DummyModelBuilder(parameters);
            final ModelBuilderTest.DummyModel model = modelBuilder.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
            assertNotNull(model._output._training_metrics);

            final Key<ModelMetrics>[] noneRemoved = model._output.clearModelMetrics(true);
            assertEquals(0, noneRemoved.length);

            final Key<ModelMetrics>[] trainingRemoved = model._output.clearModelMetrics(false);
            assertEquals(1, trainingRemoved.length);
        } finally {
            Scope.exit();
        }
    }

}
