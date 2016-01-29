package ai.h2o.automl;

import hex.Model;
import hex.ModelBuilder;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.DKV;
import water.H2O;
import water.IcedWrapper;
import water.Key;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import static water.util.RandomUtils.getRNG;


/**
 * AutoCollect collects frame metadata and score histories over a grid search on
 * the available h2o supervised learning methods. It stores all of this information
 * in a SQL db (hard-coded).
 *
 * Future enhancements will allow the user to specify where to store information.
 */
public class AutoCollect {

  public static int RFMAXDEPTH=50;
  public static int RFMAXNBINS=1024;
  public static int RFMAXNBINSCATS=1024;
  public static int RFMAXNTREES=1000;
  public static int RFMAXNROWS=50;

  private final static String[] DLGRIDDABLES = new String[]{
          "activation", "layers", "neurons", "epochs", "rho", "rate", "rate_annealing",
          "rate_decay", "momentum_ramp", "momentum_stable", "input_dropout_ratio", "l1",
          "l2", "max_w2", "initial_weight_distribution", "initial_weight_scale"
  };

  private HashSet<String> configs;  // set of configs across all runs

  public static final byte RF=0;
  public static final byte GBM=1;
  public static final byte GLM=2;
  public static final byte DL=3;
  public static final byte ANY=4; // randomly select algo on each iteration in collect

  private final int _seconds;  // time allocated per dataset
  private final String _path;  // directory of datasets to collect on
  private byte   _algo;        // algo to run collection on
  private static Connection conn=null;
  private static Connection conn2=null;
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
    String datasetName;
    String datasetPath;
    int[] x;
    int y;
    boolean isClass;
    Frame fr=null;
    try (BufferedReader br = new BufferedReader(new FileReader(_path))) {
      String line;
      while ((line = br.readLine()) != null) {
        datasetName=line;
        datasetPath=br.readLine();
        String[] preds = br.readLine().split(",");
        x = new int[preds.length];
        for(int i=0;i<preds.length;++i) x[i] = Integer.valueOf(preds[i]);
        y = Integer.valueOf(br.readLine());
        isClass = Boolean.valueOf(br.readLine());
        fr = parseFrame(new File(datasetPath));
        computeMetaData(datasetName,fr,x,y,isClass);
        collect();
        fr.delete();fr=null;
      }
    } catch( IOException ex ) {
      throw new RuntimeException(ex);
    } finally {
      if( fr!=null ) fr.delete();
    }
  }

  protected static Frame parseFrame( File f ) {
    assert f != null && f.exists():" file not found.";
    NFSFileVec nfs = NFSFileVec.make(f);
    return ParseDataset.parse(Key.make(), nfs._key);
  }

  private void collect() {
    logNewCollection();
    try {
      conn.setAutoCommit(false);  // allow failed model builds... don't let them load in!
      long start = System.currentTimeMillis();
      long elapsed = 0;
      long seedSplit;
      Thread resource = new Thread(new Runnable() {
        public void run() {
          //Collect resources info...

          //For the time being create a dummy hash map and send to ResourceMeta
          // Create a hash map
          HashMap<String, Object> hashMap = new HashMap<>();

          //Put elements into hash map
          hashMap.put("RSS", 2);
          hashMap.put("SysCPU",3);
          hashMap.put("ProcCPU",4);
          hashMap.put("timestamp",5);

          //Push to ResourceMeta
          pushResourceMeta(hashMap);
        }
      });
      while (elapsed <= _seconds) {
        Model m=null;
        Frame[] fs=null;
        try {
          fs = ShuffleSplitFrame.shuffleSplitFrame(_fr, new Key[]{Key.make(),Key.make()}, new double[]{0.8,0.2}, seedSplit=getRNG(new Random().nextLong()).nextLong());
          ModelBuilder builder = selectNewBuilder(seedSplit);
          builder._parms._train = fs[0]._key;
          builder._parms._valid = fs[1]._key;
          builder._parms._response_column = _fr.name(_resp);
          builder._parms._ignored_columns = ignored();

          /*===== Beginning of Resource Collection=====*/
          // start resource collector thread
          resource.start();
          //Model training
          m = (Model) builder.trainModel().get();
          // stop resource collector thread
          resource.interrupt();
          /*=====End of Resource Collection=====*/

          elapsed = System.currentTimeMillis() - start;
          logScoreHistory(m._output, getConfig(m._parms));
          conn.commit();
        } catch( IllegalArgumentException iae) {
          iae.printStackTrace();
        } finally {
          if( m!=null ) m.delete();
          if( fs!=null )
            for(Frame f: fs) f.delete();
        }
      }
    } catch(SQLException ex){
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
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
    "================================================\n" +
    "             Beginning New Collection           \n"+
    "================================================";
    Log.info(s);
  }

  private void logScoreHistory(Model.Output output, String configID) {
    HashMap<String, Object> scoreHistory = new HashMap<>();
    List<String> colHeaders = Arrays.asList(output._scoring_history.getColHeaders());
    scoreHistory.put("ConfigID", configID);
    for( IcedWrapper[] iw: output._scoring_history.getCellValues()) {
      if (output instanceof GLMModel.GLMOutput) {
        scoreHistory.put("ts", iw[colHeaders.indexOf("timestamp")].get());
        scoreHistory.put("duration", ((String)iw[colHeaders.indexOf("duration")].get()).trim().split(" ")[0]);
        scoreHistory.put("iteration", iw[colHeaders.indexOf("iteration")].get());
        scoreHistory.put("negative_log_likelihood", iw[colHeaders.indexOf("negative_log_likelihood")].get());
        scoreHistory.put("objective", iw[colHeaders.indexOf("objective")].get());
        scoreHistory.put("lambda", _isClass?sanitize(iw[colHeaders.indexOf("lambda")]): AutoML.SQLNAN);
        scoreHistory.put("num_predictors", _isClass?sanitize(iw[colHeaders.indexOf("Number of Predictors")]):AutoML.SQLNAN);
        scoreHistory.put("train_explained_deviance", _isClass?sanitize(iw[colHeaders.indexOf("Explained Deviance (train)")]):AutoML.SQLNAN);
        scoreHistory.put("test_explained_deviance", _isClass?sanitize(iw[colHeaders.indexOf("Explained Deviance (test)")]):AutoML.SQLNAN);
        pushMeta(scoreHistory, scoreHistory.keySet().toArray(new String[scoreHistory.size()]), "GLMScoreHistory",null);
      } else {
        scoreHistory.put("ts", iw[colHeaders.indexOf("Timestamp")].get());
        scoreHistory.put("train_mse", sanitize((double) iw[colHeaders.indexOf("Training MSE")].get()));
        scoreHistory.put("train_logloss", _isClass ? sanitize((double) iw[colHeaders.indexOf("Training LogLoss")].get()) : AutoML.SQLNAN);
        scoreHistory.put("train_classification_error", sanitize((double) (_isClass ? iw[colHeaders.indexOf("Training Classification Error")].get() : AutoML.SQLNAN)));
        scoreHistory.put("test_mse", sanitize((double) iw[colHeaders.indexOf("Validation MSE")].get()));
        scoreHistory.put("test_logloss", _isClass ? sanitize((double) (iw[colHeaders.indexOf("Validation LogLoss")].get())) : AutoML.SQLNAN);
        scoreHistory.put("test_classification_error", sanitize((double) (_isClass ? iw[colHeaders.indexOf("Validation Classification Error")].get() : AutoML.SQLNAN)));
        scoreHistory.put("train_deviance", sanitize((double) (_isClass ? AutoML.SQLNAN : iw[colHeaders.indexOf("Training Deviance")].get())));
        scoreHistory.put("test_deviance", sanitize((double) (_isClass ? AutoML.SQLNAN : iw[colHeaders.indexOf("Validation Deviance")].get())));
        pushMeta(scoreHistory, scoreHistory.keySet().toArray(new String[scoreHistory.size()]), "ScoreHistory",null);
      }
    }
  }

  private static double sanitize(double d) { return Double.isNaN(d) ? AutoML.SQLNAN : d; }
  private static Object sanitize(IcedWrapper o) { return o==null?AutoML.SQLNAN : o.get(); }

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

  private ModelBuilder selectNewBuilder(long seedSplit) {
    byte algo=_algo;
    if( algo == ANY || algo==DL ) algo = (byte)getRNG(new Random().nextLong()).nextInt((int)DL);  // TODO: currently disabling DL
    switch(algo) {
      case RF:  return makeDRF(seedSplit);
      case GBM: return makeGBM(seedSplit);
      case GLM: return makeGLM(seedSplit);
      case DL:  return makeDL(seedSplit);
      default:
        throw new RuntimeException("Unknown algo type: " + algo);
    }
  }

  DRF makeDRF(long seedSplit) { return new DRF(genRFParams(seedSplit));  }
  GBM makeGBM(long seedSplit) { return new GBM(genGBMParams(seedSplit)); }
  GLM makeGLM(long seedSplit) { return new GLM(genGLMParams(seedSplit)); }
  DeepLearning makeDL(long seedSplit) { return new DeepLearning(genDLParams(seedSplit)); }

  private String getConfig(Model.Parameters parms) {
    String configID;
    if( parms instanceof DRFModel.DRFParameters) {
      DRFModel.DRFParameters p = (DRFModel.DRFParameters)parms;
      configID = "rf_"+_idFrame+"_"+p._mtries+"_"+p._sample_rate+"_"+p._ntrees+"_"+p._max_depth+"_"+p._min_rows+"_"+p._nbins+"_"+p._nbins_cats;
    } else if( parms instanceof GBMModel.GBMParameters ) {
      GBMModel.GBMParameters p = (GBMModel.GBMParameters)parms;
      configID = "gbm_"+_idFrame+"_"+p._ntrees+"_"+p._max_depth+"_"+p._min_rows+"_"+p._learn_rate+"_"+p._sample_rate+"_"+p._col_sample_rate+"_"+p._col_sample_rate_per_tree+"_"+p._nbins+"_"+p._nbins_cats;
    } else if( parms instanceof GLMModel.GLMParameters ) {
      GLMModel.GLMParameters p = (GLMModel.GLMParameters)parms;
      configID = "glm_"+_idFrame+"_"+p._alpha[0]+"_"+p._lambda[0];
    } else if( parms instanceof DeepLearningModel.DeepLearningParameters ) {
      DeepLearningModel.DeepLearningParameters p = (DeepLearningModel.DeepLearningParameters)parms;
      throw H2O.unimpl();
    } else
      throw new IllegalArgumentException("Don't know what to do with parameters: " + parms.getClass());
    return configID;
  }

  DRFModel.DRFParameters genRFParams(long seedSplit) {
    String configID;
    DRFModel.DRFParameters p = new DRFModel.DRFParameters();
    HashMap<String, Object> config = new HashMap<>();
    config.put("idFrame", _idFrame);
    config.put("SplitSeed", seedSplit);
    do {
      config.put("mtries", p._mtries = 1+getRNG(new Random().nextLong()).nextInt(_preds.length));
      config.put("sample_rate", p._sample_rate = getRNG(new Random().nextLong()).nextFloat());
      config.put("ntrees", p._ntrees = getRNG(new Random().nextLong()).nextInt(RFMAXNTREES));
      config.put("max_depth", p._max_depth = getRNG(new Random().nextLong()).nextInt(RFMAXDEPTH));
      config.put("min_rows", p._min_rows = getRNG(new Random().nextLong()).nextInt(RFMAXNROWS));
      config.put("nbins", p._nbins = 20+getRNG(new Random().nextLong()).nextInt(RFMAXNBINS));
      config.put("nbins_cats", p._nbins_cats = 20+getRNG(new Random().nextLong()).nextInt(RFMAXNBINSCATS));
      config.put("ConfigID", configID = getConfig(p));
    } while(!isValidConfig(configID));
    configs.add(configID);
    pushMeta(config, config.keySet().toArray(new String[config.size()]), "RFConfig",null);
    return p;
  }

  GBMModel.GBMParameters genGBMParams(long seedSplit) {
    String configID;
    GBMModel.GBMParameters p = new GBMModel.GBMParameters();
    HashMap<String, Object> config = new HashMap<>();
    config.put("idFrame", _idFrame);
    config.put("SplitSeed", seedSplit);
    do {
      config.put("ntrees", p._ntrees = getRNG(new Random().nextLong()).nextInt(RFMAXNTREES));
      config.put("max_depth", p._max_depth = getRNG(new Random().nextLong()).nextInt(RFMAXDEPTH));
      config.put("min_rows", p._min_rows = getRNG(new Random().nextLong()).nextInt(RFMAXNROWS));
      config.put("learn_rate", p._learn_rate = getRNG(new Random().nextLong()).nextFloat());
      config.put("sample_rate", p._sample_rate = getRNG(new Random().nextLong()).nextFloat());
      config.put("col_sample_rate", p._col_sample_rate = getRNG(new Random().nextLong()).nextFloat());
      config.put("col_sample_rate_per_tree", p._col_sample_rate_per_tree = getRNG(new Random().nextLong()).nextFloat());
      config.put("nbins", p._nbins = 20+getRNG(new Random().nextLong()).nextInt(RFMAXNBINS));
      config.put("nbins_cats", p._nbins_cats = 20+getRNG(new Random().nextLong()).nextInt(RFMAXNBINSCATS));
      config.put("ConfigID", configID = getConfig(p));
    } while(!isValidConfig(configID));
    configs.add(configID);
    pushMeta(config, config.keySet().toArray(new String[config.size()]), "GBMConfig",null);
    return p;
  }

  GLMModel.GLMParameters genGLMParams(long seedSplit) {
    String configID;
    GLMModel.GLMParameters p = new GLMModel.GLMParameters();
    HashMap<String, Object> config = new HashMap<>();
    config.put("idFrame", _idFrame);
    config.put("SplitSeed", seedSplit);
    do {
      config.put("alpha", (p._alpha = new double[]{getRNG(new Random().nextLong()).nextDouble()})[0]);
      // for lambda, place probability distribution heavily weighted to the left: [0,1e-3) U [1e-3, 1) U [1, 10)  ... with probs (0.8, 0.1, 0.1)
      double r = getRNG(new Random().nextLong()).nextDouble();
      double lambda=getRNG(new Random().nextLong()).nextDouble();
      if( r < 0.8 )
        while( lambda < 1e-3)
          lambda = getRNG(new Random().nextLong()).nextDouble();
      else if( 0.8 <= r && r < 0.9 )
        while (1e-3 <= lambda && lambda < 1 )
          lambda = getRNG(new Random().nextLong()).nextDouble();
      else
        while(lambda < 1 || lambda > 10)
          lambda = 1 + getRNG(new Random().nextLong()).nextDouble()*10;
        config.put("lambda", (p._lambda = new double[]{lambda})[0]);
      config.put("ConfigId", configID = getConfig(p));
    } while(!isValidConfig(configID));
    configs.add(configID);
    pushMeta(config, config.keySet().toArray(new String[config.size()]), "GLMConfig",null);
    if( _isClass ) {
      p._family = _fr.vec(_resp).domain().length==2? GLMModel.GLMParameters.Family.binomial: GLMModel.GLMParameters.Family.multinomial;
    }
    return p;
  }

  DeepLearningModel.DeepLearningParameters genDLParams(long seedSplit) { throw H2O.unimpl(); }
    //          "activation", "layers", "neurons", "epochs", "rho", "rate", "rate_annealing",
//    "rate_decay", "momentum_ramp", "momentum_stable", "input_dropout_ratio", "l1",
//            "l2", "max_w2", "initial_weight_distribution", "initial_weight_scale"
//    String configID;
//    DeepLearningModel.DeepLearningParameters p = new DeepLearningModel.DeepLearningParameters();
//    do {
//      p._activation = DeepLearningModel.DeepLearningParameters.Activation.values()[getRNG(new Random().nextLong()).nextInt(DeepLearningModel.DeepLearningParameters.Activation.values().length)];
//      p._hidden
//      configID = getConfig(p);
//    } while(!isValidConfig(configID));
//    configs.add(configID);
//    return p;
//  }

  private boolean isValidConfig(String configID) { return !configs.contains(configID); }

  // member fields updated upon each call to computeMetaData
  private Frame _fr;
  private int[] _preds;
  private int[] _ignored;
  private int _resp;
  private boolean _isClass;
  private int _idFrame;
  public void computeMetaData(String datasetName, Frame f, int[] x, int y, boolean isClassification) {
    _fr=f; _preds=x; _resp=y; _isClass=isClassification; ignored(x, f);
    if( _isClass ) {
      _fr.replace(y, _fr.vec(y).toCategoricalVec()).remove();
      DKV.put(_fr);
      _fr.reloadVecs();
    }
    if( hasMeta(datasetName) )
      _idFrame=getidFrameMeta(datasetName);
    else {
      FrameMeta fm = new FrameMeta(f, y, x, datasetName, isClassification);
      HashMap<String, Object> frameMeta = FrameMeta.makeEmptyFrameMeta();
      fm.fillSimpleMeta(frameMeta);
      fm.fillDummies(frameMeta);
      _idFrame = pushFrameMeta(frameMeta);
      computeAndPushColMeta(fm, _idFrame);
    }
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
      if (rs.getInt(1) != 0) return true;
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

  private int pushFrameMeta(HashMap<String,Object> fm) { return pushMeta(fm, FrameMeta.METAVALUES, "FrameMeta",null); }
  private int pushColMeta  (HashMap<String,Object> cm) { return pushMeta(cm, ColMeta.METAVALUES, "ColMeta",null); }
  private int pushResourceMeta (HashMap<String,Object> cm) { return pushMeta(cm, cm.keySet().toArray(new String[0]), "ResourceMeta",conn2); }
  private int pushMeta(HashMap<String, Object> fm, String[] metaValues, String tableName, Connection c) {
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
}
