package water.parser;

import static org.junit.Assert.*;
import org.junit.*;
import java.io.File;

import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;

public class ParseCompressedAndXLSTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(1); }

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

  @Test public void  testXLSBadArgs(){
    Frame k1 = null;
    try {
      File f = find_test_file("smalldata/airlines/AirlinesTest.csv.zip");
      NFSFileVec nfs = NFSFileVec.make(f);
      ParseSetup setup = new ParseSetup( true, // is valid
                                         0,    // invalidLines
                                         1,    // headerlines
                                         null, // errors
                                         ParserType.XLS,
                                         (byte)52, // sep; ascii '4'
                                         12,       // ncols
                                         true,     // singleQuotes 
                                         new String[]{"fYear","fMonth","fDayofMonth","fDayOfWeek","DepTime","ArrTime","UniqueCarrier","Origin","Dest","Distance","IsDepDelayed","IsDepDelayed_REC"},
                                         null, 
                                         null, 
                                         -1, // check header
                                         null);
      k1 = ParseDataset.parse(Key.make(), new Key[]{nfs._key}, true, setup, true).get();
      assertEquals( 0,k1.numCols());
      assertEquals( 0,k1.numRows());
      k1.delete();
    } finally {
      if( k1 != null ) k1.delete();
    }
  }
}
