package water.parser;

import static org.junit.Assert.*;
import static water.parser.DefaultParserProviders.XLS_INFO;

import org.junit.*;

import water.*;
import water.fvec.Vec;
import water.fvec.Frame;
import water.fvec.NFSFileVec;

public class ParseCompressedAndXLSTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(5); }

  @Test public void testIris(){
    Frame k1 = null,k2 = null,k3 = null, k4 = null;
    try {
      k1 = parseTestFile("smalldata/junit/iris.csv");
      k2 = parseTestFile("smalldata/junit/iris.xls");
      k3 = parseTestFile("smalldata/junit/iris.csv.gz");
      k4 = parseTestFile("smalldata/junit/iris.csv.zip");
      TestUtil.assertBitIdentical(k1,k2);
      TestUtil.assertBitIdentical(k2,k3);
      TestUtil.assertBitIdentical(k3,k4);
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
      k1 = parseTestFile("smalldata/junit/benign.xls");
      assertEquals( 14,k1.numCols());
      assertEquals(203,k1.numRows());
      k1.delete();
      k1 = parseTestFile("smalldata/junit/pros.xls");
      assertEquals(  9,k1.numCols());
      assertEquals(380,k1.numRows());
    } finally {
      if( k1 != null ) k1.delete();
    }
  }

  @Test public void  testXLSBadArgs(){
    Frame k1 = null;
    try {
      NFSFileVec nfs = TestUtil.makeNfsFileVec("smalldata/airlines/AirlinesTest.csv.zip");
      byte[] ctypes = new byte[12];
      for(int i=0; i < 12; i++) ctypes[i] = Vec.T_NUM;
      ParseSetup setup = new ParseSetup(XLS_INFO,
                (byte) 52, // sep; ascii '4'
                true,     // singleQuotes
                ParseSetup.NO_HEADER, // check header
                12,       // ncols
                new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "DepTime", "ArrTime", "UniqueCarrier", "Origin", "Dest", "Distance", "IsDepDelayed", "IsDepDelayed_REC"},
                ctypes,
                null, null, null, null, null, false);
      try {
        k1 = ParseDataset.parse(Key.make(), new Key[]{nfs._key}, true, setup, true)._job.get();
        assertTrue("Should have thrown ParseException since file isn't XLS file",false); // fail - should've thrown
        k1.delete();
      } catch (Throwable t) {
        assertTrue(t instanceof ParseDataset.H2OParseException || t.getCause() instanceof ParseDataset.H2OParseException);
      }
    } finally {
      if( k1 != null ) k1.delete();
    }
  }
}
