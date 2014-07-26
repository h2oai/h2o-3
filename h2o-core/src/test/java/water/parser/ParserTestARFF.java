package water.parser;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import static water.parser.ParserTest.makeByteVec;

import java.util.Arrays;

public class ParserTestARFF extends TestUtil {

  /**
   * Helper to check parsed column types
   */
  private void testTypes(String[] dataset, byte[] exp, int len, String sep) {
    StringBuilder sb1 = new StringBuilder();
    for (String ds : dataset) sb1.append(ds).append(sep);
    Key k1 = makeByteVec(sb1.toString());
    Key r1 = Key.make("r1");
    ParseDataset2.parse(r1, k1);
    Frame fr = DKV.get(r1).get();
    try {
      Assert.assertEquals(len, fr.numRows());
      Assert.assertEquals(exp.length, fr.numCols());
      for (int j = 0; j < fr.numCols(); ++j) {
        Vec vec = fr.vecs()[j];
        if (exp[j] == ParseDataset2.FVecDataOut.TCOL) {
          Assert.assertTrue(vec.isTime());
        } else if (exp[j] == ParseDataset2.FVecDataOut.ECOL) {
          Assert.assertTrue(vec.isEnum());
        } else if (exp[j] == ParseDataset2.FVecDataOut.SCOL) {
          Assert.assertTrue(vec.isString());
        } else if (exp[j] == ParseDataset2.FVecDataOut.NCOL) {
          Assert.assertTrue(!vec.isEnum() && !vec.isString() && !vec.isUUID() && !vec.isTime());
        } else if (exp[j] == ParseDataset2.FVecDataOut.ICOL) {
          Assert.assertTrue(vec.isUUID());
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
    ParseDataset2.parse(r1, k1);
    Frame fr = DKV.get(r1).get();
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

  // negative test to check the isBitIdentical
  @Test public void testTester() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file_single_quotes("smalldata/junit/arff/iris.arff");
      k1 = parse_test_file_single_quotes("smalldata/junit/string.csv");
      Assert.assertFalse("parsed values do not match!", isBitIdentical(k1, k2));
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
      Assert.assertTrue("parsed values do not match!", isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!", Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // clean ARFF file for strings
  @Test
  @Ignore //not yet implemented
  public void testSimpleString() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file_single_quotes("smalldata/junit/arff/string.arff");
      k1 = parse_test_file_single_quotes("smalldata/junit/arff/string.csv");
      Assert.assertTrue("parsed values do not match!", isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
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
            ParseDataset2.FVecDataOut.NCOL
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
            ParseDataset2.FVecDataOut.NCOL
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

  // force numbers to be enums
  @Test public void testType2() {
    String[] data = new String[] {
            "@RELATION type",
            "",
            "@ATTRIBUTE enum  {0,1.324e-13,-2}",
            "",
            "@DATA",
            "0",
            "1.324e-13",
            "-2",
    };
    byte[] exp_types = new byte[]{
            ParseDataset2.FVecDataOut.ECOL
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
  @Ignore  //string creation in NewChunk is not yet implemented
  public void testType3() {
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
            ParseDataset2.FVecDataOut.SCOL
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
            ParseDataset2.FVecDataOut.TCOL,
            ParseDataset2.FVecDataOut.ECOL,
            ParseDataset2.FVecDataOut.NCOL,
            ParseDataset2.FVecDataOut.ECOL
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

  // mixed ARFF file with numbers as enums
  @Test public void testMixed2() {
    String[] data = new String[]{
            "@RELATION mixed",
            "",
            "@ATTRIBUTE date,yeah'!    DATE",
            "@ATTRIBUTE 0   {dog,cat,mouse}",
            "@ATTRIBUTE numeric!#$%!                       {3,4,5,6,7}", //force numbers to be enums!
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
            ParseDataset2.FVecDataOut.TCOL,
            ParseDataset2.FVecDataOut.ECOL,
            ParseDataset2.FVecDataOut.ECOL,
            ParseDataset2.FVecDataOut.ECOL
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
      Assert.assertTrue("parsed values do not match!", isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }


  // mixed ARFF file with numbers as enums
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
            ParseDataset2.FVecDataOut.ICOL
    };
    String[] exp_names = new String[]{
            "uuid"
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
      Assert.assertTrue("parsed values do not match!", isBitIdentical(k1, k2));
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
      Assert.assertTrue("parsed values do not match!", isBitIdentical(k1, k2));
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
      Assert.assertTrue("parsed values do not match!", isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }


  // Mixed UUID, numeric, enum and time (no Strings)
  @Test public void testMix() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/time.arff");
      k1 = parse_test_file("smalldata/junit/time.csv");
      Assert.assertTrue("parsed values do not match!", isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // split files into header + csv data
  @Test
  @Ignore
  public void testFolder1() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/arff/folder1/");
      k1 = parse_test_file  ("smalldata/junit/arff/iris.arff");
      Assert.assertTrue("parsed values do not match!",isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // split files into header/data + more data
  @Test
  @Ignore
  public void testFolder2() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/arff/folder2/" );
      k1 = parse_test_file("smalldata/junit/arff/iris.arff");
      Assert.assertTrue("parsed values do not match!",isBitIdentical(k1,k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }
}
