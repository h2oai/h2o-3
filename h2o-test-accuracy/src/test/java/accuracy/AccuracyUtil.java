package accuracy;

import water.*;
import water.fvec.*;
import water.parser.ParseDataset;
import water.parser.ParseSetup;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

// TODO: I think this should just extent TestUtil and maybe override the stall_till_cloudsize method???
public class AccuracyUtil extends Iced {
    private static boolean _stall_called_before = false;
    protected static int _initial_keycnt = 0;
    protected static int MINCLOUDSIZE;

    private static String accuracyDBHost, accuracyDBPort, accuracyDBUser, accuracyDBPwd, accuracyDBName,
      accuracyDBTableName, accuracyDBTimeout;
    private static PrintStream summaryPrintStream;


    public AccuracyUtil() { this(1); }
    public AccuracyUtil(int minCloudSize) { MINCLOUDSIZE = Math.max(MINCLOUDSIZE,minCloudSize); }

    // Logging utils
    public static void setupLogging() throws IOException {
        File h2oResultsDir = new File(find_test_file_static("h2o-test-accuracy").getCanonicalFile().toString() +
          "/results");
        if (!h2oResultsDir.exists()) { h2oResultsDir.mkdir(); }

        File suiteSummary = new File(find_test_file_static("h2o-test-accuracy/results").getCanonicalFile().toString() +
          "/summary.txt");
        suiteSummary.createNewFile();
        summaryPrintStream = new PrintStream(new FileOutputStream(suiteSummary, false));

        System.out.println("Commenced logging to h2o-test-accuracy/results directory.");
    }
    public static void log(String info) { summaryPrintStream.println(info + "\n"); }
    public static void logStackTrace(Exception e) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        log(stringWriter.toString());
    }

    // Database utils
    public static void configureAccessToDB() {
        //String configPath = System.getProperty("dbConfigFilePath");
        String configPath = "/Users/ece/0xdata/h2o-3/DBConfig.properties";
        if (!(null == configPath)) {
            File configFile = new File(configPath);
            Properties properties = new Properties();
            try {
                properties.load(new BufferedReader(new FileReader(configFile)));
            } catch (IOException e) {
                summaryPrintStream.println("Cannot load database configuration: " + configPath);
                summaryPrintStream.println(e.getMessage());
                System.exit(-1);
            }
            summaryPrintStream.println("The following database configuration settings were read from " + configPath);
            summaryPrintStream.println(accuracyDBHost = properties.getProperty("db.host"));
            summaryPrintStream.println(accuracyDBPort = properties.getProperty("db.port"));
            summaryPrintStream.println(accuracyDBUser = properties.getProperty("db.user"));
            summaryPrintStream.println(accuracyDBPwd = properties.getProperty("db.password"));
            summaryPrintStream.println(accuracyDBName = properties.getProperty("db.databaseName"));
            summaryPrintStream.println(accuracyDBTableName = properties.getProperty("db.tableName"));
            summaryPrintStream.println(accuracyDBTimeout = properties.getProperty("db.queryTimeout"));
            summaryPrintStream.println("");
        }
    }
    public static String getDBHost()      { return accuracyDBHost; }
    public static String getDBPort()      { return accuracyDBPort; }
    public static String getDBName()      { return accuracyDBName; }
    public static String getDBUser()      { return accuracyDBUser; }
    public static String getDBPwd()       { return accuracyDBPwd; }
    public static String getDBTableName() { return accuracyDBTableName; }

    // H2O cloud setup utils
    public static void setupH2OCloud(int numNodes) {
        stall_till_cloudsize(numNodes);
        _initial_keycnt = H2O.store_size();
    }

    // ==== Test Setup & Teardown Utilities ====
    // Stall test until we see at least X members of the Cloud
    public static void stall_till_cloudsize(int x) {
        if (! _stall_called_before) {
            if (H2O.getCloudSize() < x) {
                // Leader node, where the tests execute from.
                String cloudName = UUID.randomUUID().toString();
                String[] args = new String[]{"-name",cloudName,"-ice_root",find_test_file_static("h2o-test-accuracy/" +
                  "results").toString()};
                H2O.main(args);

                // Secondary nodes, skip if expected to be pre-built
                if( System.getProperty("ai.h2o.skipNodeCreation") == null )
                    for( int i = 0; i < x-1; i++ )
                        new NodeContainer(args).start();

                H2O.waitForCloudSize(x, 30000);

                _stall_called_before = true;
            }
        }
    }

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
            file = null;
        return file;
    }

    /** Hunt for test files in likely places.  Null if cannot find.
     *  @param fname Test filename
     *  @return      Found file or null */
    protected File find_test_file(String fname) {
        return find_test_file_static(fname);
    }

    /** Find & parse a CSV file.  NPE if file not found.
     *  @param fname Test filename
     *  @return      Frame or NPE */
    protected Frame parse_test_file( String fname ) { return parse_test_file(Key.make(),fname); }
    protected Frame parse_test_file( Key outputKey, String fname) {
        File f = find_test_file(fname);
        assert f != null && f.exists():" file not found: " + fname;
        NFSFileVec nfs = NFSFileVec.make(f);
        return ParseDataset.parse(outputKey, nfs._key);
    }
    protected Frame parse_test_file( Key outputKey, String fname , boolean guessSetup) {
        File f = find_test_file(fname);
        assert f != null && f.exists():" file not found: " + fname;
        NFSFileVec nfs = NFSFileVec.make(f);
        return ParseDataset.parse(outputKey, new Key[]{nfs._key}, true, ParseSetup.guessSetup(new Key[]{nfs._key}, false, 1));
    }

    /** Find & parse a folder of CSV files.  NPE if file not found.
     *  @param fname Test filename
     *  @return      Frame or NPE */
    protected Frame parse_test_folder( String fname ) {
        File folder = find_test_file(fname);
        assert folder.isDirectory();
        File[] files = folder.listFiles();
        Arrays.sort(files);
        ArrayList<Key> keys = new ArrayList<>();
        for( File f : files )
            if( f.isFile() )
                keys.add(NFSFileVec.make(f)._key);
        Key[] res = new Key[keys.size()];
        keys.toArray(res);
        return ParseDataset.parse(Key.make(), res);
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
        Key k = Vec.VectorGroup.VG_LEN1.addVec();
        Futures fs = new Futures();
        AppendableVec avec = new AppendableVec(k, Vec.T_NUM);
        avec.setDomain(domain);
        NewChunk chunk = new NewChunk(avec, 0);
        for( int r : rows ) chunk.addNum(r);
        chunk.close(0, fs);
        Vec vec = avec.layout_and_close(fs);
        fs.blockForPending();
        return vec;
    }

    /** Create a new frame based on given row data.
     *  @param key   Key for the frame
     *  @param names names of frame columns
     *  @param rows  data given in the form of rows
     *  @return new frame which contains columns named according given names and including given data */
    public static Frame frame(Key key, String[] names, double[]... rows) {
        assert names == null || names.length == rows[0].length;
        Futures fs = new Futures();
        Vec[] vecs = new Vec[rows[0].length];
        Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);
        int rowLayout = -1;
        for( int c = 0; c < vecs.length; c++ ) {
            AppendableVec vec = new AppendableVec(keys[c], Vec.T_NUM);
            NewChunk chunk = new NewChunk(vec, 0);
            for (double[] row : rows) chunk.addNum(row[c]);
            chunk.close(0, fs);
            if( rowLayout == -1 ) rowLayout = vec.compute_rowLayout();
            vecs[c] = vec.close(rowLayout,fs);
        }
        fs.blockForPending();
        Frame fr = new Frame(key, names, vecs);
        if( key != null ) DKV.put(key,fr);
        return fr;
    }
    public static Frame frame(double[]... rows) { return frame(null, rows); }
    public static Frame frame(String[] names, double[]... rows) { return frame(Key.make(), names, rows); }
    public static Frame frame(String name, Vec vec) { Frame f = new Frame(); f.add(name, vec); return f; }

    // Shortcuts for initializing constant arrays
    public static String[]   ar (String ...a)   { return a; }
    public static String[][] ar (String[] ...a) { return a; }
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
    public static <T> T[] aro(T ...a) { return a ;}
}
