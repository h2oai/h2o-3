package water;

import hex.CreateFrame;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.DefaultParserProviders;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.FileUtils;
import water.util.Log;
import water.util.Timer;
import water.util.TwoDimTable;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;

@Ignore("Support for tests, but no actual tests here")
public class TestUtil extends Iced {
  { // we need assertions to be checked at least when tests are running
    ClassLoader loader = getClass().getClassLoader();
    loader.setDefaultAssertionStatus(true);
  }

  public final static boolean JACOCO_ENABLED = Boolean.parseBoolean(System.getProperty("test.jacocoEnabled", "false"));
  private static boolean _stall_called_before = false;
  private static String[] ignoreTestsNames;
  private static String[] doonlyTestsNames;
  protected static int _initial_keycnt = 0;
  /** Minimal cloud size to start test. */
  protected static int MINCLOUDSIZE = Integer.parseInt(System.getProperty("cloudSize", "1"));
  /** Default time in ms to wait for clouding */
  protected static int DEFAULT_TIME_FOR_CLOUDING = 30000 /* ms */;

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
  protected static int getDefaultTimeForClouding() {
    return JACOCO_ENABLED
        ? DEFAULT_TIME_FOR_CLOUDING * 10
        : DEFAULT_TIME_FOR_CLOUDING;
  }

  public static void stall_till_cloudsize(int x) {
    stall_till_cloudsize(x, getDefaultTimeForClouding());
  }

  public static void stall_till_cloudsize(int x, int timeout) {
    stall_till_cloudsize(new String[] {}, x, timeout);
  }

  public static void stall_till_cloudsize(String[] args, int x) {
    stall_till_cloudsize(args, x, getDefaultTimeForClouding());
  }

  public static void stall_till_cloudsize(String[] args, int x, int timeout) {
    x = Math.max(MINCLOUDSIZE, x);
    if( !_stall_called_before ) {
      H2O.main(args);
      H2O.registerResourceRoot(new File(System.getProperty("user.dir") + File.separator + "h2o-web/src/main/resources/www"));
      H2O.registerResourceRoot(new File(System.getProperty("user.dir") + File.separator + "h2o-core/src/main/resources/www"));
      ExtensionManager.getInstance().registerRestApiExtensions();
      _stall_called_before = true;
    }
    H2O.waitForCloudSize(x, timeout);
    _initial_keycnt = H2O.store_size();
    // Finalize registration of REST API to enable tests which are touching Schemas.
    H2O.startServingRestApi();
  }


  /**
   * Converts a H2OFrame to a csv file for debugging purposes.
   *
   * @param fileNameWithPath: String containing filename with path that will contain the H2O Frame
   * @param h2oframe: H2O Frame to be saved as CSV file.
   * @param header: boolean to decide if column names should be saved.  Set to false if don't care.
   * @param hex_string: boolean to decide if the double values are written in hex.  Set to false if don't care.
   * @throws IOException
   */
  public static void writeFrameToCSV(String fileNameWithPath, Frame h2oframe, boolean header, boolean hex_string)
          throws IOException {
    InputStream frameToStream = h2oframe.toCSV(header, hex_string);    // read in frame as Inputstream
    // write Inputstream to a real file
    File targetFile = new File(fileNameWithPath);
    OutputStream outStream = new FileOutputStream(targetFile);

    byte[] buffer = new byte[1<<20];
    int bytesRead;

    while((bytesRead=frameToStream.read(buffer)) > 0) { // for our toCSV stream, return 0 as EOF, not -1
      outStream.write(buffer, 0, bytesRead);
    }
    frameToStream.close();
    outStream.flush();
    outStream.close();
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
          System.out.println(k + " -> " + (value.type() != TypeMap.PRIM_B ? value.get() : "byte[]"));
          if( cnt++ < 10 )
            System.err.println("Leaked key: " + k + " = " + TypeMap.className(value.type()));
        }
      }
      if( 10 < leaked_keys ) System.err.println("... and "+(leaked_keys-10)+" more leaked keys");
    }
    assertTrue("Keys leaked: " + leaked_keys + ", cnt = " + cnt, leaked_keys <= 0 || cnt == 0);
    // Bulk brainless key removal.  Completely wipes all Keys without regard.
    new DKVCleaner().doAllNodes();
    _initial_keycnt = H2O.store_size();
  }

  /**
   * generate random frames containing enum columns only
   * @param numCols
   * @param numRows
   * @param num_factor
   * @return
   */
  protected static Frame generate_enum_only(int numCols, int numRows, int num_factor, double missingfrac) {
    CreateFrame cf = new CreateFrame();
    cf.rows= numRows;
    cf.cols = numCols;
    cf.factors=num_factor;
    cf.binary_fraction = 0;
    cf.integer_fraction = 0;
    cf.categorical_fraction = 1;
    cf.has_response=false;
    cf.missing_fraction = missingfrac;
    cf.seed = System.currentTimeMillis();
    System.out.println("Createframe parameters: rows: "+numRows+" cols:"+numCols+" seed: "+cf.seed);
    return cf.execImpl().get();
  }

  protected static int[] rangeFun(int numEle, int offset) {
    int[] ranges = new int[numEle];

    for (int index = 0; index < numEle; index++) {
      ranges[index] = index+offset;
    }
    return ranges;
  }

  protected static int[] sortDir(int numEle, Random rand) {
    int[] sortDir = new int[numEle];
    int[] dirs = new int[]{-1,1};

    for (int index = 0; index < numEle; index++) {
      sortDir[index] = dirs[rand.nextInt(2)];
    }
    return sortDir;
  }
  /**
   * generate random frames containing enum columns only
   * @param numCols
   * @param numRows
   * @return
   */
  protected static Frame generate_int_only(int numCols, int numRows, int iRange, double missingfrac) {
    CreateFrame cf = new CreateFrame();
    cf.rows= numRows;
    cf.cols = numCols;
    cf.binary_fraction = 0;
    cf.integer_fraction = 1;
    cf.categorical_fraction = 0;
    cf.has_response=false;
    cf.missing_fraction = missingfrac;
    cf.integer_range=iRange;
    cf.seed = System.currentTimeMillis();
    System.out.println("Createframe parameters: rows: "+numRows+" cols:"+numCols+" seed: "+cf.seed);
    return cf.execImpl().get();
  }

  private static class DKVCleaner extends MRTask<DKVCleaner> {
    @Override public void setupLocal() {  H2O.raw_clear();  water.fvec.Vec.ESPC.clear(); }
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
      boolean ignored = false;
      if (ignoreTestsNames != null && ignoreTestsNames.length > 0) {
        for (String tn : ignoreTestsNames) {
          if (testName.startsWith(tn)) {
            ignored = true;
            break;
          }
        }
      }
      if (doonlyTestsNames != null && doonlyTestsNames.length > 0) {
        ignored = true;
        for (String tn : doonlyTestsNames) {
          if (testName.startsWith(tn)) {
            ignored = false;
            break;
          }
        }
      }
      if (ignored) {
        // Ignored tests trump do-only tests
        Log.info("#### TEST " + testName + " IGNORED");
        return new Statement() {
          @Override
          public void evaluate() throws Throwable {}
        };
      } else {
        return base;
      }
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

  static File[] contentsOf(String name, File folder) {
    try {
      return FileUtils.contentsOf(folder, name);
    } catch (IOException ioe) {
      fail(ioe.getMessage());
      return null;
    }
  }

  /** Find & parse a CSV file.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  public static Frame parse_test_file( String fname) {
    return parse_test_file(Key.make(), fname);
  }

  public static Frame parse_test_file( String fname, int[] skipped_columns) {
    return parse_test_file(Key.make(), fname, skipped_columns);
  }

  public static NFSFileVec makeNfsFileVec(String fname) {
    try {
      return NFSFileVec.make(fname);
    } catch (IOException ioe) {
      fail(ioe.getMessage());
      return null;
    }
  }

  public static Frame parse_test_file( Key outputKey, String fname) {
    return parse_test_file(outputKey, fname, new int[]{});
  }

  public static Frame parse_test_file( Key outputKey, String fname, int[] skippedColumns) {
    return parse_test_file(outputKey, fname, null, skippedColumns);
  }

  public static Frame parse_test_file(String fname, ParseSetupTransformer transformer) {
    return parse_test_file(Key.make(), fname, transformer);
  }

  public static Frame parse_test_file(String fname, ParseSetupTransformer transformer, int[] skippedColumns) {
    return parse_test_file(Key.make(), fname, transformer, skippedColumns);
  }

  public static Frame parse_test_file( Key outputKey, String fname, ParseSetupTransformer transformer) {
    return parse_test_file(outputKey, fname, transformer, null);
  }

  public static Frame parse_test_file( Key outputKey, String fname, ParseSetupTransformer transformer, int[] skippedColumns) {
    NFSFileVec nfs = makeNfsFileVec(fname);
    ParseSetup guessedSetup = ParseSetup.guessSetup(new Key[]{nfs._key}, false, ParseSetup.GUESS_HEADER);
    if (skippedColumns != null) {
      guessedSetup.setSkippedColumns(skippedColumns);
      guessedSetup.setParseColumnIndices(guessedSetup.getNumberColumns(), skippedColumns);
    }

    if (transformer != null)
      guessedSetup = transformer.transformSetup(guessedSetup);
    return ParseDataset.parse(outputKey, new Key[]{nfs._key}, true, guessedSetup);
  }

  protected Frame parse_test_file( Key outputKey, String fname, boolean guessSetup) {
    return parse_test_file(outputKey, fname, guessSetup, null);
  }

  protected Frame parse_test_file( Key outputKey, String fname, boolean guessSetup, int[] skippedColumns) {
    NFSFileVec nfs = makeNfsFileVec(fname);
    ParseSetup guessParseSetup = ParseSetup.guessSetup(new Key[]{nfs._key},false,1);
    if (skippedColumns != null) {
      guessParseSetup.setSkippedColumns(skippedColumns);
      guessParseSetup.setParseColumnIndices(guessParseSetup.getNumberColumns(), skippedColumns);
    }
    return ParseDataset.parse(outputKey, new Key[]{nfs._key}, true, ParseSetup.guessSetup(new Key[]{nfs._key},false,1));
  }

  protected Frame parse_test_file( String fname, String na_string, int check_header, byte[] column_types) {
    return parse_test_file(fname, na_string, check_header, column_types, null, null);
  }

  protected Frame parse_test_file( String fname, String na_string, int check_header, byte[] column_types, ParseSetupTransformer transformer) {
    return parse_test_file( fname, na_string, check_header, column_types, transformer,null);
  }

  protected Frame parse_test_file( String fname, String na_string, int check_header, byte[] column_types, ParseSetupTransformer transformer, int[] skippedColumns) {
    NFSFileVec nfs = makeNfsFileVec(fname);

    Key[] res = {nfs._key};

    // create new parseSetup in order to store our na_string
    ParseSetup p = ParseSetup.guessSetup(res, new ParseSetup(DefaultParserProviders.GUESS_INFO,(byte) ',',true,
        check_header,0,null,null,null,null,null));
    if (skippedColumns != null) {
      p.setSkippedColumns(skippedColumns);
      p.setParseColumnIndices(p.getNumberColumns(), skippedColumns);
    }

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

    if (transformer != null)
      p = transformer.transformSetup(p);

    return ParseDataset.parse(Key.make(), res, true, p);

  }

  /** Find & parse a folder of CSV files.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  protected Frame parse_test_folder( String fname ) {
    return parse_test_folder(fname, null);
  }

  /** Find & parse a folder of CSV files.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  protected Frame parse_test_folder( String fname, int[] skippedColumns ) {
    File folder = FileUtils.locateFile(fname);
    File[] files = contentsOf(fname, folder);
    Arrays.sort(files);
    ArrayList<Key> keys = new ArrayList<>();
    for( File f : files )
      if( f.isFile() )
        keys.add(NFSFileVec.make(f)._key);
    Key[] res = new Key[keys.size()];
    keys.toArray(res);
    return ParseDataset.parse(skippedColumns, Key.make(), res);
  }
  /**
   * Parse a folder with csv files when a single na_string is specified.
   *
   * @param fname name of folder
   * @param na_string string for NA in a column
   * @return
   */
  protected static Frame parse_test_folder( String fname, String na_string, int check_header, byte[] column_types,
                                            ParseSetupTransformer transformer) {
    return parse_test_folder(fname, na_string, check_header, column_types, transformer, null);
  }

  /**
   * Parse a folder with csv files when a single na_string is specified.
   *
   * @param fname name of folder
   * @param na_string string for NA in a column
   * @return
   */
  protected static Frame parse_test_folder( String fname, String na_string, int check_header, byte[] column_types,
                                            ParseSetupTransformer transformer, int[] skipped_columns) {
    File folder = FileUtils.locateFile(fname);
    File[] files = contentsOf(fname, folder);
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
    if (skipped_columns != null) {
      p.setSkippedColumns(skipped_columns);
      p.setParseColumnIndices(p.getNumberColumns(), skipped_columns);
    }
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

    if (transformer != null)
      p = transformer.transformSetup(p);

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
      assertEquals(expected[i], actual[i], threshold);
  }

  public static void checkIcedArrays(IcedWrapper[][] expected, IcedWrapper[][] actual, double threshold) {
    for(int i = 0; i < actual.length; i++)
      for (int j = 0; j < actual[0].length; j++)
      assertEquals(expected[i][j].d, actual[i][j].d, threshold);
  }

  public static boolean[] checkEigvec(double[][] expected, double[][] actual, double threshold) {
    int nfeat = actual.length;
    int ncomp = actual[0].length;
    boolean[] flipped = new boolean[ncomp];

    for(int j = 0; j < ncomp; j++) {
      // flipped[j] = Math.abs(expected[0][j] - actual[0][j]) > threshold;
      flipped[j] = Math.abs(expected[0][j] - actual[0][j]) > Math.abs(expected[0][j] + actual[0][j]);
      for(int i = 0; i < nfeat; i++) {
        assertEquals(expected[i][j], flipped[j] ? -actual[i][j] : actual[i][j], threshold);
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
        assertEquals(expected[i][j], flipped[j] ? -(double)actual.get(i,j) : (double)actual.get(i,j), threshold);
      }
    }
    return flipped;
  }

  public static boolean equalTwoDimTables(TwoDimTable tab1, TwoDimTable tab2, double tol) {
    boolean same = true;
    //compare colHeaders
    same = Arrays.equals(tab1.getColHeaders(), tab2.getColHeaders()) &&
            Arrays.equals(tab1.getColTypes(), tab2.getColTypes());
    String[] colTypes = tab2.getColTypes();
    IcedWrapper[][] cellValues1 = tab1.getCellValues();
    IcedWrapper[][] cellValues2 = tab2.getCellValues();

    same = same && cellValues1.length==cellValues2.length;
    if (!same)
      return false;

    // compare cell values
    for (int cindex = 0; cindex < cellValues1.length; cindex++) {
      same = same && cellValues1[cindex].length==cellValues2[cindex].length;
      if (!same)
        return false;
      for (int index=0; index < cellValues1[cindex].length; index++) {
        if (colTypes[index].equals("double")) {
          same = same && Math.abs(Double.parseDouble(cellValues1[cindex][index].toString())-Double.parseDouble(cellValues2[cindex][index].toString()))<tol;
        } else {
          same = same && cellValues1[cindex][index].toString().equals(cellValues2[cindex][index].toString());
        }
      }
    }
    return same;
  }

  public static boolean[] checkEigvec(TwoDimTable expected, TwoDimTable actual, double threshold) {
    int nfeat = actual.getRowDim();
    int ncomp = actual.getColDim();
    boolean[] flipped = new boolean[ncomp];

    // better way to get sign
    for (int j=0; j < ncomp; j++) {
      for (int i = 0; i < nfeat; i++) {
        if (Math.abs((Double) expected.get(i,j))>0.0 && Math.abs((Double) actual.get(i,j))>0.0) { // only non zeros
          flipped[j] = !(Math.signum((Double)expected.get(i,j))==Math.signum((Double)actual.get(i,j)));
          break;
        }
      }
    }

    for(int j = 0; j < ncomp; j++) {
      for(int i = 0; i < nfeat; i++) {
        assertEquals((double) expected.get(i,j), flipped[j] ? -(double)actual.get(i,j) : (double)actual.get(i,j), threshold);
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
      assertEquals(vexp.length(), vact.length());
      for (int i = 0; i < nfeat; i++) {
        if (vexp.isNA(i) || vact.isNA(i)) {
          continue;
        }
        // only perform comparison when data is not NAN
        assertEquals(vexp.at8(i), flipped[j] ? -vact.at8(i) : vact.at8(i), threshold);

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
    public Cmp1( double epsilon ) { _epsilon = epsilon; }
    public boolean _unequal;
    @Override public void map( Chunk chks[] ) {
      for( int cols=0; cols<chks.length>>1; cols++ ) {
        Chunk c0 = chks[cols                 ];
        Chunk c1 = chks[cols+(chks.length>>1)];
        for( int rows = 0; rows < chks[0]._len; rows++ ) {
          if (c0.isNA(rows) != c1.isNA(rows)) {
            _unequal = true;
            return;
          } else if (!(c0.isNA(rows) && c1.isNA(rows))) {
            if (c0 instanceof C16Chunk && c1 instanceof C16Chunk) {
              long lo0 = c0.at16l(rows), lo1 = c1.at16l(rows);
              long hi0 = c0.at16h(rows), hi1 = c1.at16h(rows);
              if (lo0 != lo1 || hi0 != hi1) {
                _unequal = true;
                return;
              }
            } else if (c0 instanceof CStrChunk && c1 instanceof CStrChunk) {
              BufferedString s0 = new BufferedString(), s1 = new BufferedString();
              c0.atStr(s0, rows);
              c1.atStr(s1, rows);
              if (s0.compareTo(s1) != 0) {
                _unequal = true;
                return;
              }
            } else if ((c0 instanceof C8Chunk) && (c1 instanceof C8Chunk)) {
              long d0 = c0.at8(rows), d1 = c1.at8(rows);
              if (d0 != d1) {
                _unequal = true;
                return;
              }
            } else {
              double d0 = c0.atd(rows), d1 = c1.atd(rows);
              if (!(Math.abs(d0 - d1) <= Math.abs(d0 + d1) * _epsilon)) {
                _unequal = true;
                return;
              }
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
      this(file, dim, null);
    }
    public GenFrameAssertion(String file, int[] dim, ParseSetupTransformer psTransformer) {
      super(file, dim);
      this.psTransformer = psTransformer;
    }

    protected File generatedFile;
    protected ParseSetupTransformer psTransformer;

    protected abstract File prepareFile() throws IOException;

    @Override
    public Frame prepare() {
      try {
        File f = generatedFile = prepareFile();
        System.out.println("File generated into: " + f.getCanonicalPath());
        if (f.isDirectory()) {
          return parse_test_folder(f.getCanonicalPath(), null, ParseSetup.HAS_HEADER, null, psTransformer);
        } else {
          return parse_test_file(f.getCanonicalPath(), psTransformer);
        }
      } catch (IOException e) {
        throw new RuntimeException("Cannot prepare test frame from file: " + file, e);
      }
    }

    @Override
    public void done(Frame frame) {
      if (generatedFile != null) {
        generatedFile.deleteOnExit();
        org.apache.commons.io.FileUtils.deleteQuietly(generatedFile);
      }
    }
  }

  public static class Datasets {
    public static Frame iris() {
      return parse_test_file(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
    }
  }

  /**
   * Tests can hook into the parse process using this interface and modify some of the guessed parameters.
   * This simplifies the test workflow as usually most of the guessed parameters are correct and the test really only
   * needs to modify/add few parameters.
   */
  public interface ParseSetupTransformer {
    ParseSetup transformSetup(ParseSetup guessedSetup);
  }

  /**
   *
   * @param frame
   * @param columnName column's name to be factorized
   * @return Frame with factorized column
   */
  public Frame asFactor(Frame frame, String columnName) {
    Vec vec = frame.vec(columnName);
    frame.replace(frame.find(columnName), vec.toCategoricalVec());
    vec.remove();
    return frame;
  }

  public void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }

  public void printOutColumnsMetadata(Frame fr) {
    for (String header : fr.toTwoDimTable().getColHeaders()) {
      String type = fr.vec(header).get_type_str();
      int cardinality = fr.vec(header).cardinality();
      System.out.println(header + " - " + type + String.format("; Cardinality = %d", cardinality));
    }
  }

}
