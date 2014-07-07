package water.parser;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;

@Test(groups={"multi-node"})
public class ParseCompressedAndXLSTest extends TestUtil {
  ParseCompressedAndXLSTest() { super(3); }

  @Test public void testIris(){
    Frame k1 = null,k2 = null,k3 = null, k4 = null;
    try {
      k1 = parse_test_file("smalldata/junit/iris.csv");
      k2 = parse_test_file("smalldata/junit/iris.xls");
      k3 = parse_test_file("smalldata/junit/iris.csv.gz");
      k4 = parse_test_file("smalldata/junit/iris.csv.zip");
      assertTrue(isBitIdentical(k1,k2));
      assertTrue(isBitIdentical(k2,k3));
      assertTrue(isBitIdentical(k3,k4));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
      if( k3 != null ) k3.delete();
      if( k4 != null ) k4.delete();
    }
  }

  @Test public void  testXLS(){
    Frame k1 = null;
    try {
      k1 = parse_test_file("smalldata/junit/benign.xls");
      assertEquals( 14,k1.numCols());
      assertEquals(203,k1.numRows());
      k1.delete();
      k1 = parse_test_file("smalldata/junit/pros.xls");
      assertEquals(  9,k1.numCols());
      assertEquals(380,k1.numRows());
    } finally {
      if( k1 != null ) k1.delete();
    }
  }

  @Test public void testMixedCSVXLS(){
    Frame k1 = null;
    try {
      NFSFileVec nfs1 = NFSFileVec.make(find_test_file("smalldata/junit/iris.csv"));
      NFSFileVec nfs2 = NFSFileVec.make(find_test_file("smalldata/junit/iris.xls"));
      k1 = water.parser.ParseDataset2.parse(Key.make(),nfs1._key,nfs2._key);
      assertEquals(  5,k1.numCols());
      assertEquals(150,k1.numRows());
    } finally {
      if( k1 != null ) k1.delete();
    }
  }
}
