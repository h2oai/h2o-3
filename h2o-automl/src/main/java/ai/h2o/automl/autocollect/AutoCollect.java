package ai.h2o.automl.autocollect;

import ai.h2o.automl.ColMeta;
import ai.h2o.automl.FrameMeta;
import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.parser.ParserType;
import water.util.Log;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import static water.util.RandomUtils.getRNG;

/**
 * AutoCollect collects frame metadata and score histories over a grid search on
 * the available h2o supervised learning methods. It stores all of this information
 * in a SQL db (hard-coded).
 *
 * Future enhancements will allow the user to specify where to store information.
 */
public class AutoCollect {

  public static final byte RF=0;
  public static final byte GBM=1;
  public static final byte DL=2;
  public static final byte ANY=3; // randomly select algo on each iteration in collect;
  public static final double SQLNAN = -99999;

  private HashSet<String> configs;      // set of configs across all runs
  private final int _seconds;           // time allocated per dataset
  private final String _path;           // directory of datasets to collect on
  private byte   _algo;                 // algo to run collection on
  private static Connection conn=null;  // connection for posting to tables
  private static Connection conn2=null; // 2nd conn for resource thread
  static {
    try {
      Class.forName("com.mysql.jdbc.Driver").newInstance();
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
      throw new RuntimeException(ex);
    }

    try {
      conn = DriverManager.getConnection("jdbc:mysql://172.16.2.171/autocollect?user=spencer&password=spencer");
      conn2 = DriverManager.getConnection("jdbc:mysql://172.16.2.171/autocollect?user=spencer&password=spencer");
    } catch (SQLException ex) {
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
    }
  }

  public AutoCollect(int seconds, String path) {
    _seconds=seconds;
    _path=path;
    _algo=ANY;
    grabConfigs();
  }

  public AutoCollect(int seconds, String path, byte algo) {
    this(seconds, path);
    _algo=algo;
  }

  public void start() {
    MetaConfig[] datasets = MetaConfig.readMeta(_path);
    for(MetaConfig mc: datasets) {
      if( mc != null ) {
        try {
          mc.parseFrame();
          mc.parseTestFrame();
          setFields(mc.name(), mc.frame(), mc.testFrame(), mc.x(), mc.y(), mc.isClass());
          checkResponse();
          if( computeMetaData(mc) ) collect();  // only collect for successful collection of meta data on frame
        } finally {
          mc.delete();
        }
      }
    }
  }

  protected static Frame parseFrame( ParseSetup ps, Key fkey, String name ) { return ParseDataset.parse(Key.make(name), new Key[]{fkey}, true, ps); }
  protected static ParseSetup paresSetup(NFSFileVec nfs, ParserType parserType) {
    return ParseSetup.guessSetup(new Key[]{nfs._key}, new ParseSetup(parserType, ParseSetup.GUESS_SEP, false, 0, ParseSetup.GUESS_COL_CNT, null));
  }
  // TODO: ParseSetup(ParserType.GUESS, GUESS_SEP, singleQuote, checkHeader, GUESS_COL_CNT, null)

  private void collect() {
    logNewCollection();
    try {
      conn.setAutoCommit(false);  // allow failed model builds... don't let them load in!
      double elapsed = 0;
      long start = System.currentTimeMillis();
      while (elapsed <= _seconds) {
        selectCollector().collect(_idFrame, _fr, _test, ignored(), _fr.name(_resp), getRNG(new Random().nextLong()).nextLong(), configs);
        elapsed = (System.currentTimeMillis() - start)/1000.;
        conn.commit();
      }
    } catch(SQLException ex) {
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
    } catch (Exception ex2) {
      ex2.printStackTrace();
    } finally {
      try {
        conn.setAutoCommit(true);
      } catch(SQLException ex) {
        System.out.println("SQLException: " + ex.getMessage());
        System.out.println("SQLState: " + ex.getSQLState());
        System.out.println("VendorError: " + ex.getErrorCode());
      }
    }
  }

  private static void logNewCollection() {
    String s =
    "================================================\n"+
    "             Beginning New Collection           \n"+
    "================================================";
    Log.info(s);
  }

  private void grabConfigs() {
    configs = new HashSet<>();
    for (String table : new String[]{"RFConfig", "GBMConfig", "DLConfig", "GLMConfig"}) {
      ResultSet rs = AutoCollect.query("SELECT ConfigID FROM " + table + ";");
      try {
        while (rs.next())
          configs.add(rs.getString(1));
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  private Collector selectCollector() {
    byte algo =_algo;
    if( algo == ANY )
      algo = (byte) getRNG(new Random().nextLong()).nextInt(_algo);
    switch( algo ) {
      case RF:  return new DRFCollect();
      case GBM: return new GBMCollect();
      case DL:  return new DLCollect();
      default:
        throw new IllegalArgumentException("Unknown algo type: " + _algo);
    }
  }

  // member fields updated upon each call to computeMetaData
  private Frame _fr;
  private Frame _test;
  private int[] _preds;
  private int[] _ignored;
  private int _resp;
  private boolean _isClass;
  private int _idFrame;

  private void setFields(String datasetName, Frame f, Frame ftest, int[] x, int y, boolean isClassification) {
    _fr=f; _test=ftest; _preds=x; _resp=y; _isClass=isClassification; ignored(x, f);
  }
  private void checkResponse() {
    if( _isClass && !_fr.vec(_resp).isCategorical() ) {
      Log.warn("Expected response column to be categorical, but was " + _fr.vec(_resp).get_type_str());
      Log.warn("Converting the response column to categorical.");
      _fr.replace(_resp, _fr.vec(_resp).toCategoricalVec()).remove();
      DKV.put(_fr);
      _fr.reloadVecs();
    }
  }
  public boolean computeMetaData(MetaConfig mc) {
    try {
      conn.setAutoCommit(false);  // transactionally set metadata...
      if( (_idFrame=getidFrameMeta(mc.name()))==-1 ) {
        FrameMeta fm = new FrameMeta(mc.frame(), mc.y() , mc.x(), mc.name(), mc.isClass());
        HashMap<String, Object> frameMeta = FrameMeta.makeEmptyFrameMeta();
        fm.fillSimpleMeta(frameMeta);
        fm.fillDummies(frameMeta);
        _idFrame = pushFrameMeta(frameMeta);
        computeAndPushColMeta(fm, _idFrame);
        new GLMCollect().collect(_idFrame, _fr, _test, ignored(), _fr.name(_resp), getRNG(new Random().nextLong()).nextLong(), configs);
      }
      conn.commit();
    } catch(SQLException ex) {
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
      return false;
    }
    return true;
  }

  private void ignored(int[] x, Frame f) {
    ArrayList<Integer> ignored = new ArrayList<>();
    int xi=0;
    for(int i=0;i<f.numCols();++i) {
      if( xi < x.length && x[xi]==i ) xi++;
      else ignored.add(i);
    }
    _ignored = new int[ignored.size()];
    for(int i=0;i<_ignored.length;++i)
      _ignored[i] = ignored.get(i);
  }

  private String[] ignored() {
    if( _ignored.length-1 == 0) return null;
    String[] res = new String[_ignored.length-1];
    for(int i=0;i<res.length;++i) {
      String cname = _fr.name(_ignored[i]);
      if( !cname.equals(_fr.name(_resp)) )
        res[i] = cname;
    }
    return res;
  }

  private void computeAndPushColMeta(FrameMeta fm, int idFrameMeta) {
    fm.computeFrameMetaPass1();
    fm.computeVIFs();
    for(int i=0; i<fm._cols.length; ++i) {
      HashMap<String, Object> colMeta = ColMeta.makeEmptyColMeta();
      fm._cols[i].fillColMeta(colMeta, idFrameMeta);
      pushColMeta(colMeta);
    }
  }

  int getidFrameMeta(String datasetName) {
    String query = "SELECT idFrameMeta AS id FROM FrameMeta WHERE DataSetName='" + datasetName + "';";
    ResultSet rs = query(query);
    try {
      if( !rs.next() ) return -1;
      return rs.getInt(1);
    } catch( SQLException ex) {
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
      throw new RuntimeException("Failed to retrieve ID for frame: " + datasetName);
    }
  }

  public boolean hasMeta(String datasetName) { return getidFrameMeta(datasetName)!=-1; }

  // up to the caller to close the ResultSet
  // TODO: use AutoCloseable
  public static ResultSet query(String query) {
    Statement s;
    ResultSet rs;
    try {
      s = conn.createStatement();
      rs = s.executeQuery(query);
      return rs;
    } catch (SQLException ex) {
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
    }
    throw new RuntimeException("Query failed");
  }

  public static int update(String query,Connection c) {

    Statement s;
    try {
      s = (c==null?conn:c).createStatement();
      s.executeUpdate(query, Statement.RETURN_GENERATED_KEYS);
      ResultSet genKeys = s.getGeneratedKeys();
      genKeys.next();
      return genKeys.getInt(1); // return the last insertID
    } catch (SQLException ex) {
      System.out.println("STATEMENT: " + query);
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
    }
    throw new RuntimeException("Query failed");
  }

  private static int pushFrameMeta(HashMap<String,Object> fm) { return pushMeta(fm, FrameMeta.METAVALUES, "FrameMeta",null); }
  private static int pushColMeta  (HashMap<String,Object> cm) { return pushMeta(cm, ColMeta.METAVALUES, "ColMeta",null); }
  static int pushResourceMeta (HashMap<String,Object> cm) { return pushMeta(cm, cm.keySet().toArray(new String[cm.size()]), "ResourceMeta",conn2); }
  static int pushMeta(HashMap<String, Object> fm, String[] metaValues, String tableName, Connection c) {
    StringBuilder sb = new StringBuilder("INSERT INTO " + tableName + " (");
    sb.append(collapseStringArray(metaValues)).append(") \n");
    sb.append("VALUES (");
    int i=0;
    for(String k: metaValues) {
      sb.append("'").append(fm.get(k)).append("'");
      if(i++==fm.size()-1) sb.append(");");
      else sb.append(",");
    }
    return update(sb.toString(),c);
  }

  static String collapseStringArray(String[] strs) {
    StringBuilder sb = new StringBuilder();
    for(int i=0;i<strs.length;++i) {
      sb.append(strs[i]);
      if( i==strs.length-1 ) return sb.toString();
      sb.append(",");
    }
    throw new RuntimeException("Should never be here");
  }

  public static void main(String[] args) {
    H2O.main(new String[]{});
    H2O.registerRestApis(System.getProperty("user.dir"));
    System.out.println("Beginning AutoCollect...");
    AutoCollect ac = new AutoCollect(3*3600, "meta");
    ac.start();
    System.out.println("AutoCollect end.");
    H2O.orderlyShutdown();
  }
}
