package water.hive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HiveTokenGeneratorTest {

  @Test
  public void makeHivePrincipalJdbcUrl() {
    assertNull(HiveTokenGenerator.makeHivePrincipalJdbcUrl(null, null, null));
    assertNull(HiveTokenGenerator.makeHivePrincipalJdbcUrl(null, null, "principal@DOMAIN"));
    assertNull(HiveTokenGenerator.makeHivePrincipalJdbcUrl("anything", null, null));
    assertNull(HiveTokenGenerator.makeHivePrincipalJdbcUrl(null, "anything", null));
    assertEquals(
            "jdbc:hive2://host:42/;principal=principal@DOMAIN",
            HiveTokenGenerator.makeHivePrincipalJdbcUrl(null, "host:42", "principal@DOMAIN"));
    assertEquals(
            "jdbc:hive2://hostname:10000/core;ssl=true;sslTrustStore=/path/to/file.jks;principal=hive/HOST@DOMAIN",
            HiveTokenGenerator.makeHivePrincipalJdbcUrl(
                "jdbc:hive2://{{host}}/core;ssl=true;sslTrustStore=/path/to/file.jks;{{auth}}", 
                "hostname:10000", "hive/HOST@DOMAIN"
            )
    );
    assertEquals(
            "jdbc:hive2://hostname:10000/core;ssl=true;sslTrustStore=/path/to/file.jks;principal=hive/HOST@DOMAIN",
            HiveTokenGenerator.makeHivePrincipalJdbcUrl(
                "jdbc:hive2://hostname:10000/core;ssl=true;sslTrustStore=/path/to/file.jks;{{auth}}", 
                null, "hive/HOST@DOMAIN")
    );
    assertEquals("anything", HiveTokenGenerator.makeHivePrincipalJdbcUrl("anything", null, "principal"));
  }

  @Test
  public void makeHiveDelegationTokenJdbcUrl() {
    assertNull(HiveTokenGenerator.makeHiveDelegationTokenJdbcUrl(null, null));
    assertEquals(
        "jdbc:hive2://host:42/;auth=delegationToken",
        HiveTokenGenerator.makeHiveDelegationTokenJdbcUrl(null, "host:42"));
    assertEquals(
        "jdbc:hive2://hostname:10000/core;ssl=true;sslTrustStore=/path/to/file.jks;auth=delegationToken",
        HiveTokenGenerator.makeHiveDelegationTokenJdbcUrl(
            "jdbc:hive2://{{host}}/core;ssl=true;sslTrustStore=/path/to/file.jks;{{auth}}", 
            "hostname:10000"
        )
    );
    assertEquals(
        "jdbc:hive2://hostname:10000/core;auth=delegationToken;ssl=true;sslTrustStore=/path/to/file.jks",
        HiveTokenGenerator.makeHiveDelegationTokenJdbcUrl(
            "jdbc:hive2://hostname:10000/core;{{auth}};ssl=true;sslTrustStore=/path/to/file.jks", 
            null
        )
    );
    assertEquals("anything", HiveTokenGenerator.makeHiveDelegationTokenJdbcUrl("anything", null));
  }

}
