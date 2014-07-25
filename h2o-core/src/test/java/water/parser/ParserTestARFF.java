package water.parser;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import static water.parser.ParserTest.makeByteVec;

import java.util.Arrays;

public class ParserTestARFF extends TestUtil {

  private static void testParsedTypes(Key k, byte[] exp_types, int len) {
    Frame fr = DKV.get(k).get();
    testParsedTypes(fr, exp_types, len);
  }
  private static void testParsedTypes(Frame fr, byte[] exp_types, int len) {
    try {
      Assert.assertEquals(len, fr.numRows());
      Assert.assertEquals(exp_types.length, fr.numCols());
      for (int j = 0; j < fr.numCols(); ++j) {
        Vec vec = fr.vecs()[j];
        if (exp_types[j] == ParseDataset2.FVecDataOut.TCOL) {
          Assert.assertTrue(vec.isTime());
        } else if (exp_types[j] == ParseDataset2.FVecDataOut.ECOL) {
          Assert.assertTrue(vec.isEnum());
        } else if (exp_types[j] == ParseDataset2.FVecDataOut.SCOL) {
          Assert.assertTrue(vec.isString());
        } else if (exp_types[j] == ParseDataset2.FVecDataOut.NCOL) {
          Assert.assertTrue(!vec.isEnum() && !vec.isString() && !vec.isUUID() && !vec.isTime());
        }
      }
    } finally {
      fr.delete();
    }
  }

  private void testTypes(String[] dataset, byte[] exp, int len, String sep) {
    StringBuilder sb1 = new StringBuilder();
    for (String ds : dataset) sb1.append(ds).append(sep);
    Key k1 = makeByteVec(sb1.toString());
    Key r1 = Key.make("r1");
    ParseDataset2.parse(r1, k1);
    testParsedTypes(r1, exp, len);
  }

  // clean ARFF file for iris
  @Test public void testSimple() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_file("smalldata/junit/arff/iris.arff");
      k1 = parse_test_file("smalldata/junit/iris.csv");
      Assert.assertTrue("parsed values do not match!", isBitIdentical(k1, k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
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
    byte[] exp = new byte[]{
            ParseDataset2.FVecDataOut.NCOL
    };
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp, len, "\n");
    testTypes(dataset, exp, len, "\r\n");
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
    byte[] exp = new byte[]{
            ParseDataset2.FVecDataOut.ECOL
    };
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp, len, "\n");
    testTypes(dataset, exp, len, "\r\n");
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
    byte[] exp = new byte[]{
            ParseDataset2.FVecDataOut.SCOL
    };
    final int len = 3;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp, len, "\n");
    testTypes(dataset, exp, len, "\r\n");
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
    byte[] exp = new byte[]{
            ParseDataset2.FVecDataOut.TCOL,
            ParseDataset2.FVecDataOut.ECOL,
            ParseDataset2.FVecDataOut.NCOL,
            ParseDataset2.FVecDataOut.ECOL
    };
    final int len = 5;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp, len, "\n");
    testTypes(dataset, exp, len, "\r\n");
  }

  // mixed ARFF file with numbers as strings
  @Test public void testMixed2() {
    String[] data = new String[] {
            "@RELATION mixed",
            "",
            "@ATTRIBUTE date     DATE",
            "@ATTRIBUTE string   {dog,cat,mouse}",
            "@ATTRIBUTE numeric  STRING", //force numbers to be STRINGS!
            "@ATTRIBUTE response {Y,N}",
            "",
            "@DATA",
            "2014-08-23,dog,3,Y",
            "2014-08-24,dog,4,Y",
            "2014-08-28,cat,5,Y",
            "2014-08-30,mouse,6,Y",
            "2013-07-20,cat,7,N"
    };
    byte[] exp = new byte[]{
            ParseDataset2.FVecDataOut.TCOL,
            ParseDataset2.FVecDataOut.ECOL,
            ParseDataset2.FVecDataOut.SCOL,
            ParseDataset2.FVecDataOut.ECOL
    };
    final int len = 5;

    String[] dataset = ParserTest.getDataForSeparator(',', data);

    testTypes(dataset, exp, len, "\n");
    testTypes(dataset, exp, len, "\r\n");
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

  // tab as separator
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
