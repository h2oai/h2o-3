package water.parser;

import org.junit.*;

import water.TestUtil;
import water.fvec.*;

public class ParseTimeTest extends TestUtil {
  private double[] d(double... ds) { return ds; }

  // Parse click & query times from a subset of kaggle bestbuy data
  @Test public void testTimeParse1() {
    Frame fr = parse_test_file("smalldata/junit/test_time.csv");
    Frame fr2 = fr.subframe(new String[]{"click_time","query_time"});
    double[][] exp = new double[][] {
      d(1314945892533L, 1314945839752L ),
      d(1315250737042L, 1315250701187L ),
      d(1314215818091L, 1314215713012L ),
      d(1319552294722L, 1319552211759L ),
      d(1319552391697L, 1319552211759L ),
      d(1315436087956L, 1315436004353L ),
      d(1316974022603L, 1316973926996L ),
      d(1316806820871L, 1316806814845L ),
      d(1314650252903L, 1314650003249L ),
      d(1319608558683L, 1319608485926L ),
      d(1315770524139L, 1315770378466L ),
      d(1318983693919L, 1318983686057L ),
      d(1315158920427L, 1315158910874L ),
      d(1319844389203L, 1319844380358L ),
      d(1318232126858L, 1318232070708L ),
      d(1316841248965L, 1316841217043L ),
      d(1315681493645L, 1315681470805L ),
      d(1319395475074L, 1319395407011L ),
      d(1319395524416L, 1319395407011L ),
    };

    ParserTest.testParsed(fr2,exp,exp.length);
    fr.delete();
  }

  @Test public void testTimeParse2() {
    double[][] exp = new double[][] {
      d(1     ,     115200000L, 1136275200000L, 1136275200000L, 1 ),
      d(1500  ,  129625200000L, 1247641200000L, 1247641200000L, 0 ),
      d(15000 , 1296028800000L, 1254294000000L, 1254294000000L, 2 ),
    };
    ParserTest.testParsed(parse_test_file("smalldata/junit/ven-11.csv"),exp,exp.length);
  }

  private long[] l(long... ls) { return ls; }
  @Test public void testUUIDParse1() {
    long[][] exp = new long[][] {
      l(         1,0x9ff4ed3a6b004130L,0x9aca2ed897305fd1L,1),
      l(         2,0xac1e1ca35ca8438aL,0x85a48175ed5bb7ecL,1),
      l(         3,0x6870f256e1454d75L,0xadb099ccb77d5d3aL,0),
      l(         4,0xd8da52c1d1454dffL,0xb3d1127c6eb75d40L,1),
      l(         5,0x25ce1456546d4e35L,0xbddcd571b26581eaL,0),
      l(         6,0x2e1d193fd1da4664L,0x8a2bffdfe0aa7be3L,0), 
      l(1000010407,0x89e68530422e43baL,0xbd00aa3d8f2cfcaaL,1),
      l(1000024046,0x4055a53b411f46f0L,0x9d2ecf03bc95c080L,0),
      l(1000054511,0x49d14d8e5c42439dL,0xb4a8995e25b1602fL,0),
      l(1000065922,0x4e31b8aa4aa94e8bL,0xbe8f5cc6323235b4L,0),
      l(1000066478,0x2e1d193fd1da4664L,0x8a2bffdfe0aa7be3L,0),
      l(1000067268,0x25ce1456546d4e35L,0xbddcd571b26581eaL,0),
      l( 100007536,0xd8da52c1d1454dffL,0xb3d1127c6eb75d40L,1),
      l(1000079839,0x6870f256e1454d75L,0xadb099ccb77d5d3aL,0),
      l(  10000913,0xac1e1ca35ca8438aL,0x85a48175ed5bb7ecL,0),
      l(1000104538,0x9ff4ed3a6b004130L,0x9aca2ed897305fd1L,1), 
      l(         7,0x0000000000000000L,0x0000000000000000L,0),
      l(         8,0x8000000000000000L,0x0000000000000000L,0),
      l(         9,0xFFFFFFFFFFFFFFFFL,0xFFFFFFFFFFFFFFFFL,1),
    };
    Frame fr = parse_test_file("smalldata/junit/test_uuid.csv");
    Vec vecs[] = fr.vecs();
    try {
      Assert.assertEquals(exp.length,fr.numRows());
      for( int row = 0; row < exp.length; row++ ) {
        int col2 = 0;
        for( int col = 0; col < fr.numCols(); col++ ) {
          if( vecs[col].isUUID() ) {
            if( exp[row][col2]==C16Chunk._LO_NA && exp[row][col2+1]==C16Chunk._HI_NA ) {
              Assert.assertTrue("Frame " + fr._key + ", row=" + row + ", col=" + col + ", expect=NA",
                         vecs[col].isNA(row));
            } else {
              long lo = vecs[col].at16l(row);
              long hi = vecs[col].at16h(row);
              Assert.assertTrue("Frame " + fr._key + ", row=" + row + ", col=" + col + ", expect=" + Long.toHexString(exp[row][col2]) + ", found=" + lo,
                      exp[row][col2] == lo && exp[row][col2 + 1] == hi);
            }
            col2 += 2;
          } else {
            long lo = vecs[col].at8(row);
            Assert.assertTrue( "Frame "+fr._key+", row="+row+", col="+col+", expect="+exp[row][col2]+", found="+lo,
                        exp[row][col2]==lo );
            col2 += 1;
          }
        }
        Assert.assertEquals(exp[row].length,col2);
      }
    } finally {
      fr.delete();
    }
  }

  void runTests(){
    System.out.println("testTimeParse");
    testTimeParse1();
    testTimeParse2();
  }
}
