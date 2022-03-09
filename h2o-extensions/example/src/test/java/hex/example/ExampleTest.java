package hex.example;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ExampleTest extends TestUtil {

    @Test
    public void testTrainModel() {
        try {
            Scope.enter();
            Frame fr = parseTestFile("./smalldata/prostate/prostate.csv");
            Scope.track(fr);

            ExampleModel.ExampleParameters p = new ExampleModel.ExampleParameters();
            p._train = fr._key;

            ExampleModel exampleModel = new Example(p).trainModel().get();
            assertNotNull(exampleModel);
            Scope.track_generic(exampleModel);

            assertArrayEquals(
                    new double[]{380.0, 1.0, 79.0, 2.0, 4.0, 2.0, 139.7, 97.6, 9.0},
                    exampleModel._output._maxs, 0);
        } finally {
            Scope.exit();
        }
    }

}
