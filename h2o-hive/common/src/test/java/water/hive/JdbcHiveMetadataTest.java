package water.hive;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.H2O;

public class JdbcHiveMetadataTest {

  @Rule
  public ExpectedException ee = ExpectedException.none();
  
  @Test
  public void getTableRegistersHiveDriver() throws Exception {
    ee.expectMessage("Connection to HIVE database is not possible due to missing JDBC driver.");
    new JdbcHiveMetadata("jdbc:hive2:anything").getTable("test_table");
  }

  @Test
  public void getTableInitializesDriverUsingSQLManager() throws Exception {
    // poor man's way of showing that getTable internally uses SQLManager to get the driver connection 
    ee.expectMessage(
            "Connection to 'dummy1' database is not possible due to missing JDBC driver. " +
                    "User specified driver class: org.dummy.Driver"
    );

    System.setProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.jdbc.driver.dummy1", "org.dummy.Driver");
    new JdbcHiveMetadata("jdbc:dummy1:localhost")
            .getTable("test_table");
  }
  
}
