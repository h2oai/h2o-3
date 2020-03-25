package water.hadoop;

import org.junit.Test;
import water.hive.HiveTokenGenerator;

import static org.junit.Assert.*;

public class HiveTokenGeneratorTest {

  @Test
  public void makeHiveJdbcUrl() {
    assertNull(HiveTokenGenerator.makeHiveJdbcUrl(null, null, null));
    assertEquals(
            "jdbc:hive2://host:42/;principal=principal",
            HiveTokenGenerator.makeHiveJdbcUrl(null, "host:42", "principal"));
    assertEquals(
            "jdbc:hive2://hostname:10000/core;ssl=true;sslTrustStore=/path/to/file.jks;principal=hive/HOST@DOMAIN",
            HiveTokenGenerator.makeHiveJdbcUrl("jdbc:hive2://{{host}}/core;ssl=true;sslTrustStore=/path/to/file.jks;principal={{principal}}", "hostname:10000", "hive/HOST@DOMAIN"));
    assertEquals(
            "jdbc:hive2://hostname:10000/core;ssl=true;sslTrustStore=/path/to/file.jks;principal=hive/HOST@DOMAIN",
            HiveTokenGenerator.makeHiveJdbcUrl("jdbc:hive2://hostname:10000/core;ssl=true;sslTrustStore=/path/to/file.jks;principal={{principal}}", null, "hive/HOST@DOMAIN"));
    assertEquals("anything", HiveTokenGenerator.makeHiveJdbcUrl("anything", null, null));
  }

}
