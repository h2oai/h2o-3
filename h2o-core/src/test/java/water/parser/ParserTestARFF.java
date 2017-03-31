package water.parser;

import com.google.common.io.LineReader;
import org.junit.*;
import static org.junit.Assert.*;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.util.BytesStats;
import water.util.FileUtils;

import static water.parser.ParserTest.makeByteVec;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParserTestARFF extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(2); }

  @Before public void before() { Scope.enter(); }

  @After public void after()   { Scope.exit(); }
  
  /**
   * Helper to check parsed column types
   */
  private void testTypes(String[] dataset, byte[] exp, int len, String sep) {
    StringBuilder sb1 = new StringBuilder();
    for (String ds : dataset) sb1.append(ds).append(sep);
    Key k1 = makeByteVec(sb1.toString());
    Key r1 = Key.make("r1");
    ParseDataset.parse(r1, k1);
    Frame fr = DKV.get(r1).get();
    try {
      assertEquals(len, fr.numRows());
      assertEquals(exp.length, fr.numCols());
      for (int j = 0; j < fr.numCols(); ++j) {
        Vec vec = fr.vecs()[j];
        if (exp[j] == Vec.T_TIME) { //Time
          assertTrue(vec.isTime());
//          assertFalse(vec.isInt()); //FIXME time is encoded as integer, but should isInt() be true?
          assertFalse(vec.isCategorical());
          assertFalse(vec.isString());
          assertFalse(vec.isUUID());
        } else if (exp[j] == Vec.T_CAT) { //Categorical
          assertTrue(vec.isCategorical());
//          assertFalse(vec.isInt()); //FIXME categorical is encoded as integer, but should isInt() be true?
          assertFalse(vec.isString());
          assertFalse(vec.isTime());
          assertFalse(vec.isUUID());
        } else if (exp[j] == Vec.T_STR) { //String
          assertTrue(vec.isString());
          assertFalse(vec.isInt());
          assertFalse(vec.isCategorical());
          assertFalse(vec.isTime());
          assertFalse(vec.isUUID());
        } else if (exp[j] == Vec.T_NUM) { //Numeric (can be Int or not)
          assertTrue(!vec.isCategorical() && !vec.isString() && !vec.isUUID() && !vec.isTime());
        } else if (exp[j] == Vec.T_UUID) { //UUID
          assertTrue(vec.isUUID());
//          assertFalse(vec.isInt()); //FIXME uuid is encoded as integer, but should isInt() be true?
          assertFalse(vec.isCategorical());
          assertFalse(vec.isString());
          assertFalse(vec.isTime());
        } else throw H2O.unimpl();
      }
    } finally {
      fr.delete();
    }
  }

  /**
   * Helper to check parsed column names
   */
  private void testColNames(String[] dataset, String[] exp, int len, String sep) {
    StringBuilder sb1 = new StringBuilder();
    for (String ds : dataset) sb1.append(ds).append(sep);
    Key k1 = makeByteVec(sb1.toString());
    Key r1 = Key.make("r1");
    ParseDataset.parse(r1, k1);
    Frame fr = DKV.get(r1).get();
    try {
      assertEquals(len, fr.numRows());
      assertEquals(exp.length, fr.numCols());
      for (int j = 0; j < fr.numCols(); ++j) {
        assertEquals(exp[j], fr.name(j));
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
      assertFalse("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
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
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      assertTrue("column names do not match!", Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
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
    byte[] exp_types = new byte[]{Vec.T_NUM};
    String[] exp_names = new String[]{"numeric"};
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");
    testColNames(dataset, exp_names, len, "\n");
    testColNames(dataset, exp_names, len, "\r\n");
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

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");
    testColNames(dataset, exp_names, len, "\n");
    testColNames(dataset, exp_names, len, "\r\n");
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

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");
    testColNames(dataset, exp_names, len, "\n");
    testColNames(dataset, exp_names, len, "\r\n");
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

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");
    testColNames(dataset, exp_names, len, "\n");
    testColNames(dataset, exp_names, len, "\r\n");
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

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");
    testColNames(dataset, exp_names, len, "\n");
    testColNames(dataset, exp_names, len, "\r\n");
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

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");

    testColNames(dataset, exp_names, len, "\n");
    testColNames(dataset, exp_names, len, "\r\n");
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

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");

    testColNames(dataset, exp_names, len, "\n");
    testColNames(dataset, exp_names, len, "\r\n");
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

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");

    testColNames(dataset, exp_names, len, "\n");
    testColNames(dataset, exp_names, len, "\r\n");
  }

  // UUID
  @Test public void testUUID() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/test_uuid.arff");
      k1 = parse_test_file("smalldata/junit/test_uuid.csv");
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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

    testTypes(dataset, exp_types, len, "\n");
    testTypes(dataset, exp_types, len, "\r\n");
  }



  // space as separator
  @Test public void testSpaceSep() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/iris_spacesep.arff");
      k1 = parse_test_file("smalldata/junit/iris.csv");
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1, k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1,k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1,k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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
      assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1,k2));
      assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
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
    assertTrue(fr.anyVec().isCategorical());
    assertFalse(fr.anyVec().isString());
    assertTrue(fr.anyVec().cardinality() == 6);
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
    assertTrue(fr.anyVec().isString());
    assertFalse(fr.anyVec().isCategorical());
    assertFalse(fr.anyVec().isInt());
    BufferedString tmpStr = new BufferedString();
    assertTrue(fr.anyVec().atStr(tmpStr, 3).toString().equals("4"));
    assertTrue(fr.anyVec().atStr(tmpStr, 4).toString().equals("5234234234"));
    assertTrue(fr.anyVec().atStr(tmpStr, 5).toString().equals("6"));
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
    assertTrue(fr.anyVec().isUUID());
    assertFalse(fr.anyVec().isCategorical());
    assertFalse(fr.anyVec().isString());
    assertTrue(!fr.anyVec().isNA(0));
    assertTrue(!fr.anyVec().isNA(1));
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
    assertFalse(fr.anyVec().isString());
    assertFalse(fr.anyVec().isCategorical());
    assertFalse(fr.anyVec().isInt());
    assertFalse(fr.anyVec().isUUID());

    assertTrue(fr.anyVec().at(0) == 0);
    assertTrue(fr.anyVec().at(1) == 1.324e-13);
    assertTrue(fr.anyVec().at(2) == -2);
    assertTrue(fr.anyVec().at(3) == 0);
    assertTrue(fr.anyVec().at(4) == 1.324e-13);
    assertTrue(fr.anyVec().at(5) == -2);
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
    assertFalse(fr.anyVec().isString());
    assertTrue(fr.anyVec().isCategorical());
    assertFalse(fr.anyVec().isUUID());

    assertTrue(fr.anyVec().at(0) == 1);
    assertTrue(fr.anyVec().at(1) == 2);
    assertTrue(fr.anyVec().at(2) == 0);
    assertTrue(fr.anyVec().at(3) == 1);
    assertTrue(fr.anyVec().at(4) == 2);
    assertTrue(fr.anyVec().at(5) == 0);
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
    assertTrue(fr.anyVec().isString());
    assertFalse(fr.anyVec().isCategorical());
    assertFalse(fr.anyVec().isInt());
    BufferedString tmpStr = new BufferedString();
    assertTrue(fr.anyVec().atStr(tmpStr, 0).toString().equals("0"));
    assertTrue(fr.anyVec().atStr(tmpStr, 1).toString().equals("1.324e-13"));
    assertTrue(fr.anyVec().atStr(tmpStr, 2).toString().equals("-2"));
    assertTrue(fr.anyVec().atStr(tmpStr, 3).toString().equals("0"));
    assertTrue(fr.anyVec().atStr(tmpStr, 4).toString().equals("1.324e-13"));
    assertTrue(fr.anyVec().atStr(tmpStr, 5).toString().equals("-2"));
    assertTrue(fr.anyVec().atStr(tmpStr, 6).toString().equals("0"));
    assertTrue(fr.anyVec().atStr(tmpStr, 7).toString().equals("1.324e-13"));
    assertTrue(fr.anyVec().atStr(tmpStr, 8).toString().equals("-2"));
    fr.delete();
  }

  /**
   * H2O shows the file has 11219 rows. but original file (from openml) has 10885 rows
   which is verified by parsing with R.
   */
  @Test public void testPUBDEV3281() throws IOException {
    final String fname = "smalldata/junit/arff/jm1_arff.txt";
    int expectedLength = 10885;
    NFSFileVec nfs = null;
    try {
      nfs = makeNfsFileVec(fname);
    } catch (Exception ignore) {
      // ok, no test file
    }
    
    if (nfs != null) {
      LineReader r = new LineReader(new FileReader(FileUtils.getFile(fname)));
      List<Double> col1 = new ArrayList<>();
      int l0 = -1;
      int l = 0;
      String s;
      while ((s = r.readLine()) != null) {
        if (!s.isEmpty() && Character.isDigit(s.charAt(0))) {
          if (l0 == -1) l0 = l;
          String n1 = s.substring(0, s.indexOf(","));
          col1.add(Double.parseDouble(n1));
        }
        l++;
      }
      
      final Key<Keyed> okey = Key.make();
      Key[] keys = new Key[]{nfs._key};
      boolean deleteOnDone = true;
      boolean singleQuote = false;
      final ParseSetup globalSetup = ParseSetup.guessSetup(keys, singleQuote, ParseSetup.GUESS_HEADER);
      assertEquals(new BytesStats(11241, 118, 850348), globalSetup.bytesStats);
      assertEquals(expectedLength, globalSetup.tentativeNumLines);
      assertEquals(16021, globalSetup.dataOffset);
      Frame k = ParseDataset.parse(okey, keys, deleteOnDone, globalSetup);
      Scope.track(k);
      final Vec vec = k.anyVec();
      assertNotNull(vec);

      for(int i = 0; i < expectedLength; i ++) {
        assertEquals("At " + i + "(line " + (l0+i) + "): ", col1.get(i), vec.at(i), 0.0001);
      }

      assertEquals(expectedLength, vec.length());
    }
  }
}
