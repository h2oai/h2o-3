package ai.h2o.automl;

import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import water.fvec.Frame;

import java.sql.*;
import java.util.HashMap;


/**
 * AutoCollect collects frame metadata and score histories over a grid search on
 * the available h2o supervised learning methods. It stores all of this information
 * in a SQL db (hard-coded).
 *
 * Future enhancements will allow the user to specify where to store information.
 */
public class AutoCollect {

  private final int _seconds;  // time allocated per dataset
  private final String _path;  // directory of datasets to collect on
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
  }

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

  public void computeMetaData(String datasetName, Frame f, int[] x, int y, boolean isClassification) {
    //if( !hasMeta(datasetName) ) {
      // gather up the FrameMeta data.
      FrameMeta fm = new FrameMeta(f,y,datasetName, isClassification);
      HashMap<String, Object> frameMeta = FrameMeta.makeEmptyFrameMeta();
      fm.fillSimpleMeta(frameMeta);
      fm.fillDummies(frameMeta);
      int idFrameMeta = pushFrameMeta(frameMeta);
      computeAndPushColMeta(fm, idFrameMeta);
//    }
  }

  private void computeAndPushColMeta(FrameMeta fm, int idFrameMeta) {

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
      System.out.println("SQLException: " + ex.getMessage());
      System.out.println("SQLState: " + ex.getSQLState());
      System.out.println("VendorError: " + ex.getErrorCode());
    }
    throw new RuntimeException("Query failed");
  }

  private int pushFrameMeta(HashMap<String,Object> fm) {
    StringBuilder sb = new StringBuilder("INSERT INTO FrameMeta (");
    sb.append(collapseStringArray(FrameMeta.METAVALUES)).append(") \n");
    sb.append("VALUES (");
    int i=0;
    for(String k: FrameMeta.METAVALUES) {
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
