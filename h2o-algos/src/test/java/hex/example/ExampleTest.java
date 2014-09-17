package hex.example;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

public class ExampleTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }
  
  @Test public void testIris() {
    ExampleModel kmm = null;
    Frame fr = null;
    try {
      long start = System.currentTimeMillis();
      System.out.println("Start Parse");
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      System.out.println("Done Parse: "+(System.currentTimeMillis()-start));

      ExampleModel.ExampleParameters parms = new ExampleModel.ExampleParameters();
      parms._training_frame = fr;
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
