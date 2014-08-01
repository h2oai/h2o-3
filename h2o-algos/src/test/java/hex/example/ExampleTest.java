package hex.example;

import org.junit.*;

import water.TestUtil;
import water.fvec.Frame;

public class ExampleTest extends TestUtil {
  @Test public void testIris() {
    ExampleModel kmm = null;
    Frame fr = null;
    try {
      long start = System.currentTimeMillis();
      System.out.println("Start Parse");
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      System.out.println("Done Parse: "+(System.currentTimeMillis()-start));

      ExampleModel.ExampleParameters parms = new ExampleModel.ExampleParameters();
      parms._src = fr._key;
      parms._max_iters = 10;

      Example job = new Example(parms).train();
      kmm = job.get();
      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }
}
