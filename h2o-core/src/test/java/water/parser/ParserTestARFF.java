package water.parser;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

public class ParserTestARFF extends TestUtil {
  @Test @Ignore
  public void testSimple() {
    Frame fr_arff = parse_test_file("smalldata/junit/iris.arff");
    Frame fr_csv = parse_test_file("smalldata/junit/iris.csv");
    Assert.assertTrue(isBitIdentical(fr_arff, fr_csv));
    fr_arff.delete();
    fr_csv.delete();
  }
}
