package water.parser;

import org.junit.Assert;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.Vec;
import water.TestUtil;

public class ParserStringTest extends TestUtil {
  @Test public void testStrings() {
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/junit/string_test.csv");

      //check dimensions
      int nlines = (int)fr.numRows();
      Assert.assertEquals(65005,nlines);
      Assert.assertEquals(7,fr.numCols());

      //check column types
      Vec[] vecs = fr.vecs();
      Assert.assertTrue(vecs[0].isString());
      Assert.assertTrue(vecs[1].isString());
      Assert.assertTrue(vecs[2].isString());
      Assert.assertTrue(vecs[3].isString());
      Assert.assertTrue(vecs[4].isString());
      Assert.assertTrue(vecs[5].isEnum());
      Assert.assertTrue(vecs[6].isString());

      //checks column counts - expects MAX_ENUM == 65000
      //Enum registration is racy so actual enum limit can exceed MAX by a few values
      Assert.assertTrue(65003 <= vecs[0].nzCnt()); //Col A lacks starting values
      Assert.assertTrue(65002 <= vecs[1].nzCnt()); //Col B has random missing values & dble quotes
      Assert.assertTrue(65005 <= vecs[2].nzCnt()); //Col C has all values & single quotes
      Assert.assertTrue(65002 <= vecs[3].nzCnt()); //Col D missing vals just prior to Enum limit
      Assert.assertTrue(65003 <= vecs[4].nzCnt()); //Col E missing vals just after Enum limit hit
      //Assert.assertTrue(65000 <= vecs[5].domain().length); //Col F cardinality just at Enum limit
      Assert.assertTrue(65003 <= vecs[6].nzCnt()); //Col G missing final values

      //spot check value parsing
      ValueString vs = new ValueString();
      Assert.assertEquals("A2", vecs[0].atStr(vs, 2).toString());
      Assert.assertEquals("B7", vecs[1].atStr(vs, 7).toString());
      Assert.assertEquals("'C65001'", vecs[2].atStr(vs, 65001).toString());
      Assert.assertEquals("E65004", vecs[4].atStr(vs, 65004).toString());
      Assert.assertNull(vecs[6].atStr(vs, 65004));

      fr.delete();
    } finally {
      if( fr != null ) fr.delete();
    }
  }
}
