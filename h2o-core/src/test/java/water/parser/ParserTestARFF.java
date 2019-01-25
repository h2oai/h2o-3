package water.parser;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static water.parser.ParserTest.makeByteVec;

public class ParserTestARFF extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(1); }

  private Frame getParsedFrame(String[] dataset, String sep, int chunks_count) {
    StringBuilder sb = new StringBuilder();
    for (String ds : dataset) sb.append(ds).append(sep);

    String[] data_chunks = new String[chunks_count];
    int offset = 0;
    for (int i = 0; i < chunks_count; i++) {
      int chunk_length = sb.length() % chunks_count == 0
          ? sb.length() / chunks_count
          : Math.min((int)Math.ceil((double)sb.length() / chunks_count), sb.length() - offset);
      data_chunks[i] = sb.substring(offset, offset + chunk_length);
      offset += chunk_length;
    }
    assert offset == sb.length();

    Key k1 = makeByteVec(data_chunks);
    Key r1 = Key.make("r1");
    ParseDataset.parse(r1, k1);
    Frame fr = DKV.get(r1).get();
    Assert.assertNotNull(fr);
    return fr;
  }

  /**
   * Helper to check parsed column types
   */
  private void testTypes(String[] dataset, byte[] exp, int len, String sep, int chunks_count) {
    Frame fr = getParsedFrame(dataset, sep, chunks_count);
    try {
      Assert.assertEquals(len, fr.numRows());
      Assert.assertEquals(exp.length, fr.numCols());
      for (int j = 0; j < fr.numCols(); ++j) {
        Vec vec = fr.vecs()[j];
        if (exp[j] == Vec.T_TIME) { //Time
          Assert.assertTrue(vec.isTime());
//          Assert.assertFalse(vec.isInt()); //FIXME time is encoded as integer, but should isInt() be true?
          Assert.assertFalse(vec.isCategorical());
          Assert.assertFalse(vec.isString());
          Assert.assertFalse(vec.isUUID());
        } else if (exp[j] == Vec.T_CAT) { //Categorical
          Assert.assertTrue(vec.isCategorical());
//          Assert.assertFalse(vec.isInt()); //FIXME categorical is encoded as integer, but should isInt() be true?
          Assert.assertFalse(vec.isString());
          Assert.assertFalse(vec.isTime());
          Assert.assertFalse(vec.isUUID());
        } else if (exp[j] == Vec.T_STR) { //String
          Assert.assertTrue(vec.isString());
          Assert.assertFalse(vec.isInt());
          Assert.assertFalse(vec.isCategorical());
          Assert.assertFalse(vec.isTime());
          Assert.assertFalse(vec.isUUID());
        } else if (exp[j] == Vec.T_NUM) { //Numeric (can be Int or not)
          Assert.assertTrue(!vec.isCategorical() && !vec.isString() && !vec.isUUID() && !vec.isTime());
        } else if (exp[j] == Vec.T_UUID) { //UUID
          Assert.assertTrue(vec.isUUID());
//          Assert.assertFalse(vec.isInt()); //FIXME uuid is encoded as integer, but should isInt() be true?
          Assert.assertFalse(vec.isCategorical());
          Assert.assertFalse(vec.isString());
          Assert.assertFalse(vec.isTime());
        } else throw H2O.unimpl();
      }
    } finally {
      fr.delete();
    }
  }

  /**
   * Helper to check parsed column names
   */
  private void testColNames(String[] dataset, String[] exp, int len, String sep, int chunks_count) {
    Frame fr = getParsedFrame(dataset, sep, chunks_count);
    try {
      Assert.assertEquals(len, fr.numRows());
      Assert.assertEquals(exp.length, fr.numCols());
      for (int j = 0; j < fr.numCols(); ++j) {
        Assert.assertTrue(exp[j].equals(fr.names()[j]));
      }
    } finally {
      fr.delete();
    }
  }

  /** Find & parse a CSV file, allowing single quotes to keep strings together.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  private Frame parse_test_file_single_quotes( String fname ) {
    NFSFileVec nfs = TestUtil.makeNfsFileVec(fname);
    return ParseDataset.parse(Key.make(), new Key[]{nfs._key}, true, true /*single quote*/, ParseSetup.GUESS_HEADER);
  }

  // negative test to check the isBitIdentical
  @Test public void testTester() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file_single_quotes("smalldata/junit/arff/iris.arff");
      k1 = parse_test_file_single_quotes("smalldata/junit/cars.csv");
      Assert.assertFalse("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // clean ARFF file for iris
  @Test public void testSimple() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/iris.arff");
      k1 = parse_test_file("smalldata/junit/iris.csv");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!", Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // test some skipped_columns
  @Test public void testSimpleSkippedColumns() {
    Scope.enter();
    Frame k1 = null, k2 = null, k3=null, k4=null;
    try {
      int[] skipped_columns = new int[] {0,2};
      k2 = parse_test_file("smalldata/junit/arff/iris.arff");
      k3 = parse_test_file("smalldata/junit/arff/iris.arff", skipped_columns);
      k4 = parse_test_file("smalldata/junit/iris.csv", skipped_columns);
      k1 = parse_test_file("smalldata/junit/iris.csv");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k3, k4));
      Assert.assertTrue("parsed values do not match!", !(TestUtil.isBitIdentical(k1, k4)));
      Assert.assertTrue("column names do not match!", Arrays.equals(k2.names(), k1.names()));
      Scope.track(k1, k2, k3, k4);
    } finally {
      Scope.exit();
    }
  }

  // test all skipped_columns
  @Test public void testSimpleSkippedColumnsAll() {
    Scope.enter();
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/iris.arff");
      Scope.track(k2);
      int[] skipped_columns = new int[k2.numCols()];
      for (int cindex=0; cindex<k2.numCols(); cindex++)
        skipped_columns[cindex]=cindex;
      k1 = parse_test_file("smalldata/junit/iris.csv", skipped_columns);
      Assert.assertTrue("Exception should have been thrown!", 1 == 2);
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
    } catch(Exception ex) {
      System.out.println(ex);
    } finally {
      Scope.exit();
    }
  }

  @Test public void testFactorCol() {
    Frame k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/myfactorcol.arff");
    } finally {
      if( k2 != null ) k2.delete();
    }
  }

  // check lower/uppercase markers
  @Test public void testUpperLowerCase() {
    String[] data = new String[] {
            "@RELaTIoN type",
            "",
            "@atTrIbute numeric  numEric",
            "",
            "@datA",
            "0",
            "1",
            "2",
    };
    byte[] exp_types = new byte[]{
            Vec.T_NUM
    };
    String[] exp_names = new String[]{
            "numeric"
    };
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);
    testColNames(dataset, exp_names, len, "\n", 1);
    testColNames(dataset, exp_names, len, "\r\n", 1);
  }

  // numbers are numbers
  @Test public void testType() {
    String[] data = new String[] {
            "@RELATION type",
            "",
            "@ATTRIBUTE numeric  NUMERIC",
            "",
            "@DATA",
            "0",
            "1",
            "2",
    };
    byte[] exp_types = new byte[]{
            Vec.T_NUM
    };
    String[] exp_names = new String[]{
            "numeric"
    };
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);
    testColNames(dataset, exp_names, len, "\n", 1);
    testColNames(dataset, exp_names, len, "\r\n", 1);
  }

  // force numbers to be categoricals //PUBDEV-17
  @Test public void testType1() {
    String[] data = new String[] {
            "@RELATION myfactorcol",
            "",
            "@ATTRIBUTE class  {0,4.10,levelA,levelB}", //dictionary
            "",
            "@DATA",
            "4",
            "10",
            "0",
            "levelA",
            "levelB",
            "levelA",
            "10",
    };
    byte[] exp_types = new byte[]{
            Vec.T_CAT
    };
    String[] exp_names = new String[]{
            "class"
    };
    final int len = 7;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);
    testColNames(dataset, exp_names, len, "\n", 1);
    testColNames(dataset, exp_names, len, "\r\n", 1);
  }

  // force numbers to be categoricals
  @Test public void testType2() {
    String[] data = new String[] {
            "@RELATION type",
            "",
            "@ATTRIBUTE enum  {0,1.324e-13,-2}", //with dictionary
            "",
            "@DATA",
            "0",
            "1.324e-13",
            "-2",
    };
    byte[] exp_types = new byte[]{
            Vec.T_CAT
    };
    String[] exp_names = new String[]{
            "enum"
    };
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);
    testColNames(dataset, exp_names, len, "\n", 1);
    testColNames(dataset, exp_names, len, "\r\n", 1);
  }

  // force numbers to be categoricals
  @Test public void testType3() {
    String[] data = new String[] {
            "@RELATION type",
            "",
            "@ATTRIBUTE enum ENUM", // no dictionary
            "",
            "@DATA",
            "0",
            "1.324e-13",
            "-2",
    };
    byte[] exp_types = new byte[]{
            Vec.T_CAT
    };
    String[] exp_names = new String[]{
            "enum"
    };
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);
    testColNames(dataset, exp_names, len, "\n", 1);
    testColNames(dataset, exp_names, len, "\r\n", 1);
  }

  // force numbers to be strings
  @Test
  public void testType4() {
    String[] data = new String[] {
            "@RELATION type",
            "",
            "@ATTRIBUTE col STRING",
            "",
            "@DATA",
            "0",
            "1",
            "2",
    };
    byte[] exp_types = new byte[]{
            Vec.T_STR
    };
    String[] exp_names = new String[]{
            "col"
    };
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);

    testColNames(dataset, exp_names, len, "\n", 1);
    testColNames(dataset, exp_names, len, "\r\n", 1);
  }

  // mixed ARFF file with numbers as numbers
  @Test public void testMixed() {
    String[] data = new String[] {
            "@RELATION mixed",
            "",
            "@ATTRIBUTE date     DATE",
            "@ATTRIBUTE string   {dog,cat,mouse}",
            "@ATTRIBUTE numeric  NUMERIC",
            "@ATTRIBUTE response {Y,N}",
            "",
            "@DATA",
            "2014-08-23,dog,3,Y",
            "2014-08-24,dog,4,Y",
            "2014-08-28,cat,5,Y",
            "2014-08-30,mouse,6,Y",
            "2013-07-20,cat,7,N"
    };
    byte[] exp_types = new byte[]{
            Vec.T_TIME,
            Vec.T_CAT,
            Vec.T_NUM,
            Vec.T_CAT
    };
    String[] exp_names = new String[]{
            "date",
            "string",
            "numeric",
            "response",
    };
    final int len = 5;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);

    testColNames(dataset, exp_names, len, "\n", 1);
    testColNames(dataset, exp_names, len, "\r\n", 1);
  }

  // mixed ARFF file with numbers as categoricals
  @Test public void testMixed2() {
    String[] data = new String[]{
            "@RELATION mixed",
            "",
            "@ATTRIBUTE date,yeah'!    DATE",
            "@ATTRIBUTE 0   {dog,cat,mouse}",
            "@ATTRIBUTE numeric!#$%!                       {3,4,5,6,7}", //force numbers to be categoricals!
            "@ATTRIBUTE response {Y,N}",
            "",
            "@DATA",
            "2014-08-23,dog,3,Y",
            "2014-08-24,dog,4,Y",
            "2014-08-28,cat,5,Y",
            "2014-08-30,mouse,6,Y",
            "2013-07-20,cat,7,N"
    };
    byte[] exp_types = new byte[]{
            Vec.T_TIME,
            Vec.T_CAT,
            Vec.T_CAT,
            Vec.T_CAT
    };
    String[] exp_names = new String[]{
            "date,yeah'!",
            "0",
            "numeric!#$%!",
            "response",
    };
    final int len = 5;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);

    testColNames(dataset, exp_names, len, "\n", 1);
    testColNames(dataset, exp_names, len, "\r\n", 1);
  }

  // UUID
  @Test public void testUUID() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/test_uuid.arff");
      k1 = parse_test_file("smalldata/junit/test_uuid.csv");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }


  // ARFF file with uuids
  @Test public void testUUID2() {
    String[] data = new String[]{
            "@relation uuid",
            "@attribute uuid uuid",
            "@data",
            "0df17cd5-6a5d-f4a9-4074-e133c9d4739fae3",
            "19281622-47ff-af63-185c-d8b2a244c78e7c6",
            "7f79c2b5-da56-721f-22f9-fdd726b13daf8e8",
            "7f79c2b5-da56-721f-22f9-fdd726b13daf8e8",
    };
    byte[] exp_types = new byte[]{
            Vec.T_UUID
    };
    final int len = 4;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n", 1);
    testTypes(dataset, exp_types, len, "\r\n", 1);
  }



  // space as separator
  @Test public void testSpaceSep() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/iris_spacesep.arff");
      k1 = parse_test_file("smalldata/junit/iris.csv");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // two spaces as separator
  @Test public void testWeirdSep() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/iris_weirdsep.arff");
      k1 = parse_test_file("smalldata/junit/iris.csv");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // mixed space and tab as separators
  @Test public void testWeirdSep2() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/iris_weirdsep2.arff");
      k1 = parse_test_file("smalldata/junit/iris.csv");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }


  // Mixed UUID, numeric, categorical and time (no Strings)
  @Test public void testMix() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/time.arff");
      k1 = parse_test_file("smalldata/junit/time.csv");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // split files into header + csv data
  @Test
  public void testFolder1() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/arff/folder1/");
      k1 = parse_test_file  ("smalldata/junit/arff/iris.arff");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // split files into header/data + more data
  @Test
  public void testFolder2() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/arff/folder2/" );
      k1 = parse_test_file("smalldata/junit/arff/iris.arff");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1,k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // split files into header/data + more data
  @Test
  public void testFolder3() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/arff/folder3/" );
      k1 = parse_test_file("smalldata/junit/arff/iris.arff");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1,k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // split files into header/data + many many more files
  @Test
  public void testFolder4() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/arff/folder4/" );
      k1 = parse_test_file("smalldata/junit/arff/iris.arff");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1,k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  @Test public void testInt(){
    String data =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num INT\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";
    double[][] exp = new double[][] {
            ard(0),
            ard(1.324e-13),
            ard(-2)
    };
    Key k = ParserTest.makeByteVec(data);
    ParserTest.testParsed(ParseDataset.parse(Key.make(), k),exp,3);
  }

  @Test public void testReal(){
    String data =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num ReAl\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";
    double[][] exp = new double[][] {
            ard(0),
            ard(1.324e-13),
            ard(-2)
    };
    Key k = ParserTest.makeByteVec(data);
    ParserTest.testParsed(ParseDataset.parse(Key.make(), k),exp,3);
  }

  @Test public void testNum(){
    String data =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num numeric\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";
    double[][] exp = new double[][] {
            ard(0),
            ard(1.324e-13),
            ard(-2)
    };
    Key k = ParserTest.makeByteVec(data);
    ParserTest.testParsed(ParseDataset.parse(Key.make(), k),exp,3);
  }

  @Test public void testNumSplit(){
    String data1 =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num numeric\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";
    String data2 =
                    "4\n" +
                    "5\n" +
                    "6\n";
    double[][] exp = new double[][] {
            ard(0),
            ard(1.324e-13),
            ard(-2),
            ard(4),
            ard(5),
            ard(6),
    };
    Key k1 = ParserTest.makeByteVec(data1);
    Key k2 = ParserTest.makeByteVec(data2);
    Key[] k = new Key[]{k1, k2};
    ParserTest.testParsed(ParseDataset.parse(Key.make(), k),exp,6);
  }

  @Test public void testEnumSplit(){
    String data1 =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num ENUM\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";
    String data2 =
            "4\n" +
                    "5\n" +
                    "6\n";
    Key k1 = ParserTest.makeByteVec(data1);
    Key k2 = ParserTest.makeByteVec(data2);
    Key[] k = new Key[]{k1, k2};
    Frame fr = ParseDataset.parse(Key.make(), k);
    Assert.assertTrue(fr.anyVec().isCategorical());
    Assert.assertFalse(fr.anyVec().isString());
    Assert.assertTrue(fr.anyVec().cardinality() == 6);
    fr.delete();
  }

  @Test public void testStringSplit(){
    String data1 =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num STRING\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";
    String data2 =
            "4\n" +
                    "5234234234\n" +
                    "6\n";
    Key k1 = ParserTest.makeByteVec(data1);
    Key k2 = ParserTest.makeByteVec(data2);
    Key[] k = new Key[]{k1, k2};
    Frame fr = ParseDataset.parse(Key.make(), k);
    Assert.assertTrue(fr.anyVec().isString());
    Assert.assertFalse(fr.anyVec().isCategorical());
    Assert.assertFalse(fr.anyVec().isInt());
    BufferedString tmpStr = new BufferedString();
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 3).toString().equals("4"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 4).toString().equals("5234234234"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 5).toString().equals("6"));
    fr.delete();
  }


  @Test @Ignore public void testUUIDSplit(){
    String data1 =
            "@RELATION uuid\n" +
                    "\n" +
                    "@ATTRIBUTE col UUID\n" +
                    "\n" +
                    "@DATA\n" +
                    "19281622-47ff-af63-185c-d8b2a244c78e7c6\n";
    String data2 =
            "19281622-47ff-af63-185c-d8b2a244c78e7c6\n" +
                    "7f79c2b5-da56-721f-22f9-fdd726b13daf8e8\n" +
                    "7f79c2b5-da56-721f-22f9-fdd726b13daf8e8\n";
    Key k1 = ParserTest.makeByteVec(data1);
    Key k2 = ParserTest.makeByteVec(data2);
    Key[] k = new Key[]{k1, k2};
    Frame fr = ParseDataset.parse(Key.make(), k);
    Assert.assertTrue(fr.anyVec().isUUID());
    Assert.assertFalse(fr.anyVec().isCategorical());
    Assert.assertFalse(fr.anyVec().isString());
    Assert.assertTrue(!fr.anyVec().isNA(0));
    Assert.assertTrue(!fr.anyVec().isNA(1));
    fr.delete();
  }

  @Test public void testMultipleFilesNum(){
    String data1 =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num numeric\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";

    Key k1 = ParserTest.makeByteVec(data1);
    Key k2 = ParserTest.makeByteVec(data1);
    Key[] k = new Key[]{k1, k2};
    Frame fr = ParseDataset.parse(Key.make(), k);
    Assert.assertFalse(fr.anyVec().isString());
    Assert.assertFalse(fr.anyVec().isCategorical());
    Assert.assertFalse(fr.anyVec().isInt());
    Assert.assertFalse(fr.anyVec().isUUID());

    Assert.assertTrue(fr.anyVec().at(0) == 0);
    Assert.assertTrue(fr.anyVec().at(1) == 1.324e-13);
    Assert.assertTrue(fr.anyVec().at(2) == -2);
    Assert.assertTrue(fr.anyVec().at(3) == 0);
    Assert.assertTrue(fr.anyVec().at(4) == 1.324e-13);
    Assert.assertTrue(fr.anyVec().at(5) == -2);
    fr.delete();
  }

  @Test public void testMultipleFilesEnum(){
    String data1 =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num enum\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";

    Key k1 = ParserTest.makeByteVec(data1);
    Key k2 = ParserTest.makeByteVec(data1);
    Key[] k = new Key[]{k1, k2};
    Frame fr = ParseDataset.parse(Key.make(), k);
    Assert.assertFalse(fr.anyVec().isString());
    Assert.assertTrue(fr.anyVec().isCategorical());
    Assert.assertFalse(fr.anyVec().isUUID());

    Assert.assertTrue(fr.anyVec().at(0) == 1);
    Assert.assertTrue(fr.anyVec().at(1) == 2);
    Assert.assertTrue(fr.anyVec().at(2) == 0);
    Assert.assertTrue(fr.anyVec().at(3) == 1);
    Assert.assertTrue(fr.anyVec().at(4) == 2);
    Assert.assertTrue(fr.anyVec().at(5) == 0);
    fr.delete();
  }

  @Test public void testMultipleFilesString(){
    String data1 =
            "@RELATION type\n" +
                    "\n" +
                    "@ATTRIBUTE num STRING\n" +
                    "\n" +
                    "@DATA\n" +
                    "0\n" +
                    "1.324e-13\n" +
                    "-2\n";

    Key k1 = ParserTest.makeByteVec(data1);
    Key k2 = ParserTest.makeByteVec(data1);
    Key k3 = ParserTest.makeByteVec(data1);
    Key[] k = new Key[]{k1, k2, k3};
    Frame fr = ParseDataset.parse(Key.make(), k);
    Assert.assertTrue(fr.anyVec().isString());
    Assert.assertFalse(fr.anyVec().isCategorical());
    Assert.assertFalse(fr.anyVec().isInt());
    BufferedString tmpStr = new BufferedString();
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 0).toString().equals("0"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 1).toString().equals("1.324e-13"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 2).toString().equals("-2"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 3).toString().equals("0"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 4).toString().equals("1.324e-13"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 5).toString().equals("-2"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 6).toString().equals("0"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 7).toString().equals("1.324e-13"));
    Assert.assertTrue(fr.anyVec().atStr(tmpStr, 8).toString().equals("-2"));
    fr.delete();
  }

  @Test public void testStandardNAs() {
    String[] arff = new String[] {
        "@relation test_nas",
        "@attribute y {'0', '1'}",
        "@attribute x1 numeric",
        "@attribute x2 string",
        "@attribute x3 {y, n}",
        "@attribute x4 {0, 1}",
        "@attribute x5 {'1', '10', '100'}",
        "@data",
        "'0',42,\"foo\",y,0,'1'",
        "'1',?,\"bar\",n,1,'10'",
        "?,42,\"baz\",?,0,'100'",
        "'0',24,?,y,1,?",
        "'1',42,\"foo\",n,?,'1'"
    };

    byte[] expected_types = new byte[]{
        Vec.T_CAT,
        Vec.T_NUM,
        Vec.T_STR,
        Vec.T_CAT,
        Vec.T_CAT,
        Vec.T_CAT,
    };
    String[] expected_names = new String[]{"y", "x1", "x2", "x3", "x4", "x5"};
    int expected_data_length = 5;

    testTypes(arff, expected_types, expected_data_length, "\n", 1);
    testColNames(arff, expected_names, expected_data_length, "\n", 1);

    Frame parsed = getParsedFrame(arff, "\n", 1);
    try {
      Assert.assertTrue(parsed.hasNAs());
      Assert.assertEquals(6, parsed.naCount());

      Assert.assertFalse(parsed.vec("y").isNA(0));
      Assert.assertTrue(parsed.vec("y").isNA(2));

      Assert.assertFalse(parsed.vec("x1").isNA(0));
      Assert.assertTrue(parsed.vec("x1").isNA(1));

      Assert.assertFalse(parsed.vec("x2").isNA(0));
      Assert.assertTrue(parsed.vec("x2").isNA(3));

      Assert.assertFalse(parsed.vec("x3").isNA(0));
      Assert.assertTrue(parsed.vec("x3").isNA(2));

      Assert.assertFalse(parsed.vec("x4").isNA(0));
      Assert.assertTrue(parsed.vec("x4").isNA(4));

      Assert.assertFalse(parsed.vec("x5").isNA(0));
      Assert.assertTrue(parsed.vec("x5").isNA(3));

    } finally {
      parsed.delete();
    }
  }

  @Test public void testIgnoreBlankLines() {
    String[] arff = new String[] {
        "% comment",
        " ",
        "\t",
        "  ",
        " \t",
        "\t ",
        "\t\t",
        "@relation test_nas",
        " ",
        "@attribute y {'0', '1'}",
        "@attribute x1 numeric",
        "@attribute x2 string",
        " ",
        "@data",
        "  ",
        "\t ",
        "'0',42,\"foo\"",
        "'1',24,\"bar\"",
        "  ",
    };
    byte[] expected_types = new byte[]{Vec.T_CAT, Vec.T_NUM, Vec.T_STR};
    String[] expected_names = new String[]{"y", "x1", "x2"};
    int expected_data_length = 2;

    testTypes(arff, expected_types, expected_data_length, "\n", 1);
    testColNames(arff, expected_names, expected_data_length, "\n", 1);
  }


  @Test public void testMultiChunkHeader() {
    final int col_count = 51;
    final int cat_length = 10_000;
    final int data_count = 10_000;
    final int header_comment_length = 1_000;

    byte[] expected_types = new byte[col_count];
    String[] expected_names = new String[col_count];

    List<String> arff = new ArrayList<>();
    for (int i = 0; i < header_comment_length; i++) {
      arff.add("%%%% comment comment comment");
      if (i % 50 == 0) arff.add("");
    }
    arff.add("@relation test_large_header");
    arff.add("@attribute y numeric");
    expected_types[0] = Vec.T_NUM;
    expected_names[0] = "y";

    //add 50 categoricals of about 100kB each;
    for (int i = 1; i < col_count; i++) {
      final StringBuilder cat = new StringBuilder("@attribute x_").append(i).append(' ').append('{');
      for (int c = 0; c < cat_length; c++) {
        if (c != 0) cat.append(',');
        cat.append("factor_x").append(i).append('_').append(c);
      }
      cat.append('}');
      arff.add(cat.toString());
      expected_types[i] = Vec.T_CAT;
      expected_names[i] = "x_"+i;
    }

    //add a few data rows
    arff.add("@data");
    Random rnd = new Random();
    for (int i = 0; i < data_count; i++) {
      final StringBuilder row = new StringBuilder();
      row.append(rnd.nextInt(100));
      for (int c = 1; c < col_count; c++) {
        row.append(',')
            .append("factor_x").append(c).append('_').append(rnd.nextInt(cat_length));
      }
      arff.add(row.toString());
    }

    String[] arffa = arff.toArray(new String[]{});
    testTypes(arffa, expected_types, data_count, "\n", 10);
    testColNames(arffa, expected_names, data_count, "\n", 10);

    Frame parsed = getParsedFrame(arffa, "\n", 10);
    try {
      Assert.assertFalse(parsed.hasNAs());
    } finally {
      parsed.delete();
    }
  }
}
