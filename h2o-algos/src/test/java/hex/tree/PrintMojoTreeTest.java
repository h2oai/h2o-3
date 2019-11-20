package hex.tree;

import hex.genmodel.tools.PrintMojo;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.Scope;
import water.TestBase;
import water.TestUtil;
import water.fvec.Frame;

import java.io.File;
import java.io.IOException;
import java.security.Permission;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class PrintMojoTreeTest extends TestBase {

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
  public void testMojoCategoricalPrint() throws IOException {
    try {
      Scope.enter();
      Frame train = Scope.track(TestUtil.parse_test_file("smalldata/iris/iris.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._ignored_columns = new String[]{"C1", "C2", "C3", "C4"};
      p._seed = 0xFEED;
      p._ntrees = 1;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      final File modelFile = folder.newFile();
      model.exportMojo(modelFile.getAbsolutePath(), true);

      final File treeOutput = folder.newFile();
      try {
        PrintMojo.main(new String[]{"--input", modelFile.getAbsolutePath(), "--output", treeOutput.getAbsolutePath()});
        fail("Expected PrintMojo to call System.exit()");
      } catch (PreventedExitException e) {
      }

      final String treeDotz = FileUtils.readFileToString(treeOutput);
      System.out.println(treeDotz);
      assertFalse(treeDotz.isEmpty());


      final Pattern labelPattern = Pattern.compile("label{1}=\\\"(.*?)\\\"");
      final Pattern labelContentPattern = Pattern.compile(".*[<>=].*"); // Contains < or > or =
      final Matcher matcher = labelPattern.matcher(treeDotz);

      assertEquals(1, matcher.groupCount());

      while (matcher.find()){
        assertFalse(labelContentPattern.matcher(matcher.group(1)).matches());
      }

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMojoCategoricalPrint_limitedLevels() throws IOException {
    try {
      Scope.enter();
      Frame train = Scope.track(TestUtil.parse_test_file("smalldata/iris/iris.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._ignored_columns = new String[]{"C1", "C2", "C3", "C4"};
      p._seed = 0xFEED;
      p._ntrees = 1;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      final File modelFile = folder.newFile();
      model.exportMojo(modelFile.getAbsolutePath(), true);

      final File treeOutput = folder.newFile();
      try {
        PrintMojo.main(new String[]{"--input", modelFile.getAbsolutePath(), "--output", treeOutput.getAbsolutePath(), "--levels", "1"});
        fail("Expected PrintMojo to call System.exit()");
      } catch (PreventedExitException e) {
      }

      final String treeDotz = FileUtils.readFileToString(treeOutput);
      assertFalse(treeDotz.isEmpty());

      assertTrue(treeDotz.contains("\"SG_0_Node_0\" -> \"SG_0_Node_1\" [fontsize=14, label=\"[NA]\\n2 levels\\n\"]\n" +
              "\"SG_0_Node_0\" -> \"SG_0_Node_4\" [fontsize=14, label=\"Iris-virginica\\n\"]\n" +
              "\"SG_0_Node_1\" -> \"SG_0_Node_5\" [fontsize=14, label=\"Iris-versicolor\\n\"]\n" +
              "\"SG_0_Node_1\" -> \"SG_0_Node_6\" [fontsize=14, label=\"[NA]\\nIris-setosa\\n\"]"));

    } finally {
      Scope.exit();
    }
  }

  /**
   * Tests if internal split representation is present in the graph. Labels contain real numbers as split and
   * edge labels contain lesser than/equals/greater than signs.
   */
  @Test
  public void testMojoCategoricalPrint_internalRepresentationOutput() throws IOException {
    try {
      Scope.enter();
      Frame train = Scope.track(TestUtil.parse_test_file("smalldata/iris/iris.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._ignored_columns = new String[]{"C1", "C2", "C3", "C4"};
      p._seed = 0xFEED;
      p._ntrees = 1;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      final File modelFile = folder.newFile();
      model.exportMojo(modelFile.getAbsolutePath(), true);

      final File treeOutput = folder.newFile();
      try {
        PrintMojo.main(new String[]{"--input", modelFile.getAbsolutePath(), "--output", treeOutput.getAbsolutePath(), "--internal"});
        fail("Expected PrintMojo to call System.exit()");
      } catch (PreventedExitException e) {
      }

      final String treeDotz = FileUtils.readFileToString(treeOutput);
      assertFalse(treeDotz.isEmpty());
      System.out.println(treeDotz);

      final Pattern labelPattern = Pattern.compile("label{1}=\\\"(.*?)\\\"");
      final Pattern labelContentPattern = Pattern.compile(".*[<>=].*"); // Contains < or > or =
      final Matcher matcher = labelPattern.matcher(treeDotz);

      assertEquals(1, matcher.groupCount());

      int matches = 0;
      while (matcher.find()){
        if(labelContentPattern.matcher(matcher.group(1)).matches()){
          matches++; // Find labels with '<>=' inside. In non-internal representation, there should be none in a graph with purely categorical splits
        }
      }

      assertTrue(matches > 0);

    } finally {
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
