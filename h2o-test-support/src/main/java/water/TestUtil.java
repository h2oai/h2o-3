package water;

import Jama.Matrix;
import hex.CreateFrame;
import hex.Model;
import hex.SplitFrame;
import hex.genmodel.GenModel;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.easy.RowData;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import water.api.StreamingSchema;
import water.fvec.*;
import water.init.NetworkInit;
import water.junit.Priority;
import water.junit.rules.RulesPriorities;
import water.parser.BufferedString;
import water.parser.DefaultParserProviders;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.Timer;
import water.util.*;
import water.util.fp.Function;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import static org.junit.Assert.*;
import static water.util.ArrayUtils.gaussianVector;

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
  /**
   * Minimal cloud size to start test.
   */
  public static int MINCLOUDSIZE = Integer.parseInt(System.getProperty("cloudSize", "1"));
  /**
   * Default time in ms to wait for clouding
   */
  protected static int DEFAULT_TIME_FOR_CLOUDING = 60000 /* ms */;

  public TestUtil() {
    this(1);
  }

  public TestUtil(int minCloudSize) {
    MINCLOUDSIZE = Math.max(MINCLOUDSIZE, minCloudSize);
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

  /**
   * Take a double array and return it as a single array.  It will take each row on top of each other
   *
   * @param arr
   * @return
   */
  public static double[] changeDouble2SingleArray(double[][] arr) {
    double[] result = new double[arr.length * arr[0].length];
    int numRows = arr.length;
    int offset = 0;
    for (int rind = 0; rind < numRows; rind++) {
      int rowLength = arr[rind].length;
      System.arraycopy(arr[rind], 0, result, offset, rowLength);
      offset += rowLength;
    }
    return result;
  }

  public static void stall_till_cloudsize(int x, int timeout) {
    stall_till_cloudsize(new String[]{}, x, timeout);
  }

  public static void stall_till_cloudsize(String[] args, int x) {
    stall_till_cloudsize(args, x, getDefaultTimeForClouding());
  }

  public static void stall_till_cloudsize(String[] args, int x, int timeout) {
    x = Math.max(MINCLOUDSIZE, x);
    if (!_stall_called_before) {
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

  @AfterClass
  public static void checkLeakedKeys() {
    int leaked_keys = H2O.store_size() - _initial_keycnt;
    int cnt = 0;
    if (leaked_keys > 0) {
      int print_max = 10;
      for (Key k : H2O.localKeySet()) {
        Value value = Value.STORE_get(k);
        // Ok to leak VectorGroups and the Jobs list
        if (value == null || value.isVecGroup() || value.isESPCGroup() || k == Job.LIST ||
                // Also leave around all attempted Jobs for the Jobs list
                (value.isJob() && value.<Job>get().isStopped())) {
          leaked_keys--;
        } else {
          System.out.println(k + " -> " + (value.type() != TypeMap.PRIM_B ? value.get() : "byte[]"));
          if (cnt++ < print_max)
            System.err.println("Leaked key: " + k + " = " + TypeMap.className(value.type()));
        }
      }
      if (print_max < leaked_keys) System.err.println("... and " + (leaked_keys - print_max) + " more leaked keys");
    }
    assertTrue("Keys leaked: " + leaked_keys + ", cnt = " + cnt, leaked_keys <= 0 || cnt == 0);
    // Bulk brainless key removal.  Completely wipes all Keys without regard.
    new DKVCleaner().doAllNodes();
    _initial_keycnt = H2O.store_size();
  }

  private static class KeyCleaner extends MRTask<KeyCleaner> {
    private final Class[] objectType;

    private KeyCleaner(Class[] objectType) {
      this.objectType = objectType;
    }

    @Override
    protected void setupLocal() {
      Futures fs = new Futures();
      for (Key k : H2O.localKeySet()) {
        Value value = Value.STORE_get(k);
        if (value == null || value.isVecGroup() || value.isESPCGroup() || k == Job.LIST ||
                value.isJob() || value.type() == TypeMap.PRIM_B
        ) {
          // do nothing
        } else {
          for (Class c : objectType) {
            if (c.isInstance(value.get())) {
              DKV.remove(k, fs);
              break;
            }
          }
        }
      }
      fs.blockForPending();
    }
  }

  public static void cleanupKeys(Class... objectType) {
    new KeyCleaner(objectType).doAllNodes();
  }

  public static double[][] genRandomMatrix(int row, int col, long seedValue) {
    double[][] randomMat = new double[row][];
    Random random = new Random(seedValue);
    for (int rInd = 0; rInd < row; rInd++)
      randomMat[rInd] = gaussianVector(col, random);
    return randomMat;
  }

  public static double[][] genSymPsdMatrix(int matSize, long seedValue, int multiplier) {
    double[][] mat = genRandomMatrix(matSize, matSize, seedValue);
    // generate symmetric matrix
    Matrix matT = new Matrix(mat);
    Matrix symMat = matT.plus(matT.transpose()).times(0.5);
    for (int index=0; index<matSize; index++) {
      symMat.set(index, index, Math.abs(genRandomMatrix(1,1,123)[0][0])*multiplier);
    }
    return symMat.getArray();
  }

  public static double[] genRandomArray(int length, long seedValue) {
    Random random = new Random(seedValue);
    return gaussianVector(length, random);
  }

  public static void checkArrays(double[] expected, double[] actual, double threshold) {
    for (int i = 0; i < actual.length; i++) {
      if (!Double.isNaN(expected[i]) && !Double.isNaN(actual[i])) // only compare when both are not NaN
        assertEquals(expected[i], actual[i], threshold * Math.min(Math.abs(expected[i]), Math.abs(actual[i])));
    }
  }

  public static void checkDoubleArrays(double[][] expected, double[][] actual, double threshold) {
    int len1 = expected.length;
    assertEquals(len1, actual.length);

    for (int ind = 0; ind < len1; ind++) {
      assertEquals(expected[ind].length, actual[ind].length);
      checkArrays(expected[ind], actual[ind], threshold);
    }
  }
  
  public static void check3DArrays(double[][][] expected, double[][][] actual, double threshold) {
    int len = expected.length;
    assertEquals(len, actual.length);
    for (int ind=0; ind < len; ind++) {
      checkDoubleArrays(expected[ind], actual[ind], threshold);
    }
  }
  
  public static void checkIntArrays(int[][] expected, int[][] actual) {
    int len1 = expected.length;
    assertEquals(len1, actual.length);

    for (int ind = 0; ind < len1; ind++) {
      assertEquals(expected[ind].length, actual[ind].length);
      Arrays.equals(expected[ind], actual[ind]);
    }
  }
  

  /**
   * @deprecated use {@link #generateEnumOnly(int, int, int, double)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected static Frame generate_enum_only(int numCols, int numRows, int num_factor, double missingfrac) {
    return generateEnumOnly(numCols, numRows, num_factor, missingfrac);
  }

  /**
   * generate random frames containing enum columns only
   *
   * @param numCols
   * @param numRows
   * @param num_factor
   * @return
   */
  protected static Frame generateEnumOnly(int numCols, int numRows, int num_factor, double missingfrac) {
    long seed = System.currentTimeMillis();
    System.out.println("Createframe parameters: rows: " + numRows + " cols:" + numCols + " seed: " + seed);
    return generateEnumOnly(numCols, numRows, num_factor, missingfrac, seed);
  }

  /**
   * @deprecated use {@link #generateEnumOnly(int, int, int, double, long)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected static Frame generate_enum_only(int numCols, int numRows, int num_factor, double missingfrac, long seed) {
    return generateEnumOnly(numCols, numRows, num_factor, missingfrac, seed);
  }

  public static Frame generateEnumOnly(int numCols, int numRows, int num_factor, double missingfrac, long seed) {
    CreateFrame cf = new CreateFrame();
    cf.rows = numRows;
    cf.cols = numCols;
    cf.factors = num_factor;
    cf.binary_fraction = 0;
    cf.integer_fraction = 0;
    cf.categorical_fraction = 1;
    cf.has_response = false;
    cf.missing_fraction = missingfrac;
    cf.seed = seed;
    System.out.println("Createframe parameters: rows: " + numRows + " cols:" + numCols + " seed: " + cf.seed);
    return cf.execImpl().get();
  }

  /**
   * @deprecated use {@link #generateRealOnly(int, int, double)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected static Frame generate_real_only(int numCols, int numRows, double missingfrac) {
    return generateRealOnly(numCols, numRows, missingfrac);
  }

  protected static Frame generateRealOnly(int numCols, int numRows, double missingfrac) {
    long seed = System.currentTimeMillis();
    System.out.println("Createframe parameters: rows: " + numRows + " cols:" + numCols + " seed: " + seed);
    return generateRealOnly(numCols, numRows, missingfrac, seed);
  }

  /**
   * @deprecated use {@link #generateRealOnly(int, int, double, long)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected static Frame generate_real_only(int numCols, int numRows, double missingfrac, long seed) {
    return generateRealOnly(numCols, numRows, missingfrac, seed);
  }

  protected static Frame generateRealOnly(int numCols, int numRows, double missingfrac, long seed) {
     return generateRealWithRangeOnly(numCols, numRows, missingfrac, seed, 100);
  }

  protected static Frame generateRealWithRangeOnly(int numCols, int numRows, double missingfrac, long seed, long range) {
    CreateFrame cf = new CreateFrame();
    cf.rows = numRows;
    cf.cols = numCols;
    cf.binary_fraction = 0;
    cf.integer_fraction = 0;
    cf.categorical_fraction = 0;
    cf.time_fraction = 0;
    cf.string_fraction = 0;
    cf.has_response = false;
    cf.missing_fraction = missingfrac;
    cf.real_range = range;
    cf.seed = seed;
    System.out.println("Createframe parameters: rows: " + numRows + " cols:" + numCols + " seed: " + cf.seed + 
            " range: "+range);
    return cf.execImpl().get();
  }

  /**
   * @deprecated use {@link #generateIntOnly(int, int, int, double)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected static Frame generate_int_only(int numCols, int numRows, int integer_range, double missingfrac) {
    return generateIntOnly(numCols, numRows, integer_range, missingfrac);
  }

  protected static Frame generateIntOnly(int numCols, int numRows, int integer_range, double missingfrac) {
    long seed = System.currentTimeMillis();
    System.out.println("Createframe parameters: rows: " + numRows + " cols:" + numCols + " seed: " + seed);
    return generateIntOnly(numCols, numRows, integer_range, missingfrac, seed);
  }

  /**
   * @deprecated use {@link #generateIntOnly(int, int, int, double, long)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected static Frame generate_int_only(int numCols, int numRows, int integer_range, double missingfrac, long seed) {
    return generateIntOnly(numCols, numRows, integer_range, missingfrac, seed);
  }

  protected static Frame generateIntOnly(int numCols, int numRows, int integerRange, double missingfrac, long seed) {
    CreateFrame cf = new CreateFrame();
    cf.rows = numRows;
    cf.cols = numCols;
    cf.binary_fraction = 0;
    cf.integer_fraction = 1;
    cf.categorical_fraction = 0;
    cf.time_fraction = 0;
    cf.string_fraction = 0;
    cf.has_response = false;
    cf.missing_fraction = missingfrac;
    cf.integer_range = integerRange;
    cf.seed = seed;
    System.out.println("Createframe parameters: rows: " + numRows + " cols:" + numCols + " seed: " + cf.seed);
    return cf.execImpl().get();
  }

  protected static int[] rangeFun(int numEle, int offset) {
    int[] ranges = new int[numEle];

    for (int index = 0; index < numEle; index++) {
      ranges[index] = index + offset;
    }
    return ranges;
  }

  protected static int[] sortDir(int numEle, Random rand) {
    int[] sortDir = new int[numEle];
    int[] dirs = new int[]{-1, 1};

    for (int index = 0; index < numEle; index++) {
      sortDir[index] = dirs[rand.nextInt(2)];
    }
    return sortDir;
  }

  public static class DKVCleaner extends MRTask<DKVCleaner> {
    @Override
    public void setupLocal() {
      H2O.raw_clear();
      water.fvec.Vec.ESPC.clear();
    }
  }

  // current running test - assumes no test parallelism just like the rest of this class
  public static Description CURRENT_TEST_DESCRIPTION;

  /**
   * Execute this rule before each test to print test name and test class
   */
  @Rule
  transient public TestRule logRule = new TestRule() {

    @Override
    public Statement apply(Statement base, Description description) {
      Log.info("###########################################################");
      Log.info("  * Test class name:  " + description.getClassName());
      Log.info("  * Test method name: " + description.getMethodName());
      Log.info("###########################################################");
      CURRENT_TEST_DESCRIPTION = description;
      return base;
    }
  };

  /* Ignore tests specified in the ignore.tests system property: applied last, if test is ignored, no other rule with be evaluated */
  @Rule
  transient public TestRule runRule = new @Priority(RulesPriorities.RUN_TEST) TestRule() {
    @Override
    public Statement apply(Statement base, Description description) {
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
          public void evaluate() throws Throwable {
          }
        };
      } else {
        return base;
      }
    }
  };

  @Rule
  transient public TestRule timerRule = new TestRule() {
    @Override
    public Statement apply(Statement base, Description description) {
      return new TimerStatement(base, description.getClassName() + "#" + description.getMethodName());
    }

    class TimerStatement extends Statement {
      private final Statement _base;
      private final String _tname;

      public TimerStatement(Statement base, String tname) {
        _base = base;
        _tname = tname;
      }

      @Override
      public void evaluate() throws Throwable {
        Timer t = new Timer();
        try {
          _base.evaluate();
        } finally {
          Log.info("#### TEST " + _tname + " EXECUTION TIME: " + t);
        }
      }
    }
  };

  // ==== Data Frame Creation Utilities ====

  /**
   * Compare 2 frames
   *
   * @param fr1     Frame
   * @param fr2     Frame
   * @param epsilon Relative tolerance for floating point numbers
   */
  public static void assertIdenticalUpToRelTolerance(Frame fr1, Frame fr2, double epsilon) {
    assertIdenticalUpToRelTolerance(fr1, fr2, epsilon, true, "");
  }

  public static void assertIdenticalUpToRelTolerance(Frame fr1, Frame fr2, double epsilon, String messagePrefix) {
    assertIdenticalUpToRelTolerance(fr1, fr2, epsilon, true, messagePrefix);
  }

  public static void assertIdenticalUpToRelTolerance(Frame fr1, Frame fr2, double epsilon, boolean expected) {
    assertIdenticalUpToRelTolerance(fr1, fr2, epsilon, expected, "");
  }

  public static void assertIdenticalUpToRelTolerance(Frame fr1, Frame fr2, double epsilon, boolean expected, String messagePrefix) {
    if (fr1 == fr2) return;
    if (expected) {
      assertEquals("Number of columns differ.", fr1.numCols(), fr2.numCols());
      assertEquals("Number of rows differ.", fr1.numRows(), fr2.numRows());
    } else if (fr1.numCols() != fr2.numCols() || fr1.numRows() != fr2.numRows()) {
      return;
    }
    Scope.enter();
    if (!fr1.isCompatible(fr2)) fr1.makeCompatible(fr2);
    Cmp1 cmp = new Cmp1(epsilon, messagePrefix).doAll(new Frame(fr1).add(fr2));
    Scope.exit();
    assertTrue(cmp._message, expected == !cmp._unequal);
  }

  /**
   * Compare 2 frames
   *
   * @param fr1 Frame
   * @param fr2 Frame
   */
  public static void assertBitIdentical(Frame fr1, Frame fr2) {
    assertIdenticalUpToRelTolerance(fr1, fr2, 0);
  }

  static File[] contentsOf(String name, File folder) {
    try {
      return FileUtils.contentsOf(folder, name);
    } catch (IOException ioe) {
      fail(ioe.getMessage());
      return null;
    }
  }

  /**
   * @deprecated use {@link #parseTestFile(String)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  public static Frame parse_test_file(String fname) {
    return parseTestFile(fname);
  }

  /**
   * @deprecated use {@link #parseTestFile(String, int[])} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  public static Frame parse_test_file(String fname, int[] skipped_columns) {
    return parseTestFile(fname, skipped_columns);
  }

  /**
   * Find & parse a CSV file.  NPE if file not found.
   *
   * @param fname Test filename
   * @return Frame or NPE
   */
  public static Frame parseTestFile(String fname) {
    return parseTestFile(Key.make(), fname);
  }

  public static Frame parseTestFile(String fname, int[] skipped_columns) {
    return parseTestFile(Key.make(), fname, skipped_columns);
  }

  /**
   * Find & parse & track in {@link Scope} a CSV file.  NPE if file not found.
   *
   * @param fname Test filename
   * @return Frame or NPE
   */
  public static Frame parseAndTrackTestFile(String fname) {
    return Scope.track(parseTestFile(Key.make(), fname));
  }

  /**
   * Make sure the given frame is distributed in a way that MRTask reduce operation is called
   * and spans at least 2 nodes of the cluster (if running on multinode).
   * <p>
   * If a new frame is created - it is automatically tracked in Scope if it is currently active.
   *
   * @param frame input frame
   * @return possibly new Frame rebalanced to a minimum number of chunks
   */
  public static Frame ensureDistributed(Frame frame) {
    int minChunks = H2O.getCloudSize() * 4; // at least one node will have 4 chunks (MR tree will have at least 2 levels)
    return ensureDistributed(frame, minChunks);
  }

  /**
   * Make sure the given frame is distributed at least to given minimum number of chunks
   * and spans at least 2 nodes of the cluster (if running on multinode).
   * <p>
   * If a new frame is created - it is automatically tracked in Scope if it is currently active.
   *
   * @param frame     input frame
   * @param minChunks minimum required number of chunks
   * @return possibly new Frame rebalanced to a minimum number of chunks
   */
  public static Frame ensureDistributed(Frame frame, int minChunks) {
    if (frame.anyVec().nChunks() < minChunks) {
      // rebalance first
      Key<Frame> k = Key.make();
      H2O.submitTask(new RebalanceDataSet(frame, k, minChunks)).join();
      frame = trackIfScopeActive(k.get());
    }
    // check frame spans 2+ nodes
    if (H2O.CLOUD.size() > 1) {
      Vec v = frame.anyVec();
      H2ONode node = null;
      for (int i = 0; i < v.nChunks(); i++) {
        H2ONode cNode = v.chunkKey(i).home_node();
        if (v.chunkLen(i) == 0)
          continue;
        if (node == null)
          node = cNode;
        else if (cNode != node) // found proof
          return frame;
      }
      throw new IllegalStateException("Frame is only stored on a sigle node");
    }
    return frame;
  }

  static Frame trackIfScopeActive(Frame frame) {
    if (Scope.isActive()) {
      // this function can only be called in tests - it is thus safe to auto-track the frame if the test created a Scope
      Scope.track(frame);
    }
    return frame;
  }

  public static void assertExists(String fname) {
    NFSFileVec v = makeNfsFileVec(fname);
    assertNotNull("File '" + fname + "' was not found", v);
    v.remove();
  }
  
  public static NFSFileVec makeNfsFileVec(String fname) {
    try {
      File file = FileUtils.locateFile(fname);
      if ((file == null) && (isCI() || runWithoutLocalFiles())) {
        long lastModified = downloadTestFileFromS3(fname);
        if (lastModified != 0 && isCI()) { // in CI fail if the file is missing for more than 30 days
          if (System.currentTimeMillis() - lastModified > 30 * 24 * 60 * 60 * 1000L) {
            throw new IllegalStateException(
                    "File '" + fname + "' is still not locally synchronized (more than 30 days). Talk to #devops-requests");
          }
        }
      }
      return NFSFileVec.make(fname);
    } catch (IOException ioe) {
      Log.err(ioe);
      fail(ioe.getMessage());
      return null;
    }
  }

  private static boolean runWithoutLocalFiles() {
    return Boolean.parseBoolean(System.getenv("H2O_JUNIT_ALLOW_NO_SMALLDATA"));
  }

  private static File getLocalSmalldataFile(final String fname) {
    String projectDir = System.getenv("H2O_PROJECT_DIR");
    return projectDir != null ? new File(projectDir, fname) : new File(fname);
  }
  
  protected static long downloadTestFileFromS3(String fname) throws IOException {
    if (fname.startsWith("./"))
      fname = fname.substring(2);
    final File f = getLocalSmalldataFile(fname);
    if (!f.exists()) {
      if (f.getParentFile() != null) {
        boolean dirsCreated = f.getParentFile().mkdirs();
        if (! dirsCreated) {
          Log.warn("Failed to create directory:" + f.getParentFile());
        }
      }
      File tmpFile = File.createTempFile(f.getName(), "tmp", f.getParentFile());
      final URL source = new URL("https://h2o-public-test-data.s3.amazonaws.com/" + fname);
      final URLConnection connection = source.openConnection();
      connection.setConnectTimeout(1000);
      connection.setReadTimeout(2000);
      final long lastModified = connection.getLastModified();
      try (final InputStream stream = connection.getInputStream()) {
        org.apache.commons.io.FileUtils.copyInputStreamToFile(stream, tmpFile);
      }
      if (tmpFile.renameTo(f)) {
        return lastModified;
      } else {
        Log.warn("Couldn't download " + fname + " from S3.");
      }
    }
    return 0;
  }

  /**
   * @deprecated use {@link #parseTestFile(Key, String, boolean)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(Key outputKey, String fname, boolean guessSetup) {
    return parseTestFile(outputKey, fname, guessSetup);
  }

  protected Frame parseTestFile(Key outputKey, String fname, boolean guessSetup) {
    return parseTestFile(outputKey, fname, guessSetup, null);
  }

  /**
   * @deprecated use {@link #parseTestFile(Key, String, boolean, int[])} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(Key outputKey, String fname, boolean guessSetup, int[] skippedColumns) {
    return parseTestFile(outputKey, fname, guessSetup, skippedColumns);
  }

  protected Frame parseTestFile(Key outputKey, String fname, boolean guessSetup, int[] skippedColumns) {
    NFSFileVec nfs = makeNfsFileVec(fname);
    ParseSetup guessParseSetup = ParseSetup.guessSetup(new Key[]{nfs._key}, false, 1);
    if (skippedColumns != null) {
      guessParseSetup.setSkippedColumns(skippedColumns);
      guessParseSetup.setParseColumnIndices(guessParseSetup.getNumberColumns(), skippedColumns);
    }
    return ParseDataset.parse(outputKey, new Key[]{nfs._key}, true, ParseSetup.guessSetup(new Key[]{nfs._key}, false, 1));
  }

  /**
   * @deprecated use {@link #parseTestFile(Key, String)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(Key outputKey, String fname) {
    return parseTestFile(outputKey, fname);
  }

  public static Frame parseTestFile(Key outputKey, String fname) {
    return parseTestFile(outputKey, fname, new int[]{});
  }

  /**
   * @deprecated use {@link #parseTestFile(Key, String, int[])} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(Key outputKey, String fname, int[] skippedColumns) {
    return parseTestFile(outputKey, fname, skippedColumns);
  }

  public static Frame parseTestFile(Key outputKey, String fname, int[] skippedColumns) {
    return parseTestFile(outputKey, fname, null, skippedColumns);
  }

  /**
   * @deprecated use {@link #parseTestFile(String, ParseSetupTransformer)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(String fname, ParseSetupTransformer transformer) {
    return parseTestFile(fname, transformer);
  }

  public static Frame parseTestFile(String fname, ParseSetupTransformer transformer) {
    return parseTestFile(Key.make(), fname, transformer);
  }

  /**
   * @deprecated use {@link #parseTestFile(String, ParseSetupTransformer, int[])} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(String fname, ParseSetupTransformer transformer, int[] skippedColumns) {
    return parseTestFile(fname, transformer, skippedColumns);
  }

  public static Frame parseTestFile(String fname, ParseSetupTransformer transformer, int[] skippedColumns) {
    return parseTestFile(Key.make(), fname, transformer, skippedColumns);
  }

  /**
   * @deprecated use {@link #parseTestFile(Key, String, ParseSetupTransformer)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(Key outputKey, String fname, ParseSetupTransformer transformer) {
    return parseTestFile(outputKey, fname, transformer);
  }

  public static Frame parseTestFile(Key outputKey, String fname, ParseSetupTransformer transformer) {
    return parseTestFile(outputKey, fname, transformer, null);
  }

  /**
   * @deprecated use {@link #parseTestFile(Key outputKey, String fname, ParseSetupTransformer transformer, int[] skippedColumns)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(Key outputKey, String fname, ParseSetupTransformer transformer, int[] skippedColumns) {
    return parseTestFile(outputKey, fname, transformer, skippedColumns);
  }

  public static Frame parseTestFile(Key outputKey, String fname, ParseSetupTransformer transformer, int[] skippedColumns) {
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

  public static Frame parseTestFile(Key outputKey, String fname, ParseSetupTransformer transformer, 
                                    int[] skippedColumns, int psetup) {
    NFSFileVec nfs = makeNfsFileVec(fname);
    ParseSetup guessedSetup = ParseSetup.guessSetup(new Key[]{nfs._key}, false, psetup);
    if (skippedColumns != null) {
      guessedSetup.setSkippedColumns(skippedColumns);
      guessedSetup.setParseColumnIndices(guessedSetup.getNumberColumns(), skippedColumns);
    }

    if (transformer != null)
      guessedSetup = transformer.transformSetup(guessedSetup);
    return ParseDataset.parse(outputKey, new Key[]{nfs._key}, true, guessedSetup);
  }

  /**
   * @deprecated use {@link #parseTestFile(String fname, String na_string, int check_header, byte[] column_types)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(String fname, String na_string, int check_header, byte[] column_types) {
    return parseTestFile(fname, na_string, check_header, column_types);
  }

  public static Frame parseTestFile(String fname, String na_string, int check_header, byte[] column_types) {
    return parseTestFile(fname, na_string, check_header, column_types, null, null);
  }


  /**
   * @deprecated use {@link #parseTestFile(String fname, String na_string, int check_header, byte[] column_types, ParseSetupTransformer transformer)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(String fname, String na_string, int check_header, byte[] column_types, ParseSetupTransformer transformer) {
    return parseTestFile(fname, na_string, check_header, column_types, transformer);
  }

  public static Frame parseTestFile(String fname, String na_string, int check_header, byte[] column_types, ParseSetupTransformer transformer) {
    return parseTestFile(fname, na_string, check_header, column_types, transformer, null);
  }


  /**
   * @deprecated use {@link #parseTestFile(String fname, String na_string, int check_header, byte[] column_types, ParseSetupTransformer transformer, int[] skippedColumns)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_file(String fname, String na_string, int check_header, byte[] column_types, ParseSetupTransformer transformer, int[] skippedColumns) {
    return parseTestFile(fname, na_string, check_header, column_types, transformer, skippedColumns);
  }

  public static Frame parseTestFile(String fname, String na_string, int check_header, byte[] column_types, ParseSetupTransformer transformer, int[] skippedColumns) {
    NFSFileVec nfs = makeNfsFileVec(fname);

    Key[] res = {nfs._key};

    // create new parseSetup in order to store our na_string
    ParseSetup p = ParseSetup.guessSetup(res, new ParseSetup(DefaultParserProviders.GUESS_INFO, (byte) ',', false,
            check_header, 0, null, null, null, null, null, null, null, false));
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

  public static Frame parseTestFile(String fname, String na_string, int check_header, byte[] column_types, 
                                    ParseSetupTransformer transformer, int[] skippedColumns, boolean force_col_types) {
    NFSFileVec nfs = makeNfsFileVec(fname);

    Key[] res = {nfs._key};

    // create new parseSetup in order to store our na_string
    ParseSetup p = ParseSetup.guessSetup(res, new ParseSetup(DefaultParserProviders.GUESS_INFO, (byte) ',', false,
            check_header, 0, null, null, null, null, null, null, null, false));
    if (skippedColumns != null) {
      p.setSkippedColumns(skippedColumns);
      p.setParseColumnIndices(p.getNumberColumns(), skippedColumns);
    }
    
    if (force_col_types)  // only useful for parquet parsers here
      p.setForceColTypes(true);

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


  /**
   * @deprecated use {@link #parseTestFolder(String)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_folder(String fname) {
    return parseTestFolder(fname);
  }

  /**
   * Find & parse a folder of CSV files.  NPE if file not found.
   *
   * @param fname Test filename
   * @return Frame or NPE
   */
  protected Frame parseTestFolder(String fname) {
    return parseTestFolder(fname, null);
  }

  /**
   * @deprecated use {@link #parseTestFolder(String, int[])} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_folder(String fname, int[] skippedColumns) {
    return parseTestFolder(fname, skippedColumns);
  }

  /**
   * Find & parse a folder of CSV files.  NPE if file not found.
   *
   * @param fname Test filename
   * @return Frame or NPE
   */
  protected Frame parseTestFolder(String fname, int[] skippedColumns) {
    File folder = FileUtils.locateFile(fname);
    File[] files = contentsOf(fname, folder);
    Arrays.sort(files);
    ArrayList<Key> keys = new ArrayList<>();
    for (File f : files)
      if (f.isFile())
        keys.add(NFSFileVec.make(f)._key);
    Key[] res = new Key[keys.size()];
    keys.toArray(res);
    return ParseDataset.parse(skippedColumns, Key.make(), res);
  }


  /**
   * @deprecated use {@link #parseTestFolder(String, String, int, byte[], ParseSetupTransformer)} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_folder(String fname, String na_string, int check_header, byte[] column_types,
                                    ParseSetupTransformer transformer) {
    return parseTestFolder(fname, na_string, check_header, column_types, transformer);
  }

  /**
   * Parse a folder with csv files when a single na_string is specified.
   *
   * @param fname     name of folder
   * @param na_string string for NA in a column
   * @return
   */
  protected static Frame parseTestFolder(String fname, String na_string, int check_header, byte[] column_types,
                                         ParseSetupTransformer transformer) {
    return parseTestFolder(fname, na_string, check_header, column_types, transformer, null);
  }

  /**
   * @deprecated use {@link #parseTestFolder(String, String, int, byte[], ParseSetupTransformer, int[])} instead
   * <p>
   * Will be removed at version 3.38.0.1
   */
  @Deprecated
  protected Frame parse_test_folder(String fname, String na_string, int check_header, byte[] column_types,
                                    ParseSetupTransformer transformer, int[] skipped_columns) {
    return parseTestFolder(fname, na_string, check_header, column_types, transformer, skipped_columns);
  }

  /**
   * Parse a folder with csv files when a single na_string is specified.
   *
   * @param fname     name of folder
   * @param na_string string for NA in a column
   * @return
   */
  protected static Frame parseTestFolder(String fname, String na_string, int check_header, byte[] column_types,
                                         ParseSetupTransformer transformer, int[] skipped_columns) {
    File folder = FileUtils.locateFile(fname);
    File[] files = contentsOf(fname, folder);
    Arrays.sort(files);
    ArrayList<Key> keys = new ArrayList<>();
    for (File f : files)
      if (f.isFile())
        keys.add(NFSFileVec.make(f)._key);

    Key[] res = new Key[keys.size()];
    keys.toArray(res);  // generated the necessary key here

    // create new parseSetup in order to store our na_string
    ParseSetup p = ParseSetup.guessSetup(res, new ParseSetup(DefaultParserProviders.GUESS_INFO, (byte) ',', true,
            check_header, 0, null, null, null, null, null, null, null, false));
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

  public static class Frames {
    public final Frame train;
    public final Frame test;
    public final Frame valid;

    public Frames(Frame train, Frame test, Frame valid) {
      this.train = train;
      this.test = test;
      this.valid = valid;
    }
  }

  public static Frames split(Frame f) {
    return split(f, 0.9, 0d);
  }

  public static Frames split(Frame f, double testFraction) {
    return split(f, testFraction, 0);
  }

  public static Frames split(Frame f, double testFraction, double validFraction) {
    double[] fractions;
    double trainFraction = 1d - testFraction - validFraction;
    if (validFraction > 0d) {
      fractions = new double[]{trainFraction, testFraction, validFraction};
    } else {
      fractions = new double[]{trainFraction, testFraction};
    }
    SplitFrame sf = new SplitFrame(f, fractions, null);
    sf.exec().get();
    Key<Frame>[] splitKeys = sf._destination_frames;
    Frame trainFrame = Scope.track(splitKeys[0].get());
    Frame testFrame = Scope.track(splitKeys[1].get());
    Frame validFrame = (validFraction > 0d) ? Scope.track(splitKeys[2].get()) : null;
    return new Frames(trainFrame, testFrame, validFrame);
  }

  /**
   * A Numeric Vec from an array of ints
   *
   * @param rows Data
   * @return The Vec
   */
  public static Vec vec(int... rows) {
    return vec(null, rows);
  }

  /**
   * A Categorical/Factor Vec from an array of ints - with categorical/domain mapping
   *
   * @param domain Categorical/Factor names, mapped by the data values
   * @param rows   Data
   * @return The Vec
   */
  public static Vec vec(String[] domain, int... rows) {
    Key<Vec> k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k, Vec.T_NUM);
    avec.setDomain(domain);
    NewChunk chunk = new NewChunk(avec, 0);
    for (int r : rows) chunk.addNum(r);
    chunk.close(0, fs);
    Vec vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  /**
   * A numeric Vec from an array of ints
   */
  public static Vec ivec(int... rows) {
    return vec(null, rows);
  }

  /**
   * A categorical Vec from an array of strings
   */
  public static Vec cvec(String... rows) {
    return cvec(null, rows);
  }

  public static Vec cvec(String[] domain, String... rows) {
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

  /**
   * A numeric Vec from an array of doubles
   */
  public static Vec dvec(double... rows) {
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

  /**
   * A time Vec from an array of ints
   */
  public static Vec tvec(int... rows) {
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

  /**
   * A string Vec from an array of strings
   */
  public static Vec svec(String... rows) {
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

  /**
   * A string Vec from an array of strings
   */
  public static Vec uvec(UUID... rows) {
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
  public static String[] ar(String... a) {
    return a;
  }

  public static String[][] ar(String[]... a) {
    return a;
  }

  public static byte[] ar(byte... a) {
    return a;
  }

  public static long[] ar(long... a) {
    return a;
  }

  public static long[][] ar(long[]... a) {
    return a;
  }

  public static int[] ari(int... a) {
    return a;
  }

  public static int[][] ar(int[]... a) {
    return a;
  }

  public static float[] arf(float... a) {
    return a;
  }

  public static double[] ard(double... a) {
    return a;
  }

  public static double[][] ard(double[]... a) {
    return a;
  }

  public static double[][] ear(double... a) {
    double[][] r = new double[a.length][1];
    for (int i = 0; i < a.length; i++) r[i][0] = a[i];
    return r;
  }

  // Java7+  @SafeVarargs
  public static <T> T[] aro(T... a) {
    return a;
  }

  // ==== Comparing Results ====

  public static void assertFrameEquals(Frame expected, Frame actual, double absDelta) {
    assertFrameEquals(expected, actual, absDelta, null);
  }

  public static void assertFrameEquals(Frame expected, Frame actual, Double absDelta, Double relativeDelta) {
    assertEquals("Frames have different number of vecs. ", expected.vecs().length, actual.vecs().length);
    for (int i = 0; i < expected.vecs().length; i++) {
      if (expected.vec(i).isString())
        assertStringVecEquals(expected.vec(i), actual.vec(i));
      else
        assertVecEquals(i + "/" + expected._names[i] + " ", expected.vec(i), actual.vec(i), absDelta, relativeDelta);
    }
  }

  public static void assertVecEquals(Vec expecteds, Vec actuals, double delta) {
    assertVecEquals("", expecteds, actuals, delta);
  }

  public static void assertVecEquals(Vec expecteds, Vec actuals, double delta, double relativeDelta) {
    assertVecEquals("", expecteds, actuals, delta, relativeDelta);
  }

  public static void assertVecEquals(String messagePrefix, Vec expecteds, Vec actuals, double delta) {
    assertVecEquals(messagePrefix, expecteds, actuals, delta, null);
  }

  public static void assertVecEquals(String messagePrefix, Vec expecteds, Vec actuals, Double absDelta, Double relativeDelta) {
    assertEquals(expecteds.length(), actuals.length());
    for (int i = 0; i < expecteds.length(); i++) {
      final String message = messagePrefix + i + ": " + expecteds.at(i) + " != " + actuals.at(i) + ", chunkIds = " + expecteds.elem2ChunkIdx(i) + ", " + actuals.elem2ChunkIdx(i) + ", row in chunks = " + (i - expecteds.chunkForRow(i).start()) + ", " + (i - actuals.chunkForRow(i).start());
      double expectedVal = expecteds.at(i);
      double actualVal = actuals.at(i);
      assertEquals(message, expectedVal, actualVal, computeAssertionDelta(expectedVal, absDelta, relativeDelta));
    }
  }

  private static double computeAssertionDelta(double expectedVal, Double absDelta, Double relDelta) {
    if ((absDelta == null || absDelta.isNaN()) && (relDelta == null || relDelta.isNaN())) {
      throw new IllegalArgumentException("Either absolute or relative delta has to be non-null and non-NaN");
    } else if (relDelta == null || relDelta.isNaN()) {
      return absDelta;
    } else {
      double computedRelativeDelta;
      double deltaBase = Math.abs(expectedVal);
      if (deltaBase == 0) {
        computedRelativeDelta = relDelta;
      } else {
        computedRelativeDelta = deltaBase * relDelta;
      }
      if (absDelta == null || absDelta.isNaN()) {
        return computedRelativeDelta;
      } else {
        // use the bigger delta for the assert
        return Math.max(computedRelativeDelta, absDelta);
      }
    }
  }

  public static void assertUUIDVecEquals(Vec expecteds, Vec actuals) {
    assertEquals(expecteds.length(), actuals.length());
    assertEquals("Vec types match", expecteds.get_type_str(), actuals.get_type_str());
    for (int i = 0; i < expecteds.length(); i++) {
      UUID expected = new UUID(expecteds.at16l(i), expecteds.at16h(i));
      UUID actual = new UUID(actuals.at16l(i), actuals.at16h(i));
      final String message = i + ": " + expected + " != " + actual + ", chunkIds = " + expecteds.elem2ChunkIdx(i) + ", " + actuals.elem2ChunkIdx(i) + ", row in chunks = " + (i - expecteds.chunkForRow(i).start()) + ", " + (i - actuals.chunkForRow(i).start());
      assertEquals(message, expected, actual);
    }
  }

  private static String toStr(BufferedString bs) {
    return bs != null ? bs.toString() : null;
  }

  public static void assertStringVecEquals(Vec expecteds, Vec actuals) {
    assertEquals(expecteds.length(), actuals.length());
    assertEquals("Vec types match", expecteds.get_type_str(), actuals.get_type_str());
    for (int i = 0; i < expecteds.length(); i++) {
      String expected = toStr(expecteds.atStr(new BufferedString(), i));
      String actual = toStr(actuals.atStr(new BufferedString(), i));
      final String message = i + ": " + expected + " != " + actual + ", chunkIds = " + expecteds.elem2ChunkIdx(i) + ", " + actuals.elem2ChunkIdx(i) + ", row in chunks = " + (i - expecteds.chunkForRow(i).start()) + ", " + (i - actuals.chunkForRow(i).start());
      assertEquals(message, expected, actual);
    }
  }

  private static String getFactorAsString(Vec v, long row) {
    return v.isNA(row) ? null : v.factor((long) v.at(row));
  }

  public static void assertCatVecEquals(Vec expecteds, Vec actuals) {
    assertEquals(expecteds.length(), actuals.length());
    assertEquals("Vec types match", expecteds.get_type_str(), actuals.get_type_str());
    for (int i = 0; i < expecteds.length(); i++) {
      String expected = getFactorAsString(expecteds, i);
      String actual = getFactorAsString(actuals, i);
      final String message = i + ": " + expected + " != " + actual + ", chunkIds = " + expecteds.elem2ChunkIdx(i) + ", " + actuals.elem2ChunkIdx(i) + ", row in chunks = " + (i - expecteds.chunkForRow(i).start()) + ", " + (i - actuals.chunkForRow(i).start());
      assertEquals(message, expected, actual);
    }
  }

  public static void assertTwoDimTableEquals(TwoDimTable expected, TwoDimTable actual) {
    assertEquals("tableHeader different", expected.getTableHeader(), actual.getTableHeader());
    assertEquals("tableDescriptionDifferent", expected.getTableDescription(), actual.getTableDescription());
    assertArrayEquals("rowHeaders different", expected.getRowHeaders(), actual.getRowHeaders());
    assertArrayEquals("colHeaders different", expected.getColHeaders(), actual.getColHeaders());
    assertArrayEquals("colTypes different", expected.getColTypes(), actual.getColTypes());
    assertArrayEquals("colFormats different", expected.getColFormats(), actual.getColFormats());
    assertEquals("colHeaderForRowHeaders different", expected.getColHeaderForRowHeaders(), actual.getColHeaderForRowHeaders());
    for (int r = 0; r < expected.getRowDim(); r++) {
      for (int c = 0; c < expected.getColDim(); c++) {
        Object ex = expected.get(r, c);
        Object act = actual.get(r, c);
        assertEquals("cellValues different at row " + r + ", col " + c, ex, act);
      }
    }
  }

  public static void checkStddev(double[] expected, double[] actual, double threshold) {
    for (int i = 0; i < actual.length; i++)
      assertEquals(expected[i], actual[i], threshold);
  }

  public static void checkIcedArrays(IcedWrapper[][] expected, IcedWrapper[][] actual, double threshold) {
    for (int i = 0; i < actual.length; i++)
      for (int j = 0; j < actual[0].length; j++)
        assertEquals(expected[i][j].d, actual[i][j].d, threshold);
  }

  public static boolean[] checkEigvec(double[][] expected, double[][] actual, double threshold) {
    int nfeat = actual.length;
    int ncomp = actual[0].length;
    boolean[] flipped = new boolean[ncomp];

    for (int j = 0; j < ncomp; j++) {
      // flipped[j] = Math.abs(expected[0][j] - actual[0][j]) > threshold;
      flipped[j] = Math.abs(expected[0][j] - actual[0][j]) > Math.abs(expected[0][j] + actual[0][j]);
      for (int i = 0; i < nfeat; i++) {
        assertEquals(expected[i][j], flipped[j] ? -actual[i][j] : actual[i][j], threshold);
      }
    }
    return flipped;
  }

  public static boolean[] checkEigvec(double[][] expected, TwoDimTable actual, double threshold) {
    int nfeat = actual.getRowDim();
    int ncomp = actual.getColDim();
    boolean[] flipped = new boolean[ncomp];

    for (int j = 0; j < ncomp; j++) {
      flipped[j] = Math.abs(expected[0][j] - (double) actual.get(0, j)) > threshold;
      for (int i = 0; i < nfeat; i++) {
        assertEquals(expected[i][j], flipped[j] ? -(double) actual.get(i, j) : (double) actual.get(i, j), threshold);
      }
    }
    return flipped;
  }

  public static boolean equalTwoArrays(double[] array1, double[] array2, double tol) {
    assert array1.length == array2.length : "Arrays have different lengths";
    for (int index = 0; index < array1.length; index++) {
      double diff = Math.abs(array1[index] - array2[index])/Math.max(Math.abs(array1[index]), Math.abs(array2[index]));
      if (diff > tol)
        return false;
    }
    return true;
  }

  public static class StandardizeColumns extends MRTask<StandardizeColumns> {
    int[] _columns2Transform;
    double[] _colMeans;
    double[] _oneOStd;

    public StandardizeColumns(int[] cols, double[] colMeans, double[] oneOSigma,
                              Frame transF) {
      assert cols.length == colMeans.length;
      assert colMeans.length == oneOSigma.length;
      _columns2Transform = cols;
      _colMeans = colMeans;
      _oneOStd = oneOSigma;

      int numCols = transF.numCols();
      for (int cindex : cols) { // check to make sure columns are numerical
        assert transF.vec(cindex).isNumeric();
      }
    }

    @Override
    public void map(Chunk[] chks) {
      int chunkLen = chks[0].len();
      int colCount = 0;
      for (int cindex : _columns2Transform) {
        for (int rindex = 0; rindex < chunkLen; rindex++) {
          double temp = (chks[cindex].atd(rindex) - _colMeans[colCount]) * _oneOStd[colCount];
          chks[cindex].set(rindex, temp);
        }
        colCount += 1;
      }
    }
  }

  public static boolean equalTwoHashMaps(HashMap<String, Double> coeff1, HashMap<String, Double> coeff2, double tol) {
    assert coeff1.size() == coeff2.size() : "HashMap sizes are differenbt";
    for (String key : coeff1.keySet()) {
      if (Math.abs(coeff1.get(key) - coeff2.get(key)) > tol)
        return false;
    }
    return true;
  }

  public static boolean equalTwoDimTables(TwoDimTable tab1, TwoDimTable tab2, double tol) {
    boolean same = true;
    //compare colHeaders
    same = Arrays.equals(tab1.getColHeaders(), tab2.getColHeaders()) &&
            Arrays.equals(tab1.getColTypes(), tab2.getColTypes());
    String[] colTypes = tab2.getColTypes();
    IcedWrapper[][] cellValues1 = tab1.getCellValues();
    IcedWrapper[][] cellValues2 = tab2.getCellValues();

    same = same && cellValues1.length == cellValues2.length;
    if (!same)
      return false;

    // compare cell values
    for (int cindex = 0; cindex < cellValues1.length; cindex++) {
      same = same && cellValues1[cindex].length == cellValues2[cindex].length;
      if (!same)
        return false;
      for (int index = 0; index < cellValues1[cindex].length; index++) {
        if (colTypes[index].equals("double")) {
          same = same && Math.abs(Double.parseDouble(cellValues1[cindex][index].toString()) - Double.parseDouble(cellValues2[cindex][index].toString())) < tol;
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
    for (int j = 0; j < ncomp; j++) {
      for (int i = 0; i < nfeat; i++) {
        if (Math.abs((Double) expected.get(i, j)) > 0.0 && Math.abs((Double) actual.get(i, j)) > 0.0) { // only non zeros
          flipped[j] = !(Math.signum((Double) expected.get(i, j)) == Math.signum((Double) actual.get(i, j)));
          break;
        }
      }
    }

    for (int j = 0; j < ncomp; j++) {
      for (int i = 0; i < nfeat; i++) {
        assertEquals((double) expected.get(i, j), flipped[j] ? -(double) actual.get(i, j) : (double) actual.get(i, j), threshold);
      }
    }
    return flipped;
  }

  public static boolean[] checkProjection(Frame expected, Frame actual, double threshold, boolean[] flipped) {
    assertEquals("Number of columns", expected.numCols(), actual.numCols());
    assertEquals("Number of columns in flipped", expected.numCols(), flipped.length);
    int nfeat = (int) expected.numRows();
    int ncomp = expected.numCols();

    for (int j = 0; j < ncomp; j++) {
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
  public static void main(String[] args) {
    H2O.main(new String[0]);
    for (String arg : args) {
      try {
        System.out.println("=== Starting " + arg);
        Class<?> clz = Class.forName(arg);
        Method main = clz.getDeclaredMethod("main");
        main.invoke(null);
      } catch (InvocationTargetException ite) {
        Throwable e = ite.getCause();
        e.printStackTrace();
        try {
          Thread.sleep(100);
        } catch (Exception ignore) {
        }
      } catch (Exception e) {
        e.printStackTrace();
        try {
          Thread.sleep(100);
        } catch (Exception ignore) {
        }
      } finally {
        System.out.println("=== Stopping " + arg);
      }
    }
    try {
      Thread.sleep(100);
    } catch (Exception ignore) {
    }
    if (args.length != 0)
      UDPRebooted.T.shutdown.send(H2O.SELF);
  }

  protected static class Cmp1 extends MRTask<Cmp1> {
    final double _epsilon;
    final String _messagePrefix;

    public Cmp1(double epsilon) {
      _epsilon = epsilon;
      _messagePrefix = "";
    }

    public Cmp1(double epsilon, String msg) {
      _epsilon = epsilon;
      _messagePrefix = msg + " ";
    }

    public boolean _unequal;
    public String _message;

    @Override
    public void map(Chunk chks[]) {
      for (int cols = 0; cols < chks.length >> 1; cols++) {
        Chunk c0 = chks[cols];
        Chunk c1 = chks[cols + (chks.length >> 1)];
        for (int rows = 0; rows < chks[0]._len; rows++) {
          String msgBase = _messagePrefix + "At [" + rows + ", " + cols + "]: ";
          if (c0.isNA(rows) != c1.isNA(rows)) {
            _unequal = true;
            _message = msgBase + "c0.isNA " + c0.isNA(rows) + " != c1.isNA " + c1.isNA(rows);
            return;
          } else if (!(c0.isNA(rows) && c1.isNA(rows))) {
            if (c0 instanceof C16Chunk && c1 instanceof C16Chunk) {
              long lo0 = c0.at16l(rows), lo1 = c1.at16l(rows);
              long hi0 = c0.at16h(rows), hi1 = c1.at16h(rows);
              if (lo0 != lo1 || hi0 != hi1) {
                _unequal = true;
                _message = msgBase + " lo0 " + lo0 + " != lo1 " + lo1 + " || hi0 " + hi0 + " != hi1 " + hi1;
                return;
              }
            } else if (c0 instanceof CStrChunk && c1 instanceof CStrChunk) {
              BufferedString s0 = new BufferedString(), s1 = new BufferedString();
              c0.atStr(s0, rows);
              c1.atStr(s1, rows);
              if (s0.compareTo(s1) != 0) {
                _unequal = true;
                _message = msgBase + " s0 " + s0 + " != s1 " + s1;
                return;
              }
            } else if ((c0 instanceof C8Chunk) && (c1 instanceof C8Chunk)) {
              long d0 = c0.at8(rows), d1 = c1.at8(rows);
              if (d0 != d1) {
                _unequal = true;
                _message = msgBase + " d0 " + d0 + " != d1 " + d1;
                return;
              }
            } else {
              double d0 = c0.atd(rows), d1 = c1.atd(rows);
              double cmpValue = ((d0 == 0.0) || (d1 == 0.0)) ? 1.0 : Math.abs(d0) + Math.abs(d1);
              if (!(Math.abs(d0 - d1) <= cmpValue * _epsilon)) {
                _unequal = true;
                _message = msgBase + " d0 " + d0 + " != d1 " + d1;
                return;
              }
            }
          }
        }
      }
    }

    @Override
    public void reduce(Cmp1 cmp) {
      if (_unequal) return;
      if (cmp._unequal) {
        _unequal = true;
        _message = cmp._message;
      }
    }
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
      return parseTestFile(file);
    }

    public void done(Frame frame) {
    }

    public void check(Frame frame) {
    }

    public final int nrows() {
      return dim[1];
    }

    public final int ncols() {
      return dim[0];
    }
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
          return parseTestFolder(f.getCanonicalPath(), null, ParseSetup.HAS_HEADER, null, psTransformer);
        } else {
          return parseTestFile(f.getCanonicalPath(), psTransformer);
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
      return parseTestFile(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
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
   * @param frame
   * @param columnName column's name to be factorized
   * @return Frame with factorized column
   */
  public static Frame asFactor(Frame frame, String columnName) {
    Vec vec = frame.vec(columnName);
    frame.replace(frame.find(columnName), vec.toCategoricalVec());
    vec.remove();
    DKV.put(frame);
    return frame;
  }

  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
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

  protected static RowData toRowData(Frame fr, String[] columns, long row) {
    RowData rd = new RowData();
    for (String col : columns) {
      Vec v = fr.vec(col);
      if (!v.isNumeric() && !v.isCategorical()) {
        throw new UnsupportedOperationException("Unsupported column type for column '" + col + "': " + v.get_type_str());
      }
      if (!v.isNA(row)) {
        Object val;
        if (v.isCategorical()) {
          val = v.domain()[(int) v.at8(row)];
        } else {
          val = v.at(row);
        }
        rd.put(col, val);
      }
    }
    return rd;
  }

  protected static double[] toNumericRow(Frame fr, long row) {
    double[] result = new double[fr.numCols()];
    for (int i = 0; i < result.length; i++) {
      result[i] = fr.vec(i).at(row);
    }
    return result;
  }

  /**
   * Compares two frames. Two frames are equal if and only if they contain the same number of columns, rows,
   * and values at each cell (coordinate) are the same. Column names are ignored, as well as chunks sizes and all other
   * aspects besides those explicitly mentioned.
   *
   * @param f1    Frame to be compared, not null
   * @param f2    Frame to be compared, not null
   * @param delta absolute tolerance
   * @return True if frames are the same up to tolerance - number of columns, rows & values at each cell.
   * @throws AssertionError           If any inequalities are found
   * @throws IllegalArgumentException If input frames don't have the same column and row count
   */
  public static boolean compareFrames(final Frame f1, final Frame f2, double delta) {
    return compareFrames(f1, f2, delta, 0.0);
  }

  /**
   * Compares two frames. Two frames are equal if and only if they contain the same number of columns, rows,
   * and values at each cell (coordinate) are the same. Column names are ignored, as well as chunks sizes and all other
   * aspects besides those explicitly mentioned.
   *
   * @param f1            Frame to be compared, not null
   * @param f2            Frame to be compared, not null
   * @param delta         absolute tolerance
   * @param relativeDelta relative tolerance
   * @return True if frames are the same up to tolerance - number of columns, rows & values at each cell.
   * @throws AssertionError           If any inequalities are found
   * @throws IllegalArgumentException If input frames don't have the same column and row count
   */
  public static boolean compareFrames(final Frame f1, final Frame f2, double delta, double relativeDelta) {
    Objects.requireNonNull(f1);
    Objects.requireNonNull(f2);

    if (f1.numCols() != f2.numCols())
      throw new IllegalArgumentException(String.format("Number of columns is not the same: {%o, %o}",
              f1.numCols(), f2.numCols()));
    if (f1.numRows() != f2.numRows())
      throw new IllegalArgumentException(String.format("Number of rows is not the same: {%o, %o}",
              f1.numRows(), f2.numRows()));

    for (int vecNum = 0; vecNum < f1.numCols(); vecNum++) {

      final Vec f1Vec = f1.vec(vecNum);
      final Vec f2Vec = f2.vec(vecNum);

      assertVecEquals(f1Vec, f2Vec, delta, relativeDelta);
    }

    return true;
  }

  public static final String[] ignoredColumns(final Frame frame, final String... usedColumns) {
    Set<String> ignored = new HashSet(Arrays.asList(frame.names()));
    ignored.removeAll(Arrays.asList(usedColumns));
    return ignored.toArray(new String[ignored.size()]);
  }

  public static boolean compareFrames(final Frame f1, final Frame f2) throws IllegalStateException {
    return compareFrames(f1, f2, 0);
  }

  /**
   * Sets a locale cluster-wide. Consider returning it back to the default value.
   *
   * @param locale Locale to set to the whole cluster
   */
  public static void setLocale(final Locale locale) {
    new ChangeLocaleTsk(locale)
            .doAllNodes();
  }

  private static class ChangeLocaleTsk extends MRTask<ChangeLocaleTsk> {

    private final Locale _locale;

    public ChangeLocaleTsk(Locale locale) {
      this._locale = locale;
    }

    @Override
    protected void setupLocal() {
      Locale.setDefault(_locale);
    }
  }

  /**
   * Converts a H2OFrame to a csv file for debugging purposes.
   *
   * @param fileNameWithPath: String containing filename with path that will contain the H2O Frame
   * @param h2oframe:         H2O Frame to be saved as CSV file.
   * @param header:           boolean to decide if column names should be saved.  Set to false if don't care.
   * @param hex_string:       boolean to decide if the double values are written in hex.  Set to false if don't care.
   * @throws IOException
   */
  public static void writeFrameToCSV(String fileNameWithPath, Frame h2oframe, boolean header, boolean hex_string)
          throws IOException {

    Frame.CSVStreamParams params = new Frame.CSVStreamParams()
            .setHeaders(header)
            .setHexString(hex_string);
    File targetFile = new File(fileNameWithPath);

    byte[] buffer = new byte[1 << 20];
    int bytesRead;
    try (InputStream frameToStream = h2oframe.toCSV(params);
         OutputStream outStream = new FileOutputStream(targetFile)) {
      while ((bytesRead = frameToStream.read(buffer)) > 0) { // for our toCSV stream, return 0 as EOF, not -1
        outStream.write(buffer, 0, bytesRead);
      }
    }
  }

  /**
   * @param len        Length of the resulting vector
   * @param randomSeed Seed for the random generator (for reproducibility)
   * @return An instance of {@link Vec} with binary weights (either 0.0D or 1.0D, nothing in between).
   */
  public static Vec createRandomBinaryWeightsVec(final long len, final long randomSeed) {
    final Vec weightsVec = Vec.makeZero(len, Vec.T_NUM);
    final Random random = RandomUtils.getRNG(randomSeed);
    for (int i = 0; i < weightsVec.length(); i++) {
      weightsVec.set(i, random.nextBoolean() ? 1.0D : 0D);
    }

    return weightsVec;
  }

  /**
   * @param len        Length of the resulting vector
   * @param randomSeed Seed for the random generator (for reproducibility)
   * @return An instance of {@link Vec} with random double values
   */
  public static Vec createRandomDoubleVec(final long len, final long randomSeed) {
    final Vec vec = Vec.makeZero(len, Vec.T_NUM);
    final Random random = RandomUtils.getRNG(randomSeed);
    for (int i = 0; i < vec.length(); i++) {
      vec.set(i, random.nextDouble());
    }
    return vec;
  }

  /**
   * @param len        Length of the resulting vector
   * @param randomSeed Seed for the random generator (for reproducibility)
   * @return An instance of {@link Vec} with random categorical values
   */
  public static Vec createRandomCategoricalVec(final long len, final long randomSeed) {
    String[] domain = new String[100];
    for (int i = 0; i < domain.length; i++) domain[i] = "CAT_" + i;
    final Vec vec = Scope.track(Vec.makeZero(len, Vec.T_NUM)).makeZero(domain);
    final Random random = RandomUtils.getRNG(randomSeed);
    for (int i = 0; i < vec.length(); i++) {
      vec.set(i, random.nextInt(domain.length));
    }
    return vec;
  }

  @SuppressWarnings("rawtypes")
  public static GenModel toMojo(Model model, String testName, boolean readModelMetaData) {
    final String filename = testName + ".zip";
    StreamingSchema ss = new StreamingSchema(model.getMojo(), filename);
    try (FileOutputStream os = new FileOutputStream(ss.getFilename())) {
      ss.getStreamWriter().writeTo(os);
    } catch (IOException e) {
      throw new IllegalStateException("MOJO writing failed", e);
    }
    try {
      MojoReaderBackend cr = MojoReaderBackendFactory.createReaderBackend(filename);
      return ModelMojoReader.readFrom(cr, readModelMetaData);
    } catch (IOException e) {
      throw new IllegalStateException("MOJO loading failed", e);
    } finally {
      boolean deleted = new File(filename).delete();
      if (!deleted) Log.warn("Failed to delete the file");
    }
  }

  public static boolean isCI() {
    return System.getProperty("user.name").equals("jenkins");
  }

  public static <T extends Keyed<T>> void assertInDKV(Key<T> key, T object) {
    assertEquals(key, object._key);
    T dkvObject = DKV.getGet(key);
    assertNotNull(dkvObject);
    assertEquals(object.checksum(true), dkvObject.checksum(true));
  }

  public static Vec transformVec(Vec vec, Function<Double, Double> transform) {
    new MRTask() {
      @Override
      public void map(Chunk c) {
        for (int i = 0; i < c._len; i++) {
          if (c.isNA(i))
            continue;
          c.set(i, transform.apply(c.atd(i)));
        }
      }
    }.doAll(vec);
    return vec;
  }

  /**
   * Debugging-only function that lets the developer open Flow (or R/Py) during execution of a junit test
   * and inspect the model.
   */
  @SuppressWarnings("unused")
  @Deprecated // just to make it noticeable in IDE
  public static void browser() {
    if (isCI()) {
      throw new IllegalStateException("Never leave browser() calls in committed source code - only for debugging");
    }
    File root = new File(".");
    while (!new File(root, "h2o-core").isDirectory()) {
      root = new File(root, "..");
    }
    H2O.registerResourceRoot(new File(root, "h2o-web/src/main/resources/www"));
    H2O.registerResourceRoot(new File(root, "h2o-core/src/main/resources/www"));

    String message = "Open H2O Flow in your web browser: ";
    System.err.println(message + H2O.getURL(NetworkInit.h2oHttpView.getScheme()));

    while (!H2O.getShutdownRequested()) {
      try {
        Thread.sleep(60 * 1000);
        System.err.println("Still waiting for H2O to shutdown");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

}
