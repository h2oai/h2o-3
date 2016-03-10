package hex;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

public class CreateFrameTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void basicTest() {
    CreateFrame cf = new CreateFrame();
    cf.rows = 100;
    cf.cols = 10;
    cf.categorical_fraction = 0.1;
    cf.integer_fraction = 1 - cf.categorical_fraction;
    cf.binary_fraction = 0;
    cf.factors = 4;
    cf.response_factors = 2;
    cf.positive_response = false;
    cf.has_response = true;
    cf.seed = 1234;
    Frame frame = cf.execImpl().get();
    Assert.assertTrue(frame.numCols() == 11);
    Assert.assertTrue(frame.numRows() == 100);
    // Tries to print a frame
    frame.toString();
    frame.delete();
  }

  @Test public void binaryTest() {
    CreateFrame cf = new CreateFrame();
    cf.rows = 10;
    cf.cols = 100;
    cf.categorical_fraction = 0;
    cf.integer_fraction = 0;
    cf.binary_fraction = 1;
    cf.binary_ones_fraction = 1e-2;
    cf.factors = 1;
    cf.response_factors = 2;
    cf.positive_response = false;
    cf.has_response = true;
    cf.seed = 1234;
    Frame frame = cf.execImpl().get();
    Assert.assertTrue(frame.numCols() == 101);
    Assert.assertTrue(frame.numRows() == 10);
    // Print a fame
    frame.toString();
    frame.delete();
  }

  @Test public void timeTest() {
    CreateFrame cf = new CreateFrame();
    cf.rows = 10;
    cf.cols = 100;
    cf.categorical_fraction = 0;
    cf.integer_fraction = 0;
    cf.binary_fraction = 0;
    cf.time_fraction = 1;
    cf.binary_ones_fraction = 0;
    cf.factors = 1;
    cf.response_factors = 2;
    cf.positive_response = false;
    cf.has_response = true;
    cf.seed = 1234;
    Frame frame = cf.execImpl().get();
    Assert.assertTrue(frame.numCols() == 101);
    Assert.assertTrue(frame.numRows() == 10);
    // Print a fame
    frame.toString();
    frame.delete();
  }

  @Test public void stringTest() {
    CreateFrame cf = new CreateFrame();
    cf.rows = 10;
    cf.cols = 100;
    cf.categorical_fraction = 0;
    cf.integer_fraction = 0;
    cf.binary_fraction = 0;
    cf.time_fraction = 0;
    cf.string_fraction = 1;
    cf.binary_ones_fraction = 0;
    cf.factors = 1;
    cf.response_factors = 2;
    cf.positive_response = false;
    cf.has_response = true;
    cf.seed = 1234;
    Frame frame = cf.execImpl().get();
    Assert.assertTrue(frame.numCols() == 101);
    Assert.assertTrue(frame.numRows() == 10);
    // Print a fame
    frame.toString();
    frame.delete();
  }

  @Test public void everything() {
    CreateFrame cf = new CreateFrame();
    cf.rows = 100;
    cf.cols = 100;
    cf.categorical_fraction = 0.1;
    cf.integer_fraction = 0.1;
    cf.binary_fraction = 0.1;
    cf.time_fraction = 0.1;
    cf.string_fraction = 0.1;
    cf.binary_ones_fraction = 0.1;
    cf.factors = 5;
    cf.response_factors = 5;
    cf.positive_response = false;
    cf.has_response = true;
    cf.seed = 1234;
    Frame frame = cf.execImpl().get();
    Assert.assertTrue(frame.numCols() == 101);
    Assert.assertTrue(frame.numRows() == 100);
    // Print a fame
    frame.toString();
    frame.delete();
  }

  @Test public void trainTest() {
    CreateFrame cf = new CreateFrame();
    cf.rows = 5;
    cf.cols = 100;
    cf.categorical_fraction = 0.1;
    cf.integer_fraction = 0.1;
    cf.binary_fraction = 0.1;
    cf.time_fraction = 0.1;
    cf.string_fraction = 0.1;
    cf.binary_ones_fraction = 0.1;
    cf.factors = 5;
    cf.response_factors = 5;
    cf.positive_response = false;
    cf.has_response = true;
    cf.seed = 1234;
    cf.seed_for_column_types = 1234;
    Frame frame1 = cf.execImpl().get();

    cf = new CreateFrame();
    cf.rows = 5;
    cf.cols = 100;
    cf.categorical_fraction = 0.1;
    cf.integer_fraction = 0.1;
    cf.binary_fraction = 0.1;
    cf.time_fraction = 0.1;
    cf.string_fraction = 0.1;
    cf.binary_ones_fraction = 0.1;
    cf.factors = 5;
    cf.response_factors = 5;
    cf.positive_response = false;
    cf.has_response = true;
    cf.seed = 12345;
    cf.seed_for_column_types = 1234;
    Frame frame2 = cf.execImpl().get();
    frame1.toString();
    frame2.toString();
    for (int i=0;i<frame1.numCols();++i) {
      Assert.assertTrue(frame1.vec(i).get_type()==frame2.vec(i).get_type());
    }

    frame1.delete();
    frame2.delete();
  }

}
