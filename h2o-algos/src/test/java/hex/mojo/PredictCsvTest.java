package hex.mojo;

import hex.genmodel.tools.PredictCsv;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.security.Permission;

import static org.junit.Assert.*;
import static water.TestUtil.parseAndTrackTestFile;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class PredictCsvTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private SecurityManager originalSecurityManager;

  @Before
  public void setUp() {
    originalSecurityManager = System.getSecurityManager();
    System.setSecurityManager(new PreventExitSecurityManager());
  }

  @After
  public void tearDown() {
    System.setSecurityManager(originalSecurityManager);
  }

  @Test
  public void testScoreMissingColumns() throws IOException {
    final PrintStream originaOutputStream = System.out;
    String predictCsvOutput = null;
    try {
      Scope.enter();
      // The following iris dataset has columns named: {C1,C2,C3,C4,C5}, while the test dataset used below has descriptive names. 
      Frame train = parseAndTrackTestFile("smalldata/iris/iris.csv");

      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._train = train._key;
      p._seed = 0xC0DE;
      p._response_column = "C5";
      p._ntrees = 1;

      GBMModel model = new GBM(p).trainModel().get();
      Scope.track_generic(model);

      final File modelFile = folder.newFile();
      model.exportMojo(modelFile.getAbsolutePath(), true);

      final File outputFile = folder.newFile();

      ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(outputBytes);
      System.setOut(printStream);
      try {
        PredictCsv.main(new String[]{"--mojo", modelFile.getAbsolutePath(),
                "--input", TestUtil.makeNfsFileVec("smalldata/iris/iris_test.csv").getPath(),
                "--output", outputFile.getAbsolutePath()});
        fail("Expected PredictCSV to exit");
      } catch (PreventedExitException e) {
        assertEquals(0, e.status); // PredictCsv is expected to finish without errors
      }
      
      predictCsvOutput = new String(outputBytes.toByteArray());
      assertTrue(predictCsvOutput.contains("There were 4 missing columns found in the input data set:"));
      assertTrue(predictCsvOutput.contains("C1"));
      assertTrue(predictCsvOutput.contains("C2"));
      assertTrue(predictCsvOutput.contains("C3"));
      assertTrue(predictCsvOutput.contains("C4"));

      assertTrue(predictCsvOutput.contains("Detected 5 unused columns in the input data set:"));
      assertTrue(predictCsvOutput.contains("petal_wid"));
      assertTrue(predictCsvOutput.contains("sepal_len"));
      assertTrue(predictCsvOutput.contains("species"));
      assertTrue(predictCsvOutput.contains("petal_len"));
      assertTrue(predictCsvOutput.contains("sepal_wid"));

    } finally {
      System.setOut(originaOutputStream);
      System.out.print(predictCsvOutput);
      Scope.exit();
    }
  }

  @Test
  public void testScoreNoMissingColumns() throws IOException {
    final PrintStream originaOutputStream = System.out;
    String predictCsvOutput = null;
    try {
      Scope.enter();
      // The following iris dataset has columns named: {C1,C2,C3,C4,C5}, while the test dataset used below has descriptive names. 
      Frame train = parseAndTrackTestFile("smalldata/junit/iris.csv");

      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._train = train._key;
      p._seed = 0xC0DE;
      p._response_column = "class";
      p._ntrees = 1;

      GBMModel model = new GBM(p).trainModel().get();
      Scope.track_generic(model);
      final File modelFile = folder.newFile();
      model.exportMojo(modelFile.getAbsolutePath(), true);

      final File outputFile = folder.newFile();

      ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(outputBytes);
      System.setOut(printStream);
      try {
        PredictCsv.main(new String[]{"--mojo", modelFile.getAbsolutePath(),
                "--input", TestUtil.makeNfsFileVec("smalldata/junit/iris.csv").getPath(),
                "--output", outputFile.getAbsolutePath()});
        fail("Expected PredictCSV to exit");
      } catch (PreventedExitException e) {
        assertEquals(0, e.status); // PredictCsv is expected to finish without errors
      }

      predictCsvOutput = new String(outputBytes.toByteArray());
      assertTrue(predictCsvOutput.isEmpty());
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
