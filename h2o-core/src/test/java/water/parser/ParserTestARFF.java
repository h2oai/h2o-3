package water.parser;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

public class ParserTestARFF extends TestUtil {

  @Test
  public void testSimple() {
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

  @Test
  public void testSpaceSep() {
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

  @Test
  public void testWeirdSep() {
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

  @Test
  public void testWeirdSep2() {
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

  @Test
  @Ignore
  public void testFolder1() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/arff/folder1/" );
      k1 = parse_test_file  ("smalldata/junit/arff/iris.arff");
      Assert.assertTrue("parsed values do not match!",isBitIdentical(k1,k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  @Test
  @Ignore
  public void testFolder2() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/arff/folder2/" );
      k1 = parse_test_file  ("smalldata/junit/arff/iris.arff");
      Assert.assertTrue("parsed values do not match!",isBitIdentical(k1,k2));
      Assert.assertTrue("column names do not match!",  Arrays.equals(k2.names(), k1.names()));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }
}
