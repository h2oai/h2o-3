package hex.example;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Job;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ExampleTest {
  
    @Test
    public void testIris() {
        Scope.enter();
        try {
            Frame fr = Scope.track(parseTestFile("smalldata/iris/iris_wheader.csv"));

            ExampleModel.ExampleParameters parms = new ExampleModel.ExampleParameters();
            parms._train = fr._key;
            parms._max_iterations = 10;
            parms._response_column = "class";

            Job<ExampleModel> job = new Example(parms).trainModel();
            Scope.track_generic(job.get());
        } finally {
            Scope.exit();
        }
    }
}
