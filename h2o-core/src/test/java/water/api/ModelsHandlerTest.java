package water.api;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.TestUtil;
import water.api.schemas3.ModelImportV3;

public class ModelsHandlerTest extends TestUtil {

  @Rule
  public ExpectedException ee = ExpectedException.none();
  
  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testImportModelWithException() {
    ModelImportV3 importSpec = new ModelImportV3();
    importSpec.dir = "/definitely/invalid/directory";
    // the message should show what went wrong
    ee.expectMessage("Illegal argument: dir of function: importModel: water.api.FSIOException: FS IO Failure: \n" +
            " accessed path : file:/definitely/invalid/directory msg: File not found");
    new ModelsHandler().importModel(3, importSpec);
  }
}
