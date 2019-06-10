package hex.mojo;

import hex.genmodel.tools.PredictCsv;
import hex.tree.PrintMojoTreeTest;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.FileUtils;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.security.Permission;

import static org.junit.Assert.*;


public class PredictCsvTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private SecurityManager originalSecurityManager;

  @Before
  public void setUp() throws Exception {
    TestUtil.stall_till_cloudsize(1);
    originalSecurityManager = System.getSecurityManager();
    System.setSecurityManager(new PreventExitSecurityManager());
  }

  @After
  public void tearDown() throws Exception {
    System.setSecurityManager(originalSecurityManager);
  }

  @Test
  public void testScoreMissingColumns() throws IOException {
    final PrintStream originaOutputStream = System.out;
    String predictCsvOutput = null;
    try {
      Scope.enter();
      // The following iris dataset has columns named: {C1,C2,C3,C4,C5}, while the test dataset used below has descriptive names. 
      Frame train = Scope.track(TestUtil.parse_test_file("smalldata/iris/iris.csv"));

      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._train = train._key;
      p._seed = 0xC0DE;
      p._response_column = "C5";
      p._ntrees = 1;

      GBMModel model = new GBM(p).trainModel().get();
      final File modelFile = folder.newFile();
      model.exportMojo(modelFile.getAbsolutePath(), true);

      final File outputFile = folder.newFile();

      ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(outputBytes);
      System.setOut(printStream);
      try {
        PredictCsv.main(new String[]{"--mojo", modelFile.getAbsolutePath(),
                "--input", FileUtils.getFile("smalldata/iris/iris_test.csv").getAbsolutePath(),
                "--output", outputFile.getAbsolutePath()});
        fail("Expected PredictCSV to exit");
      } catch (PreventedExitException e) {
        assertEquals(0, e.status); // PredictCsv is expected to finish without errors
      }
      
      predictCsvOutput = new String(outputBytes.toByteArray());
      assertEquals("There were 5 missing columns found in the input data set: {C1,C2,C3,C4,C5}\n" +
              "Detected 5 unused columns in the input data set: {petal_wid,sepal_len,species,petal_len,sepal_wid}\n", predictCsvOutput);
      

    } finally {
      System.setOut(originaOutputStream);
      System.out.print(predictCsvOutput);
      Scope.exit();
    }
  }


  protected static class PreventedExitException extends SecurityException {
    public final int status;

    public PreventedExitException(int status) {
      this.status = status;
    }
  }

  /**
   * Security managers that prevents PrintMojo from exiting.
   */
  private static class PreventExitSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission perm) {
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
    }

    @Override
    public void checkExit(int status) {
      throw new PreventedExitException(status);
    }
  }

}
