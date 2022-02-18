package water.hive;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.fvec.Vec;

import static org.junit.Assert.*;

public class HiveTableImporterImplTest {

  @Rule
  public ExpectedException ee = ExpectedException.none();
  
  @Test
  public void convertHiveType() {
    assertEquals(Vec.T_NUM, HiveTableImporterImpl.convertHiveType("int"));
    assertEquals(Vec.T_STR, HiveTableImporterImpl.convertHiveType("varchar(100)"));
    assertEquals(Vec.T_STR, HiveTableImporterImpl.convertHiveType("Invalid Type")); // fallback
  }

  @Test
  public void convertHiveType_strict() {
    assertEquals(Vec.T_STR, HiveTableImporterImpl.convertHiveType("varchar(100)", true));
    ee.expectMessage("Unsupported column type: Invalid Type");
    HiveTableImporterImpl.convertHiveType("Invalid Type", true);
  }

  @Test
  public void sanitizeHiveType() {
    assertEquals("anything", HiveTableImporterImpl.sanitizeHiveType(" anyTHING   "));
    assertEquals("varchar", HiveTableImporterImpl.sanitizeHiveType("VARCHAR (100)"));
  }
}
