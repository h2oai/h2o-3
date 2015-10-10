package water.parser;

import org.junit.*;

import water.*;
import water.fvec.*;
import water.util.Log;

import java.io.File;

public class ParserTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(5); }
  private final double NaN = Double.NaN;
  private final char[] SEPARATORS = new char[] {',', ' '};

  // Make a ByteVec with the specific Chunks
  public static Key makeByteVec(String... data) {
    Futures fs = new Futures();
    long[] espc  = new long[data.length+1];
    for( int i = 0; i < data.length; ++i ) espc[i+1] = espc[i]+data[i].length();
    Key k = Vec.newKey();
    ByteVec bv = new ByteVec(k,Vec.ESPC.rowLayout(k,espc));
    DKV.put(k,bv,fs);
    for( int i = 0; i < data.length; ++i ) {
      Key ck = bv.chunkKey(i);
      DKV.put(ck, new Value(ck,new C1NChunk(data[i].getBytes())),fs);
    }
    fs.blockForPending();
    return k;
  }

  public static boolean compareDoubles(double a, double b, double threshold) {
    if( a==b ) return true;
    if( ( Double.isNaN(a) && !Double.isNaN(b)) ||
        (!Double.isNaN(a) &&  Double.isNaN(b)) ) return false;
    return !Double.isInfinite(a) && !Double.isInfinite(b) &&
           Math.abs(a-b)/Math.max(Math.abs(a),Math.abs(b)) < threshold;
  }
  private static void testParsed(Key k, double[][] expected) {
    testParsed(k,expected, expected.length);
  }
  private static void testParsed(Key k, double[][] expected, int len) {
    Frame fr = DKV.get(k).get();
    testParsed(fr,expected,len);
  }
  static void testParsed(Frame fr, double[][] expected, int len) {
    Assert.assertEquals(len,fr.numRows());
    Assert.assertEquals(expected[0].length,fr.numCols());
    for( int j = 0; j < fr.numCols(); ++j ) {
      Vec vec = fr.vecs()[j];
      for( int i = 0; i < expected.length; ++i ) {
        double pval = vec.at(i);
        if( Double.isNaN(expected[i][j]) )
          Assert.assertTrue(i+" -- "+j, vec.isNA(i));
        else
          Assert.assertTrue(expected[i][j]+" -- "+pval,compareDoubles(expected[i][j],pval,0.0000001));
      }
    }
    fr.delete();
  }

  @Test public void testBasic() {
    String[] data = new String[] {
        "1|2|3\n1|2|3",
        "4|5|6",
        "4|5.2|",
        "asdf|qwer|1",
        "1.1",
        "1.1|2.1|3.4",
    };

    double[][] exp = new double[][] {
      ard(1.0, 2.0, 3.0),
      ard(1.0, 2.0, 3.0),
      ard(4.0, 5.0, 6.0),
      ard(4.0, 5.2, NaN),
      ard(NaN, NaN, 1.0),
      ard(1.1, NaN, NaN),
      ard(1.1, 2.1, 3.4),
    };

    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);

      StringBuilder sb1 = new StringBuilder();
      for( String ds : dataset ) sb1.append(ds).append("\n");
      Key k1 = makeByteVec(sb1.toString());
      Key r1 = Key.make("r1");
      ParseDataset.parse(r1, k1);
      testParsed(r1,exp);

      StringBuilder sb2 = new StringBuilder();
      for( String ds : dataset ) sb2.append(ds).append("\r\n");
      Key k2 = makeByteVec(sb2.toString());
      Key r2 = Key.make("r2");
      ParseDataset.parse(r2, k2);
      testParsed(r2,exp);
    }
  }

  @Test public void testChunkBoundaries() {
    String[] data = new String[] {
      "1|2|3\n1|2|3\n",
      "1|2|3\n1|2", "|3\n1|1|1\n",
      "2|2|2\n2|3|", "4\n3|3|3\n",
      "3|4|5\n5",
      ".5|2|3\n5.","5|2|3\n55e-","1|2.0|3.0\n55e","-1|2.0|3.0\n55","e-1|2.0|3.0\n"

    };
    double[][] exp = new double[][] {
      ard(1.0, 2.0, 3.0),
      ard(1.0, 2.0, 3.0),
      ard(1.0, 2.0, 3.0),
      ard(1.0, 2.0, 3.0),
      ard(1.0, 1.0, 1.0),
      ard(2.0, 2.0, 2.0),
      ard(2.0, 3.0, 4.0),
      ard(3.0, 3.0, 3.0),
      ard(3.0, 4.0, 5.0),
      ard(5.5, 2.0, 3.0),
      ard(5.5, 2.0, 3.0),
      ard(5.5, 2.0, 3.0),
      ard(5.5, 2.0, 3.0),
      ard(5.5, 2.0, 3.0),
    };

    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);
      Key k = makeByteVec(dataset);
      Key r3 = Key.make();
      ParseDataset.parse(r3, k);
      testParsed(r3,exp);
    }
  }

  @Test public void testChunkBoundariesMixedLineEndings() {
    String[] data = new String[] {
      "1|2|3\n4|5|6\n7|8|9",
      "\r\n10|11|12\n13|14|15",
      "\n16|17|18\r",
      "\n19|20|21\n",
      "22|23|24\n25|26|27\r\n",
      "28|29|30"
    };
    double[][] exp = new double[][] {
      ard(1, 2, 3),
      ard(4, 5, 6),
      ard(7, 8, 9),
      ard(10, 11, 12),
      ard(13, 14, 15),
      ard(16, 17, 18),
      ard(19, 20, 21),
      ard(22, 23, 24),
      ard(25, 26, 27),
      ard(28, 29, 30),
    };

    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);
      Key k = makeByteVec(dataset);
      Key r4 = Key.make();
      ParseDataset.parse(r4, k);
      testParsed(r4,exp);
    }
  }

  @Test public void testNondecimalColumns() {
    String data[] = {
         "1| 2|one\n"
       + "3| 4|two\n"
       + "5| 6|three\n"
       + "7| 8|one\n"
       + "9| 10|two\n"
       + "11|12|three\n"
       + "13|14|one\n"
       + "15|16|\"two\"\n"
       + "17|18|\" four\"\n"
       + "19|20| three\n",
    };

    double[][] expDouble = new double[][] {
      ard(1, 2, 1), // preserve order
      ard(3, 4, 3),
      ard(5, 6, 2),
      ard(7, 8, 1),
      ard(9, 10, 3),
      ard(11,12, 2),
      ard(13,14, 1),
      ard(15,16, 3),
      ard(17,18, 0),
      ard(19,20, 2),
    };

    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);
      Key key = makeByteVec(dataset);
      Key r = Key.make();
      ParseDataset.parse(r, key);
      Frame fr = DKV.get(r).get();
      String[] cd = fr.vecs()[2].domain();
      Assert.assertEquals(" four",cd[0]);
      Assert.assertEquals("one",cd[1]);
      Assert.assertEquals("three",cd[2]);
      Assert.assertEquals("two",cd[3]);
      testParsed(r, expDouble);
    }
  }

  @Test public void testSingleEntryDatasets() {
    String[] numericDataset = new String[]{"10.9533122476"};
    Key k1 = makeByteVec(numericDataset);
    Key r1 = Key.make();
    ParseDataset.parse(r1, k1);
    Frame fr1 = DKV.get(r1).get();
    Assert.assertTrue(fr1.vecs()[0].isNumeric());
    Assert.assertTrue(fr1.numCols()  == 1);
    Assert.assertTrue(fr1.numRows()  == 1);
    fr1.delete();

    String[] dateDataset = new String[]{"3-Jan-06"};
    Key k2 = makeByteVec(dateDataset);
    Key r2 = Key.make();
    ParseDataset.parse(r2, k2);
    Frame fr2 = DKV.get(r2).get();
    Assert.assertTrue(fr2.vecs()[0].isTime());
    Assert.assertTrue(fr2.numCols()  == 1);
    Assert.assertTrue(fr2.numRows()  == 1);
    fr2.delete();

    String[] UUIDDataset = new String[]{"9ff4ed3a-6b00-4130-9aca-2ed897305fd1"};
    Key k3 = makeByteVec(UUIDDataset);
    Key r3 = Key.make();
    ParseDataset.parse(r3, k3);
    Frame fr3 = DKV.get(r3).get();
    Assert.assertTrue(fr3.numCols()  == 1);
    Assert.assertTrue(fr3.numRows()  == 1);
    Assert.assertTrue(fr3.vecs()[0].isUUID());
    fr3.delete();

    String[] categoricalDataset = new String[]{"Foo-bar"};
    Key k4 = makeByteVec(categoricalDataset);
    Key r4 = Key.make();
    ParseDataset.parse(r4, k4);
    Frame fr4 = DKV.get(r4).get();
    Assert.assertTrue(fr4.numCols()  == 1);
    Assert.assertTrue(fr4.numRows()  == 1);
    Assert.assertTrue(fr4.vecs()[0].isCategorical());
    String[] dom = fr4.vecs()[0].domain();
    Assert.assertTrue(dom.length == 1);
    Assert.assertEquals("Foo-bar", dom[0]);
    fr4.delete();
  }

  @Test public void testNumberFormats(){
    String [] data = {"+.6e102|+.7e102|+.8e102\n.6e102|.7e102|.8e102\n"};
    double[][] expDouble = new double[][] {
      ard(+.6e102,.7e102,.8e102), // preserve order
      ard(+.6e102, +.7e102,+.8e102),
    };
    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);
      Key key = makeByteVec(dataset);
      Key r = Key.make();
      ParseDataset.parse(r, key);
      testParsed(r, expDouble);
    }
  }
 @Test public void testMultipleNondecimalColumns() {
    String data[] = {
        "foo| 2|one\n"
      + "bar| 4|two\n"
      + "foo| 6|three\n"
      + "bar| 8|one\n"
      + "bar|ten|two\n"
      + "bar| 12|three\n"
      + "foobar|14|one\n",
    };
    double[][] expDouble = new double[][] {
      ard(1, 2, 0), // preserve order
      ard(0, 4, 2),
      ard(1, 6, 1),
      ard(0, 8, 0),
      ard(0, NaN, 2),
      ard(0, 12, 1),
      ard(2, 14, 0),
    };


    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);
      Key key = makeByteVec(dataset);
      Key r = Key.make();
      ParseDataset.parse(r, key);
      Frame fr = DKV.get(r).get();
      String[] cd = fr.vecs()[2].domain();
      Assert.assertEquals("one",cd[0]);
      Assert.assertEquals("three",cd[1]);
      Assert.assertEquals("two",cd[2]);
      cd = fr.vecs()[0].domain();
      Assert.assertEquals("bar",cd[0]);
      Assert.assertEquals("foo",cd[1]);
      Assert.assertEquals("foobar",cd[2]);
      testParsed(r, expDouble);
    }
  }


  // Test if the empty column is correctly handled.
  // NOTE: this test makes sense only for comma separated columns
  @Test public void testEmptyColumnValues() {
    String data[] = {
        "1,2,3,foo\n"
      + "4,5,6,bar\n"
      + "7,,8,\n"
      + ",9,10\n"
      + "11,,,\n"
      + "0,0,0,z\n"
      + "0,0,0,z\n"
      + "0,0,0,z\n"
      + "0,0,0,z\n"
      + "0,0,0,z\n"
    };
    double[][] expDouble = new double[][] {
      ard(1, 2, 3, 1),
      ard(4, 5, 6, 0),
      ard(7, NaN, 8, NaN),
      ard(NaN, 9, 10, NaN),
      ard(11, NaN, NaN, NaN),
      ard(0, 0, 0, 2),
      ard(0, 0, 0, 2),
      ard(0, 0, 0, 2),
      ard(0, 0, 0, 2),
      ard(0, 0, 0, 2),
    };

    final char separator = ',';

    String[] dataset = getDataForSeparator(separator, data);
    Key key = makeByteVec(dataset);
    Key r = Key.make();
    ParseDataset.parse(r, key);
    Frame fr = DKV.get(r).get();
    String[] cd = fr.vecs()[3].domain();
    Assert.assertEquals("bar",cd[0]);
    Assert.assertEquals("foo",cd[1]);
    testParsed(r, expDouble);
  }


  @Test public void testBasicSpaceAsSeparator() {

    String[] data = new String[] {
      " 1|2|3",
      " 4 | 5 | 6",
      "4|5.2 ",
      "asdf|qwer|1",
      "1.1",
      "1.1|2.1|3.4",
    };
    double[][] exp = new double[][] {
      ard(1.0, 2.0, 3.0),
      ard(4.0, 5.0, 6.0),
      ard(4.0, 5.2, NaN),
      ard(NaN, NaN, 1.0),
      ard(1.1, NaN, NaN),
      ard(1.1, 2.1, 3.4),
    };

    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);
      StringBuilder sb = new StringBuilder();
      for( String ds : dataset ) sb.append(ds).append("\n");
      Key k = makeByteVec(sb.toString());
      Key r5 = Key.make();
      ParseDataset.parse(r5, k);
      testParsed(r5, exp);
    }
  }

  public static String[] getDataForSeparator(char sep, String[] data) {
    return getDataForSeparator('|', sep, data);
  }
  static String[] getDataForSeparator(char placeholder, char sep, String[] data) {
    String[] result = new String[data.length];
    for (int i = 0; i < data.length; i++) {
      result[i] = data[i].replace(placeholder, sep);
    }
    return result;
  }

  @Test public void testTimeParse() {
    Frame fr = parse_test_file("smalldata/junit/bestbuy_train_10k.csv.gz");
    fr.delete();
  }

  // TODO Update, originally tested categorical to string conversion
  // TODO now just tests missing values among strings
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
      Assert.assertTrue(vecs[5].isString());
      Assert.assertTrue(vecs[6].isString());

      //checks column counts - expects MAX_CATEGORICAL_COUNT == 65000
      //Categorical registration is racy so actual categorical limit can exceed MAX by a few values
      Assert.assertTrue(65003 <= vecs[0].nzCnt()); //ColV2 A lacks starting values
      Assert.assertTrue(65002 <= vecs[1].nzCnt()); //ColV2 B has random missing values & dble quotes
      Assert.assertTrue(65005 <= vecs[2].nzCnt()); //ColV2 C has all values & single quotes
      Assert.assertTrue(65002 <= vecs[3].nzCnt()); //ColV2 D missing vals just prior to Categorical limit
      Assert.assertTrue(65003 <= vecs[4].nzCnt()); //ColV2 E missing vals just after Categorical limit hit
      //Assert.assertTrue(65000 <= vecs[5].domain().length); //ColV2 F cardinality just at Categorical limit
      Assert.assertTrue(65003 <= vecs[6].nzCnt()); //ColV2 G missing final values

      //spot check value parsing
      BufferedString str = new BufferedString();
      Assert.assertEquals("A2", vecs[0].atStr(str, 2).toString());
      Assert.assertEquals("B7", vecs[1].atStr(str, 7).toString());
      Assert.assertEquals("'C65001'", vecs[2].atStr(str, 65001).toString());
      Assert.assertEquals("E65004", vecs[4].atStr(str, 65004).toString());
      Assert.assertNull(vecs[6].atStr(str, 65004));

      fr.delete();
    } finally {
      if( fr != null ) fr.delete();
    }
  }

  @Test public void testMixedSeps() {
    double[][] exp = new double[][] {
      ard(NaN,   1,   1),
      ard(NaN,   2, NaN),
      ard(  3, NaN,   3),
      ard(  4, NaN, NaN),
      ard(NaN, NaN, NaN),
      ard(NaN, NaN, NaN),
      ard(NaN, NaN,   6),
    };
    Frame fr = parse_test_file("smalldata/junit/is_NA.csv");
    testParsed(fr._key,exp, 25);
  }

  @Test public void testSVMLight() {
    String[] dataset = new String[] {
  //    " 1 2:.2 5:.5 9:.9\n",
  //    "-1 7:.7 8:.8 9:.9\n",
  //    "+1 1:.1 5:.5 6:.6\n"
      "1 2:.2 5:.5 9:.9\n-1 1:.1 4:.4 8:.8\n",
      "1 2:.2 5:.5 9:.9\n1 3:.3 6:.6\n",
      "-1 7:.7 8:.8 9:.9\n1 20:2.\n",
      "+1 1:.1 5:.5 6:.6 10:1\n1 19:1.9\n",
      "1 2:.2 5:.5 9:.9\n-1 1:.1 4:.4 8:.8\n",
      "1 2:.2 5:.5 9:.9\n1 3:.3 6:.6\n",
      "-1 7:.7 8:.8 9:.9\n1 20:2.\n",
      "+1 1:.1 5:.5 6:.6 10:1\n1 19:1.9\n",
      "1 2:.2 5:.5 9:.9\n-1 1:.1 4:.4 8:.8\n",
      "1 2:.2 5:.5 9:.9\n1 3:.3 6:.6\n",
      "-1 7:.7 8:.8 9:.9\n1 20:2.\n",
      "+1 1:.1 5:.5 6:.6 10:1\n1 19:1.9\n"
    };

    double[][] exp = new double[][] {
//      ard( 1., .0, .2, .0, .0, .5, .0, .0, .0, .9),
//      ard(-1., .0, .0, .0, .0, .0, .0, .7, .8, .9),
//      ard( 1., .1, .0, .0, .0, .5, .6, .0, .0, .0),
      ard(  1., .0, .2, .0, .0, .5, .0, .0, .0, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard( -1., .1, .0, .0, .4, .0, .0, .0, .8, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .2, .0, .0, .5, .0, .0, .0, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .3, .0, .0, .6, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard( -1., .0, .0, .0, .0, .0, .0, .7, .8, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .0, .0, .0, .0, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0,2.0),
      ard(  1., .1, .0, .0, .0, .5, .6, .0, .0, .0, 1, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .0, .0, .0, .0, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0,1.9, .0),
      ard(  1., .0, .2, .0, .0, .5, .0, .0, .0, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard( -1., .1, .0, .0, .4, .0, .0, .0, .8, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .2, .0, .0, .5, .0, .0, .0, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .3, .0, .0, .6, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard( -1., .0, .0, .0, .0, .0, .0, .7, .8, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .0, .0, .0, .0, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0,2.0),
      ard(  1., .1, .0, .0, .0, .5, .6, .0, .0, .0, 1, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .0, .0, .0, .0, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0,1.9, .0),
      ard(  1., .0, .2, .0, .0, .5, .0, .0, .0, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard( -1., .1, .0, .0, .4, .0, .0, .0, .8, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .2, .0, .0, .5, .0, .0, .0, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .3, .0, .0, .6, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard( -1., .0, .0, .0, .0, .0, .0, .7, .8, .9, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .0, .0, .0, .0, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .0,2.0),
      ard(  1., .1, .0, .0, .0, .5, .6, .0, .0, .0, 1, 0, 0, 0, 0, 0, 0, 0, 0, .0, .0),
      ard(  1., .0, .0, .0, .0, .0, .0, .0, .0, .0, 0, 0, 0, 0, 0, 0, 0, 0, 0,1.9, .0),
    };
    StringBuilder sb = new StringBuilder();
    for( String ds : dataset ) sb.append(ds).append("\n");
    Key k = makeByteVec(sb.toString());
    Key r1 = Key.make("r1");
    ParseDataset.parse(r1, k);
    testParsed(r1,exp);
  }

  // Mix of NA's, very large & very small, ^A Hive-style seperator, comments, labels
  @Test public void testParseMix() {
    double[][] exp = new double[][] {
      ard( 0      ,  0.5    ,  1      , 0),
      ard( 3      ,  NaN    ,  4      , 1),
      ard( 6      ,  NaN    ,  8      , 0),
      ard( 0.6    ,  0.7    ,  0.8    , 1),
      ard(+0.6    , +0.7    , +0.8    , 0),
      ard(-0.6    , -0.7    , -0.8    , 1),
      ard(  .6    ,   .7    ,   .8    , 0),
      ard(+ .6    ,  +.7    ,  +.8    , 1),
      ard(- .6    ,  -.7    ,  -.8    , 0),
      ard(+0.6e0  , +0.7e0  , +0.8e0  , 1),
      ard(-0.6e0  , -0.7e0  , -0.8e0  , 0),
      ard(  .6e0  ,   .7e0  ,   .8e0  , 1),
      ard(+ .6e0  ,  +.7e0  ,  +.8e0  , 0),
      ard( -.6e0  ,  -.7e0  ,  -.8e0  , 1),
      ard(+0.6e00 , +0.7e00 , +0.8e00 , 0),
      ard(-0.6e00 , -0.7e00 , -0.8e00 , 1),
      ard(  .6e00 ,   .7e00 ,   .8e00 , 0),
      ard( +.6e00 ,  +.7e00 ,  +.8e00 , 1),
      ard( -.6e00 ,  -.7e00 ,  -.8e00 , 0),
      ard(+0.6e-01, +0.7e-01, +0.8e-01, 1),
      ard(-0.6e-01, -0.7e-01, -0.8e-01, 0),
      ard(  .6e-01,   .7e-01,   .8e-01, 1),
      ard( +.6e-01,  +.7e-01,  +.8e-01, 0),
      ard( -.6e-01,  -.7e-01,  -.8e-01, 1),
      ard(+0.6e+01, +0.7e+01, +0.8e+01, 0),
      ard(-0.6e+01, -0.7e+01, -0.8e+01, 1),
      ard(  .6e+01,   .7e+01,   .8e+01, 0),
      ard( +.6e+01,  +.7e+01,  +.8e+01, 1),
      ard( -.6e+01,  -.7e+01,  -.8e+01, 0),
      ard(+0.6e102, +0.7e102, +0.8e102, 1),
      ard(-0.6e102, -0.7e102, -0.8e102, 0),
      ard(  .6e102,   .7e102,   .8e102, 1),
      ard( +.6e102,  +.7e102,  +.8e102, 0),
      ard( -.6e102,  -.7e102,  -.8e102, 1)
    };
    Frame fr = parse_test_file("smalldata/junit/test_parse_mix.csv");
    testParsed(fr._key, exp);
  }

  // Test of parsing numbers with many digits
  @Test public void testParseManyDigits1() {
    String pows10 =
      "1\n"+
      "10\n"+
      "100\n"+
      "1000\n"+
      "10000\n"+
      "100000\n"+
      "1000000\n"+
      "10000000\n"+
      "100000000\n"+
      "1000000000\n"+
      "10000000000\n"+
      "100000000000\n"+
      "1000000000000\n"+
      "10000000000000\n"+
      "100000000000000\n"+
      "1000000000000000\n"+
      "10000000000000000\n"+
      "100000000000000000\n"+
      "1000000000000000000\n"+
      "10000000000000000000\n"+
      "100000000000000000000\n"+
      "1000000000000000000000\n"+
      "10000000000000000000000\n"+
      "100000000000000000000000\n";
    double[][] pows10_exp = new double[][] {
      ard(1e0 ), ard(1e1 ), ard(1e2 ), ard(1e3 ), ard(1e4 ), ard(1e5 ), ard(1e6 ), ard(1e7 ), ard(1e8 ), ard(1e9 ),
      ard(1e10), ard(1e11), ard(1e12), ard(1e13), ard(1e14), ard(1e15), ard(1e16), ard(1e17), ard(1e18), ard(1e19),
      ard(1e20), ard(1e21), ard(1e22), ard(1e23),
    };
    Key k = makeByteVec(pows10);
    Key r1 = Key.make("r1");
    ParseDataset.parse(r1, k);
    testParsed(r1,pows10_exp);
  }

  // Test of parsing numbers with many digits
  @Test public void testParseManyDigits2() {
    String pows10 =
      "9\n"+
      "99\n"+
      "999\n"+
      "9999\n"+
      "99999\n"+
      "999999\n"+
      "9999999\n"+
      "99999999\n"+
      "999999999\n"+
      "9999999999\n"+
      "99999999999\n"+
      "999999999999\n"+
      "9999999999999\n"+
      "99999999999999\n"+
      "999999999999999\n"+
      "9999999999999999\n"+
      "99999999999999999\n"+
      "999999999999999999\n"+
      "9999999999999999999\n"+
      "99999999999999999999\n"+
      "999999999999999999999\n"+
      "9999999999999999999999\n"+
      "99999999999999999999999\n"+
      "999999999999999999999999\n";
    double[][] pows10_exp = new double[][] {
      ard(9L),
      ard(99L),
      ard(999L),
      ard(9999L),
      ard(99999L),
      ard(999999L),
      ard(9999999L),
      ard(99999999L),
      ard(999999999L),
      ard(9999999999L),
      ard(99999999999L),
      ard(999999999999L),
      ard(9999999999999L),
      ard(99999999999999L),
      ard(999999999999999L),
      ard(9999999999999999L),
      ard(99999999999999999L),
      ard(999999999999999999L),
      ard(9.99999999999999999e18),
      ard(9.99999999999999999e19),
      ard(9.99999999999999999e20),
      ard(9.99999999999999999e21),
      ard(9.99999999999999999e22),
      ard(9.99999999999999999e23),
    };
    Key k = makeByteVec(pows10);
    Key r1 = Key.make("r1");
    ParseDataset.parse(r1, k);
    testParsed(r1,pows10_exp);
  }

  // Test of parsing numbers with many digits
  @Test public void testParseManyDigits3() {
    String pows10 =
      "0.00000000000001\n"+
      "1000001\n"+
      "2000001\n"+
      "";
    double[][] pows10_exp = new double[][] {
      ard(1e-14),
      ard(1000001L),
      ard(2000001L),
    };
    Key k = makeByteVec(pows10);
    Key r1 = Key.make("r1");
    ParseDataset.parse(r1, k);
    testParsed(r1,pows10_exp);
  }

  // Test of parsing numbers with many digits
  @Test public void testParseManyDigits4() {
    String pows10 =
      "3\n"+
      "1e-18\n"+
      "1e-34\n"+
      "";
    double[][] pows10_exp = new double[][] {
      ard(3),
      ard(1e-18),
      ard(1e-34),
    };
    Key k = makeByteVec(pows10);
    Key r1 = Key.make("r1");
    ParseDataset.parse(r1, k);
    testParsed(r1,pows10_exp);
  }

  // if there's only 3 different things - 2 strings and one other things (number of string), then declare this column an categorical column
  @Test @Ignore public void testBinaryWithNA() {
    String[] data = new String[] {
            "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "T", "F", "0",
    };

    double[][] exp = new double[][] {
            ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN),
            ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN),
            ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN),
            ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN),
            ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN),
            ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(Double.NaN), ard(1), ard(0), ard(Double.NaN),
    };

    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);

      StringBuilder sb1 = new StringBuilder();
      for( String ds : dataset ) sb1.append(ds).append("\n");
      Key k1 = makeByteVec(sb1.toString());
      Key r1 = Key.make("r1");
      ParseDataset.parse(r1, k1);
      testParsed(r1,exp);

      StringBuilder sb2 = new StringBuilder();
      for( String ds : dataset ) sb2.append(ds).append("\r\n");
      Key k2 = makeByteVec(sb2.toString());
      Key r2 = Key.make("r2");
      ParseDataset.parse(r2, k2);
      testParsed(r2,exp);
    }
  }

  @Test public void testParseAll() {
    String[] files = new String[]{
            "smalldata/./airlines/allyears2k_headers.zip",
            "smalldata/./covtype/covtype.20k.data",
            "smalldata/./iris/iris.csv",
            "smalldata/./iris/iris_wheader.csv",
            "smalldata/./junit/benign.xls",
            "smalldata/./junit/bestbuy_train_10k.csv.gz",
            "smalldata/./junit/cars.csv",
            "smalldata/./junit/iris.csv",
            "smalldata/./junit/iris.csv.gz",
            "smalldata/./junit/iris.csv.zip",
            "smalldata/./junit/iris.xls",
            "smalldata/./junit/is_NA.csv",
            "smalldata/./junit/one-line-dataset-0.csv",
            "smalldata/./junit/one-line-dataset-1dos.csv",
            "smalldata/./junit/one-line-dataset-1unix.csv",
            "smalldata/./junit/one-line-dataset-2dos.csv",
            "smalldata/./junit/one-line-dataset-2unix.csv",
            "smalldata/./junit/parse_folder/prostate_0.csv",
            "smalldata/./junit/parse_folder/prostate_1.csv",
            "smalldata/./junit/parse_folder/prostate_2.csv",
            "smalldata/./junit/parse_folder/prostate_3.csv",
            "smalldata/./junit/parse_folder/prostate_4.csv",
            "smalldata/./junit/parse_folder/prostate_5.csv",
            "smalldata/./junit/parse_folder/prostate_6.csv",
            "smalldata/./junit/parse_folder/prostate_7.csv",
            "smalldata/./junit/parse_folder/prostate_8.csv",
            "smalldata/./junit/parse_folder/prostate_9.csv",
            "smalldata/./junit/parse_folder_gold.csv",
            "smalldata/./junit/pros.xls",
            "smalldata/./junit/syn_2659x1049.csv.gz",
            "smalldata/./junit/test_parse_mix.csv",
            "smalldata/./junit/test_quote.csv",
            "smalldata/./junit/test_time.csv",
            "smalldata/./junit/test_uuid.csv",
            "smalldata/./junit/time.csv",
            "smalldata/./junit/two-lines-dataset.csv",
            "smalldata/./junit/ven-11.csv",
            "smalldata/./logreg/prostate.csv",
    };
    for (String f : files) {
      for (boolean delete_on_done : new boolean[]{
              true,
//              false
      }) {
        for (int check_header : new int[]{
                ParseSetup.GUESS_HEADER,
//              ParseSetup.HAS_HEADER
        }) {
          try {
            Log.info("Trying to parse " + f);
            NFSFileVec nfs = NFSFileVec.make(find_test_file(f));
            Frame fr = ParseDataset.parse(Key.make(), new Key[]{nfs._key}, delete_on_done, true /*single quote*/, check_header);
            fr.delete();
          } catch (Throwable t) {
            throw Log.throwErr(t);
          }
        }
      }
    }
  }

  @Ignore //PUBDEV-1384 fails for cloudsize > 1
  @Test public void parseMNIST() {
    File train = find_test_file("bigdata/laptop/mnist/train.csv.gz");
    if (train != null) {
      NFSFileVec trainfv = NFSFileVec.make(train);
      Frame frame = ParseDataset.parse(Key.make(), trainfv._key);
      frame.delete();
    }
  }
}
