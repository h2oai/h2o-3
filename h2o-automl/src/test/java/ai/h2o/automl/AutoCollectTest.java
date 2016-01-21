package ai.h2o.automl;

import org.junit.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AutoCollectTest { // requires mysql-connector-java-3.1.14-bin.jar in -cp
  @Test public void testConnection() {
    ResultSet rs = AutoCollect.query("SHOW TABLES;");
    try {
      while(rs.next()) {
        System.out.println(rs.getString(1));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        rs.close();
      } catch(SQLException ex) {}
    }
    System.out.println();
  }

  @Test public void testHasDataset() {
    AutoCollect ac = new AutoCollect(3600,"");
    System.out.println(ac.hasMeta("iris"));
    System.out.println("--");
  }
}
