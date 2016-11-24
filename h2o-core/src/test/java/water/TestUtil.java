package water;

import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.DefaultParserProviders;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.Log;
import water.util.StringUtils;
import water.util.Timer;
import water.util.TwoDimTable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Ignore("Support for tests, but no actual tests here")
public class TestUtil extends Iced {
  {
    ClassLoader loader = getClass().getClassLoader();
    loader.setDefaultAssertionStatus(true);
  }

  public final static boolean JACOCO_ENABLED = Boolean.parseBoolean(System.getProperty("test.jacocoEnabled", "false"));
  private static boolean _stall_called_before = false;
  private static String[] ignoreTestsNames;
  private static String[] doonlyTestsNames;
  protected static int _initial_keycnt = 0;
  protected static int MINCLOUDSIZE = Integer.parseInt(System.getProperty("cloudSize", "1"));

  public TestUtil() { this(1); }
  public TestUtil(int minCloudSize) {
    MINCLOUDSIZE = Math.max(MINCLOUDSIZE,minCloudSize);
    String ignoreTests = System.getProperty("ignore.tests");
    if (ignoreTests != null) {
      ignoreTestsNames = ignoreTests.split(",");
      if (ignoreTestsNames.length == 1 && ignoreTestsNames[0].equals("")) {
        ignoreTestsNames = null;
      }
    }
    String doonlyTests = System.getProperty("doonly.tests");
    if (doonlyTests != null) {
      doonlyTestsNames = doonlyTests.split(",");
      if (doonlyTestsNames.length == 1 && doonlyTestsNames[0].equals("")) {
        doonlyTestsNames = null;
      }
    }
  }

  // ==== Test Setup & Teardown Utilities ====
  // Stall test until we see at least X members of the Cloud
  public static void stall_till_cloudsize(int x) {
    stall_till_cloudsize(new String[] {}, x);
  }
  public static void stall_till_cloudsize(String[] args, int x) {
    x = Math.max(MINCLOUDSIZE, x);
    if( !_stall_called_before ) {
      H2O.main(args);
      H2O.registerRestApis(System.getProperty("user.dir"));
      _stall_called_before = true;
    }
    if (JACOCO_ENABLED) {
      H2O.waitForCloudSize(x, 300000);
    } else {
      H2O.waitForCloudSize(x, 30000);
    }
    _initial_keycnt = H2O.store_size();
  }

  @AfterClass
  public static void checkLeakedKeys() {
    int leaked_keys = H2O.store_size() - _initial_keycnt;
    int cnt=0;
    if( leaked_keys > 0 ) {
      for( Key k : H2O.localKeySet() ) {
        Value value = Value.STORE_get(k);
        // Ok to leak VectorGroups and the Jobs list
        if( value==null || value.isVecGroup() || value.isESPCGroup() || k == Job.LIST ||
            // Also leave around all attempted Jobs for the Jobs list
            (value.isJob() && value.<Job>get().isStopped()) ) {
          leaked_keys--;
        } else {
          System.out.println(k + " -> " + value.get());
          if( cnt++ < 10 )
            System.err.println("Leaked key: " + k + " = " + TypeMap.className(value.type()));
        }
      }
      if( 10 < leaked_keys ) System.err.println("... and "+(leaked_keys-10)+" more leaked keys");
    }
    assertTrue("Keys leaked: " + leaked_keys + ", cnt = " + cnt, leaked_keys <= 0 || cnt == 0);
    // Bulk brainless key removal.  Completely wipes all Keys without regard.
    new MRTask(){
      @Override public void setupLocal() {  H2O.raw_clear();  water.fvec.Vec.ESPC.clear(); }
    }.doAllNodes();
    _initial_keycnt = H2O.store_size();
  }


  /** Execute this rule before each test to print test name and test class */
  @Rule transient public TestRule logRule = new TestRule() {

    @Override public Statement apply(Statement base, Description description) {
      Log.info("###########################################################");
      Log.info("  * Test class name:  " + description.getClassName());
      Log.info("  * Test method name: " + description.getMethodName());
      Log.info("###########################################################");
      return base;
    }
  };

  /* Ignore tests specified in the ignore.tests system property */
  @Rule transient public TestRule runRule = new TestRule() {
    @Override public Statement apply(Statement base, Description description) {
      String testName = description.getClassName() + "#" + description.getMethodName();
      if ((ignoreTestsNames != null && Arrays.asList(ignoreTestsNames).contains(testName)) ||
          (doonlyTestsNames != null && !Arrays.asList(doonlyTestsNames).contains(testName))) {
        // Ignored tests trump do-only tests
        Log.info("#### TEST " + testName + " IGNORED");
        return new Statement() {
          @Override
          public void evaluate() throws Throwable {}
        };
      } else { return base; }
    }
  };

  @Rule transient public TestRule timerRule = new TestRule() {
    @Override public Statement apply(Statement base, Description description) {
      return new TimerStatement(base, description.getClassName()+"#"+description.getMethodName());
    }
    class TimerStatement extends Statement {
      private final Statement _base;
      private final String _tname;
      Throwable _ex;
      public TimerStatement(Statement base, String tname) { _base = base; _tname = tname;}
      @Override public void evaluate() throws Throwable {
        Timer t = new Timer();
        try {
          _base.evaluate();
        } catch( Throwable ex ) {
          _ex=ex;
          throw _ex;
        } finally {
          Log.info("#### TEST "+_tname+" EXECUTION TIME: " + t.toString());
        }
      }
    }
  };

  // ==== Data Frame Creation Utilities ====

  /** Hunt for test files in likely places.  Null if cannot find.
   *  @param fname Test filename
   *  @return      Found file or null */
  public static File find_test_file_static(String fname) {
    // When run from eclipse, the working directory is different.
    // Try pointing at another likely place
    File file = new File(fname);
    if( !file.exists() )
      file = new File("target/" + fname);
    if( !file.exists() )
      file = new File("../" + fname);
    if( !file.exists() )
      file = new File("../../" + fname);
    if( !file.exists() )
      file = new File("../target/" + fname);
    if( !file.exists() )
      file = new File(StringUtils.expandPath(fname));
    if( !file.exists() )
      file = null;
    return file;
  }

  /** Compare 2 frames
   *  @param fr1 Frame
   *  @param fr2 Frame
   *  @param epsilon Relative tolerance for floating point numbers
   *  @return true if equal  */
  public static boolean isIdenticalUpToRelTolerance(Frame fr1, Frame fr2, double epsilon) {
    if (fr1 == fr2) return true;
    if( fr1.numCols() != fr2.numCols() ) return false;
    if( fr1.numRows() != fr2.numRows() ) return false;
    Scope.enter();
    if( !fr1.isCompatible(fr2) ) fr1.makeCompatible(fr2);
    boolean identical = !(new Cmp1(epsilon).doAll(new Frame(fr1).add(fr2))._unequal);
    Scope.exit();
    return identical;
  }

  /** Compare 2 frames
   *  @param fr1 Frame
   *  @param fr2 Frame
   *  @return true if equal  */
  public static boolean isBitIdentical(Frame fr1, Frame fr2) {
    return isIdenticalUpToRelTolerance(fr1,fr2,0);
  }

  /** Hunt for test files in likely places.  Null if cannot find.
   *  @param fname Test filename
   *  @return      Found file or null */
  protected File find_test_file(String fname) {
    return find_test_file_static(fname);
  }

  private static void checkFileEntry(String name, File file) {
    assertNotNull("File not found: " + name, file);
    assertTrue("File should exist: " + name, file.exists());
  }

  private static void checkFile(String name, File file) {
    checkFileEntry(name, file);
    assertTrue("Expected a readable file: " + name, file.canRead());
  }

  private static File[] checkFolder(String name, File folder) {
    checkFileEntry(name, folder);
    assertTrue("Expected a folder: " + name, folder.isDirectory());
    File[] files = folder.listFiles();
    assertNotNull("No files found in " + folder, files);
    return files;
  }

  /** Find & parse a CSV file.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  public static Frame parse_test_file( String fname ) { return parse_test_file(Key.make(),fname); }
  public static Frame parse_test_file( Key outputKey, String fname) {
    File f = find_test_file_static(fname);
    checkFile(fname, f);
    NFSFileVec nfs = NFSFileVec.make(f);
    return ParseDataset.parse(outputKey, nfs._key);
  }

  protected Frame parse_test_file( Key outputKey, String fname , boolean guessSetup) {
    File f = find_test_file(fname);
    checkFile(fname, f);
    NFSFileVec nfs = NFSFileVec.make(f);
    return ParseDataset.parse(outputKey, new Key[]{nfs._key}, true, ParseSetup.guessSetup(new Key[]{nfs._key},false,1));
  }

  protected Frame parse_test_file( String fname, String na_string, int check_header, byte[] column_types ) {
    File f = find_test_file_static(fname);
    checkFile(fname, f);
    NFSFileVec nfs = NFSFileVec.make(f);

    Key[] res = {nfs._key};

    // create new parseSetup in order to store our na_string
    ParseSetup p = ParseSetup.guessSetup(res, new ParseSetup(DefaultParserProviders.GUESS_INFO,(byte) ',',true,
        check_header,0,null,null,null,null,null));

    // add the na_strings into p.
    if (na_string != null) {
      int column_number = p.getColumnTypes().length;
      int na_length = na_string.length() - 1;

      String[][] na_strings = new String[column_number][na_length + 1];

      for (int index = 0; index < column_number; index++) {
        na_strings[index][na_length] = na_string;
      }

      p.setNAStrings(na_strings);
    }

    if (column_types != null)
      p.setColumnTypes(column_types);

    return ParseDataset.parse(Key.make(), res, true, p);

  }

  /** Find & parse a folder of CSV files.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  protected Frame parse_test_folder( String fname ) {
    File folder = find_test_file(fname);
    File[] files = checkFolder(fname, folder);
    Arrays.sort(files);
    ArrayList<Key> keys = new ArrayList<>();
    for( File f : files )
      if( f.isFile() )
        keys.add(NFSFileVec.make(f)._key);
    Key[] res = new Key[keys.size()];
    keys.toArray(res);
    return ParseDataset.parse(Key.make(), res);
  }


  /**
   * Parse a folder with csv files when a single na_string is specified.
   *
   * @param fname name of folder
   * @param na_string string for NA in a column
   * @return
   */
  protected static Frame parse_test_folder( String fname, String na_string, int check_header, byte[] column_types ) {
    File folder = find_test_file_static(fname);
    File[] files = checkFolder(fname, folder);
    Arrays.sort(files);
    ArrayList<Key> keys = new ArrayList<>();
    for( File f : files )
      if( f.isFile() )
        keys.add(NFSFileVec.make(f)._key);

    Key[] res = new Key[keys.size()];
    keys.toArray(res);  // generated the necessary key here

    // create new parseSetup in order to store our na_string
    ParseSetup p = ParseSetup.guessSetup(res, new ParseSetup(DefaultParserProviders.GUESS_INFO,(byte) ',',true,
        check_header,0,null,null,null,null,null));

    // add the na_strings into p.
    if (na_string != null) {
      int column_number = p.getColumnTypes().length;
      int na_length = na_string.length() - 1;

      String[][] na_strings = new String[column_number][na_length + 1];

      for (int index = 0; index < column_number; index++) {
        na_strings[index][na_length] = na_string;
      }

      p.setNAStrings(na_strings);
    }

    if (column_types != null)
      p.setColumnTypes(column_types);

    return ParseDataset.parse(Key.make(), res, true, p);

  }


  /** A Numeric Vec from an array of ints
   *  @param rows Data
   *  @return The Vec  */
  public static Vec vec(int...rows) { return vec(null, rows); }
  /** A Categorical/Factor Vec from an array of ints - with categorical/domain mapping
   *  @param domain Categorical/Factor names, mapped by the data values
   *  @param rows Data
   *  @return The Vec  */
  public static Vec vec(String[] domain, int ...rows) {
    Key<Vec> k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k,Vec.T_NUM);
    avec.setDomain(domain);
    NewChunk chunk = new NewChunk(avec, 0);
    for( int r : rows ) chunk.addNum(r);
    chunk.close(0, fs);
    Vec vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  /** A numeric Vec from an array of ints */
  public static Vec ivec(int...rows) {
    return vec(null, rows);
  }

  /** A categorical Vec from an array of strings */
  public static Vec cvec(String ...rows) {
    return cvec(null, rows);
  }

  public static Vec cvec(String[] domain, String ...rows) {
    HashMap<String, Integer> domainMap = new HashMap<>(10);
    ArrayList<String> domainList = new ArrayList<>(10);
    if (domain != null) {
      int j = 0;
      for (String s : domain) {
        domainMap.put(s, j++);
        domainList.add(s);
      }
    }
    int[] irows = new int[rows.length];
    for (int i = 0, j = 0; i < rows.length; i++) {
      String s = rows[i];
      if (!domainMap.containsKey(s)) {
        domainMap.put(s, j++);
        domainList.add(s);
      }
      irows[i] = domainMap.get(s);
    }
    return vec(domainList.toArray(new String[]{}), irows);
  }

  /** A numeric Vec from an array of doubles */
  public static Vec dvec(double...rows) {
    Key<Vec> k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k, Vec.T_NUM);
    NewChunk chunk = new NewChunk(avec, 0);
    for (double r : rows)
      chunk.addNum(r);
    chunk.close(0, fs);
    Vec vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  /** A time Vec from an array of ints */
  public static Vec tvec(int...rows) {
    Key<Vec> k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k, Vec.T_TIME);
    NewChunk chunk = new NewChunk(avec, 0);
    for (int r : rows)
      chunk.addNum(r);
    chunk.close(0, fs);
    Vec vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  /** A string Vec from an array of strings */
  public static Vec svec(String...rows) {
    Key<Vec> k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k, Vec.T_STR);
    NewChunk chunk = new NewChunk(avec, 0);
    for (String r : rows)
      chunk.addStr(r);
    chunk.close(0, fs);
    Vec vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  /** A string Vec from an array of strings */
  public static Vec uvec(UUID...rows) {
    Key<Vec> k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k, Vec.T_UUID);
    NewChunk chunk = new NewChunk(avec, 0);
    for (UUID r : rows)
      chunk.addUUID(r);
    chunk.close(0, fs);
    Vec vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  // Shortcuts for initializing constant arrays
  public static String[]   ar (String ...a)   { return a; }
  public static String[][] ar (String[] ...a) { return a; }
  public static byte  []   ar (byte   ...a)   { return a; }
  public static long  []   ar (long   ...a)   { return a; }
  public static long[][]   ar (long[] ...a)   { return a; }
  public static int   []   ari(int    ...a)   { return a; }
  public static int [][]   ar (int[]  ...a)   { return a; }
  public static float []   arf(float  ...a)   { return a; }
  public static double[]   ard(double ...a)   { return a; }
  public static double[][] ard(double[] ...a) { return a; }
  public static double[][] ear (double ...a) {
    double[][] r = new double[a.length][1];
    for (int i=0; i<a.length;i++) r[i][0] = a[i];
    return r;
  }

  // Java7+  @SafeVarargs
  public static <T> T[] aro(T ...a) { return a ;}

  // ==== Comparing Results ====

  public static void assertVecEquals(Vec expecteds, Vec actuals, double delta) {
    assertEquals(expecteds.length(), actuals.length());
    for(int i = 0; i < expecteds.length(); i++) {
      final String message = i + ": " + expecteds.at(i) + " != " + actuals.at(i) + ", chunkIds = " + expecteds.elem2ChunkIdx(i) + ", " + actuals.elem2ChunkIdx(i) + ", row in chunks = " + (i - expecteds.chunkForRow(i).start()) + ", " + (i - actuals.chunkForRow(i).start());
      assertEquals(message, expecteds.at(i), actuals.at(i), delta);
    }
  }

  public static void assertUUIDVecEquals(Vec expecteds, Vec actuals) {
    assertEquals(expecteds.length(), actuals.length());
    assertEquals("Vec types match", expecteds.get_type_str(), actuals.get_type_str());
    for(int i = 0; i < expecteds.length(); i++) {
      UUID expected = new UUID(expecteds.at16l(i), expecteds.at16h(i));
      UUID actual = new UUID(actuals.at16l(i), actuals.at16h(i));
      final String message = i + ": " + expected + " != " + actual + ", chunkIds = " + expecteds.elem2ChunkIdx(i) + ", " + actuals.elem2ChunkIdx(i) + ", row in chunks = " + (i - expecteds.chunkForRow(i).start()) + ", " + (i - actuals.chunkForRow(i).start());
      assertEquals(message, expected, actual);
    }
  }

  private static String toStr(BufferedString bs) { return bs != null ? bs.toString() : null; }

  public static void assertStringVecEquals(Vec expecteds, Vec actuals) {
    assertEquals(expecteds.length(), actuals.length());
    assertEquals("Vec types match", expecteds.get_type_str(), actuals.get_type_str());
    for(int i = 0; i < expecteds.length(); i++) {
      String expected = toStr(expecteds.atStr(new BufferedString(), i));
      String actual = toStr(actuals.atStr(new BufferedString(), i));
      final String message = i + ": " + expected + " != " + actual + ", chunkIds = " + expecteds.elem2ChunkIdx(i) + ", " + actuals.elem2ChunkIdx(i) + ", row in chunks = " + (i - expecteds.chunkForRow(i).start()) + ", " + (i - actuals.chunkForRow(i).start());
      assertEquals(message, expected, actual);
    }
  }

  private static String getFactorAsString(Vec v, long row) { return v.isNA(row) ? null : v.factor((long) v.at(row)); }

  public static void assertCatVecEquals(Vec expecteds, Vec actuals) {
    assertEquals(expecteds.length(), actuals.length());
    assertEquals("Vec types match", expecteds.get_type_str(), actuals.get_type_str());
    for(int i = 0; i < expecteds.length(); i++) {
      String expected = getFactorAsString(expecteds, i);
      String actual = getFactorAsString(actuals, i);
      final String message = i + ": " + expected + " != " + actual + ", chunkIds = " + expecteds.elem2ChunkIdx(i) + ", " + actuals.elem2ChunkIdx(i) + ", row in chunks = " + (i - expecteds.chunkForRow(i).start()) + ", " + (i - actuals.chunkForRow(i).start());
      assertEquals(message, expected, actual);
    }
  }

  public static void checkStddev(double[] expected, double[] actual, double threshold) {
    for(int i = 0; i < actual.length; i++)
      Assert.assertEquals(expected[i], actual[i], threshold);
  }

  public static boolean[] checkEigvec(double[][] expected, double[][] actual, double threshold) {
    int nfeat = actual.length;
    int ncomp = actual[0].length;
    boolean[] flipped = new boolean[ncomp];

    for(int j = 0; j < ncomp; j++) {
      // flipped[j] = Math.abs(expected[0][j] - actual[0][j]) > threshold;
      flipped[j] = Math.abs(expected[0][j] - actual[0][j]) > Math.abs(expected[0][j] + actual[0][j]);
      for(int i = 0; i < nfeat; i++) {
        Assert.assertEquals(expected[i][j], flipped[j] ? -actual[i][j] : actual[i][j], threshold);
      }
    }
    return flipped;
  }

  public static boolean[] checkEigvec(double[][] expected, TwoDimTable actual, double threshold) {
    int nfeat = actual.getRowDim();
    int ncomp = actual.getColDim();
    boolean[] flipped = new boolean[ncomp];

    for(int j = 0; j < ncomp; j++) {
      flipped[j] = Math.abs(expected[0][j] - (double)actual.get(0,j)) > threshold;
      for(int i = 0; i < nfeat; i++) {
        Assert.assertEquals(expected[i][j], flipped[j] ? -(double)actual.get(i,j) : (double)actual.get(i,j), threshold);
      }
    }
    return flipped;
  }

  public static boolean[] checkProjection(Frame expected, Frame actual, double threshold, boolean[] flipped) {
    assertEquals("Number of columns", expected.numCols(), actual.numCols());
    assertEquals("Number of columns in flipped", expected.numCols(), flipped.length);
    int nfeat = (int) expected.numRows();
    int ncomp = expected.numCols();

    for(int j = 0; j < ncomp; j++) {
      Vec.Reader vexp = expected.vec(j).new Reader();
      Vec.Reader vact = actual.vec(j).new Reader();
      Assert.assertEquals(vexp.length(), vact.length());
      for (int i = 0; i < nfeat; i++) {
        Assert.assertEquals(vexp.at8(i), flipped[j] ? -vact.at8(i) : vact.at8(i), threshold);
      }
    }
    return flipped;
  }

  // Run tests from cmd-line since testng doesn't seem to be able to it.
  public static void main( String[] args ) {
    H2O.main(new String[0]);
    for( String arg : args ) {
      try {
        System.out.println("=== Starting "+arg);
        Class<?> clz = Class.forName(arg);
        Method main = clz.getDeclaredMethod("main");
        main.invoke(null);
      } catch( InvocationTargetException ite ) {
        Throwable e = ite.getCause();
        e.printStackTrace();
        try { Thread.sleep(100); } catch( Exception ignore ) { }
      } catch( Exception e ) {
        e.printStackTrace();
        try { Thread.sleep(100); } catch( Exception ignore ) { }
      } finally {
        System.out.println("=== Stopping "+arg);
      }
    }
    try { Thread.sleep(100); } catch( Exception ignore ) { }
    if( args.length != 0 )
      UDPRebooted.T.shutdown.send(H2O.SELF);
  }

  protected static class Cmp1 extends MRTask<Cmp1> {
    final double _epsilon;
    Cmp1( double epsilon ) { _epsilon = epsilon; }
    boolean _unequal;
    @Override public void map( Chunk chks[] ) {
      for( int cols=0; cols<chks.length>>1; cols++ ) {
        Chunk c0 = chks[cols                 ];
        Chunk c1 = chks[cols+(chks.length>>1)];
        for( int rows = 0; rows < chks[0]._len; rows++ ) {
          if (c0 instanceof C16Chunk && c1 instanceof C16Chunk) {
            if (! (c0.isNA(rows) && c1.isNA(rows))) {
              long lo0 = c0.at16l(rows), lo1 = c1.at16l(rows);
              long hi0 = c0.at16h(rows), hi1 = c1.at16h(rows);
              if (lo0 != lo1 || hi0 != hi1) {
                _unequal = true;
                return;
              }
            }
          } else if (c0 instanceof CStrChunk && c1 instanceof CStrChunk) {
            if (!(c0.isNA(rows) && c1.isNA(rows))) {
              BufferedString s0 = new BufferedString(), s1 = new BufferedString();
              c0.atStr(s0, rows); c1.atStr(s1, rows);
              if (s0.compareTo(s1) != 0) {
                _unequal = true;
                return;
              }
            }
          }else {
            double d0 = c0.atd(rows), d1 = c1.atd(rows);
            if (!(Double.isNaN(d0) && Double.isNaN(d1)) && !(Math.abs(d0-d1)<=Math.abs(d0+d1)*_epsilon) ) {
              _unequal = true;
              return;
            }
          }
        }
      }
    }
    @Override public void reduce( Cmp1 cmp ) { _unequal |= cmp._unequal; }
  }

  public static void assertFrameAssertion(FrameAssertion frameAssertion) {
    int[] dim = frameAssertion.dim;
    Frame frame = null;
    try {
      frame = frameAssertion.prepare();
      assertEquals("Frame has to have expected number of columns", dim[0], frame.numCols());
      assertEquals("Frame has to have expected number of rows", dim[1], frame.numRows());
      frameAssertion.check(frame);
    } finally {
      frameAssertion.done(frame);
      if (frame != null)
        frame.delete();
    }
  }

  public static abstract class FrameAssertion {
    protected final String file;
    private final int[] dim; // columns X rows

    public FrameAssertion(String file, int[] dim) {
      this.file = file;
      this.dim = dim;
    }

    public Frame prepare() {
      return TestUtil.parse_test_file(file);
    }

    public void done(Frame frame) {}

    public void check(Frame frame) {}

    public final int nrows() { return dim[1]; }
    public final int ncols() { return dim[0]; }
  }

  public static abstract class GenFrameAssertion extends FrameAssertion {

    public GenFrameAssertion(String file, int[] dim) {
      super(file, dim);
    }
    File generatedFile;

    protected abstract File prepareFile() throws IOException;

    @Override
    public Frame prepare() {
      try {
        File f = generatedFile = prepareFile();
        System.out.println("File generated into: " + f.getCanonicalPath());
        if (f.isDirectory()) {
          return parse_test_folder(f.getCanonicalPath(), null, ParseSetup.HAS_HEADER, null);
        } else {
          return parse_test_file(f.getCanonicalPath());
        }
      } catch (IOException e) {
        throw new RuntimeException("Cannot prepare test frame from file: " + file, e);
      }
    }

    @Override
    public void done(Frame frame) {
      if (generatedFile != null) {
        generatedFile.deleteOnExit();
        FileUtils.deleteQuietly(generatedFile);
      }
    }
  }

  @BeforeClass
  public static void hi() {
    stall_till_cloudsize(1);
    Scope.enter();
  }

  @AfterClass public static void bye() { Scope.exit(); }

  protected static Vec willDrop(Vec v) { return Scope.track(v); }
}
