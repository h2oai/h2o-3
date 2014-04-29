package water.parser;

import org.junit.Assert;
import org.junit.Test;
import water.*;
import water.fvec.*;

public class ParserTest extends TestUtil {
  private final double NaN = Double.NaN;
  private final char[] SEPARATORS = new char[] {',', ' '};

  // Make a ByteVec with the specific Chunks
  private Key k(String... data) {
    Futures fs = new Futures();
    long[] espc  = new long[data.length+1];
    for( int i = 0; i < data.length; ++i ) espc[i+1] = espc[i]+data[i].length();
    Key k = Vec.newKey();
    ByteVec bv = new ByteVec(k,espc);
    DKV.put(k,bv,fs);
    for( int i = 0; i < data.length; ++i ) {
      Key ck = bv.chunkKey(i);
      DKV.put(ck, new Value(ck,new C1NChunk(data[i].getBytes())),fs);
    }
    fs.blockForPending();
    return k;
  }

  private static boolean compareDoubles(double a, double b, double threshold) {
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
      Key k1 = k(sb1.toString());
      Key r1 = Key.make("r1");
      ParseDataset2.parse(r1, k1);
      testParsed(r1,exp);

      StringBuilder sb2 = new StringBuilder();
      for( String ds : dataset ) sb2.append(ds).append("\r\n");
      Key k2 = k(sb2.toString());
      Key r2 = Key.make("r2");
      ParseDataset2.parse(r2, k2);
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
      Key k = k(dataset);
      Key r3 = Key.make();
      ParseDataset2.parse(r3,k);
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
      Key k = k(dataset);
      Key r4 = Key.make();
      ParseDataset2.parse(r4,k);
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
      Key key = k(dataset);
      Key r = Key.make();
      ParseDataset2.parse(r,key);
      Frame fr = DKV.get(r).get();
      String[] cd = fr.vecs()[2].domain();
      Assert.assertEquals(" four",cd[0]);
      Assert.assertEquals("one",cd[1]);
      Assert.assertEquals("three",cd[2]);
      Assert.assertEquals("two",cd[3]);
      testParsed(r, expDouble);
    }
  }

  @Test public void testNumberFormats(){
    String [] data = {"+.6e102|+.7e102|+.8e102\n.6e102|.7e102|.8e102\n"};
    double[][] expDouble = new double[][] {
      ard(+.6e102,.7e102,.8e102), // preserve order
      ard(+.6e102, +.7e102,+.8e102),
    };
    for (char separator : SEPARATORS) {
      String[] dataset = getDataForSeparator(separator, data);
      Key key = k(dataset);
      Key r = Key.make();
      ParseDataset2.parse(r,key);
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
      Key key = k(dataset);
      Key r = Key.make();
      ParseDataset2.parse(r,key);
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
    Key key = k(dataset);
    Key r = Key.make();
    ParseDataset2.parse(r,key);
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
      Key k = k(sb.toString());
      Key r5 = Key.make();
      ParseDataset2.parse(r5, k);
      testParsed(r5, exp);
    }
  }

  String[] getDataForSeparator(char sep, String[] data) {
    return getDataForSeparator('|', sep, data);
  }
  String[] getDataForSeparator(char placeholder, char sep, String[] data) {
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
      " 1 2:.2 5:.5 9:.9\n",
      "-1 7:.7 8:.8 9:.9\n",
      "+1 1:.1 5:.5 6:.6\n"
    };

    double[][] exp = new double[][] {
      ard( 1., .0, .2, .0, .0, .5, .0, .0, .0, .9),
      ard(-1., .0, .0, .0, .0, .0, .0, .7, .8, .9),
      ard( 1., .1, .0, .0, .0, .5, .6, .0, .0, .0),
    };
    StringBuilder sb = new StringBuilder();
    for( String ds : dataset ) sb.append(ds).append("\n");
    Key k = k(sb.toString());
    Key r1 = Key.make("r1");
    ParseDataset2.parse(r1, k);
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
}
