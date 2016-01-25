package ai.h2o.automl;

import hex.Model;
import hex.ModelBuilder;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.fvec.Frame;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
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

  private final static String[] RFGRIDDABLES = new String[]{
          "mtries", "sample_rate", "ntrees", "max_depth",
          "min_rows", "nbins", "nbins_cats", "nbins_top_level"};

  public static final byte RF=0;
  public static final byte GBM=1;
  public static final byte GLM=2;
  public static final byte DL=3;
  public static final byte ANY=4; // randomly select algo on each iteration in collect

  private final int _seconds;  // time allocated per dataset
  private final String _path;  // directory of datasets to collect on
  private byte   _algo;        // algo to run collection on
  private static Connection conn=null;
  static {
    try {
      Class.forName("com.mysql.jdbc.Driver").newInstance();
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
      throw new RuntimeException(ex);
    }

    try {
      conn = DriverManager.getConnection("jdbc:mysql://172.16.2.171/autocollect?user=spencer&password=spencer");
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
  }

  public AutoCollect(int seconds, String path, byte algo) {
    this(seconds, path);
    _algo=algo;
  }

  public void collect() {
    long start = System.currentTimeMillis();
    long elapsed = 0;
    while( elapsed <= _seconds ) {
      ModelBuilder builder = selectNewBuilder();
      Model m = (Model)builder.trainModel().get();
      
      elapsed = System.currentTimeMillis()-start;
    }
  }

  private ModelBuilder selectNewBuilder() {
    byte algo=_algo;
    if( algo == ANY ) algo = (byte)getRNG(new Random().nextLong()).nextInt((int)ANY);
    switch(algo) {
      case RF:  return makeDRF();
      case GBM: return makeGBM();
      case GLM: return makeGLM();
      case DL:  return makeDL();
      default:
        throw new RuntimeException("Unknown algo type: " + algo);
    }
  }

//  DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
//  drf._train = training_frame._key;
//  drf._response_column = response;
////    drf._model_id = Key.make(modelName);
//  drf._ntrees = ntree;
//  drf._max_depth = max_depth;
//  drf._min_rows = min_rows;
//  drf._stopping_rounds = stopping_rounds;
//  drf._stopping_tolerance = stopping_tolerance;
//  drf._nbins = nbins;
//  drf._nbins_cats = nbins_cats;
//  drf._mtries = mtries;
//  drf._sample_rate = sample_rate;
//  drf._seed = seed;
//  return new DRF(drf);

  static DRF makeDRF() {         return new DRF(genRFParams());  }
  static GBM makeGBM() {         return new GBM(genGBMParams()); }
  static GLM makeGLM() {         return new GLM(genGLMParams()); }
  static DeepLearning makeDL() { return new DeepLearning(genDLParams()); }

  static DRFModel.DRFParameters genRFParams() {

    return null;
  }

  static GBMModel.GBMParameters genGBMParams() {
    return null;
  }

  static GLMModel.GLMParameters genGLMParams() {
    return null;
  }

  static DeepLearningModel.DeepLearningParameters genDLParams() {
    return null;
  }


  // member fields updated upon each call to computeMetaData
  private Frame _fr;
  private int[] _preds;
  private int[] _ignored;
  private int _resp;
  private boolean _isClass;
  public int computeMetaData(String datasetName, Frame f, int[] x, int y, boolean isClassification) {
    _fr=f; _preds=x; _resp=y; _isClass=isClassification; ignored(x, f);
    if( hasMeta(datasetName) ) return getidFrameMeta(datasetName);
    FrameMeta fm = new FrameMeta(f, y, x, datasetName, isClassification);
    HashMap<String, Object> frameMeta = FrameMeta.makeEmptyFrameMeta();
    fm.fillSimpleMeta(frameMeta);
    fm.fillDummies(frameMeta);
    int idFrameMeta = pushFrameMeta(frameMeta);
    computeAndPushColMeta(fm, idFrameMeta);
    return idFrameMeta;
  }

  private void ignored(int[] x, Frame f) {
    ArrayList<Integer> ignored = new ArrayList<>();
    int xi=0;
    for(int i=0;i<f.numCols();++i) {
      if( xi < x.length && x[xi]==i ) xi++;
      else        ignored.add(i);
    }
    _ignored = new int[ignored.size()];
    for(int i=0;i<_ignored.length;++i)
      _ignored[i] = ignored.get(i);
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
    String query = "SELECT idFrameMeta  AS id FROM FrameMeta WHERE DataSetName=\"" + datasetName + "\";";
    ResultSet rs = query(query);
    try {
      rs.next();
      return rs.getInt(1);
    } catch( SQLException ex) {
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
      throw new RuntimeException("Failed to retrieve ID for frame: " + datasetName);
    }
  }

  boolean hasMeta(String datasetName) {
    String query = "SELECT COUNT(*) AS cnt FROM FrameMeta WHERE DataSetName=\"" + datasetName + "\";";
    ResultSet rs = query(query);
    try {
      rs.next();
      if (rs.getInt(1) == 1) return true;
    } catch( SQLException ex) {
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
    }
    return false;
  }

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

  public static int update(String query) {
    Statement s;
    try {
      s = conn.createStatement();
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

  private int pushFrameMeta(HashMap<String,Object> fm) { return pushMeta(fm, FrameMeta.METAVALUES, "FrameMeta"); }
  private int pushColMeta  (HashMap<String,Object> cm) { return pushMeta(cm, ColMeta.METAVALUES, "ColMeta"); }
  private int pushMeta(HashMap<String, Object> fm, String[] metaValues, String tableName) {
    StringBuilder sb = new StringBuilder("INSERT INTO " + tableName + " (");
    sb.append(collapseStringArray(metaValues)).append(") \n");
    sb.append("VALUES (");
    int i=0;
    for(String k: metaValues) {
      sb.append("'").append(fm.get(k)).append("'");
      if(i++==fm.size()-1) sb.append(");");
      else sb.append(",");
    }
    return update(sb.toString());
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
}
