package hex.tree;

import hex.Model;
import hex.genmodel.tools.PrintMojo;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Permission;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class PrintMojoTreeTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private SecurityManager originalSecurityManager;

  @Before
  public void setUp() throws Exception {
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
      Frame train = Scope.track(parseTestFile("smalldata/iris/iris.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._ignored_columns = new String[]{"C1", "C2", "C3", "C4"};
      p._seed = 0xFEED;
      p._ntrees = 1;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      Scope.track_generic(model);
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
      Frame train = Scope.track(parseTestFile("smalldata/iris/iris.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._ignored_columns = new String[]{"C1", "C2", "C3", "C4"};
      p._seed = 0xFEED;
      p._ntrees = 1;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      Scope.track_generic(model);
      final File modelFile = folder.newFile();
      model.exportMojo(modelFile.getAbsolutePath(), true);

      final File treeOutput = folder.newFile();
      try {
        PrintMojo.main(new String[]{"--input", modelFile.getAbsolutePath(), "--output", treeOutput.getAbsolutePath(), "--levels", "1"});
        fail("Expected PrintMojo to call System.exit()");
      } catch (PreventedExitException e) {
        // expected
      }

      final String treeDotz = FileUtils.readFileToString(treeOutput);
      assertTrue(treeDotz.contains("\"SG_0_Node_0\" -> \"SG_0_Node_1\" [fontsize=14, label=\"[NA]\n2 levels\n\"]\n" +
          "\"SG_0_Node_0\" -> \"SG_0_Node_4\" [fontsize=14, label=\"Iris-virginica\n\"]\n" +
          "\"SG_0_Node_1\" -> \"SG_0_Node_5\" [fontsize=14, label=\"Iris-versicolor\n\"]\n" +
          "\"SG_0_Node_1\" -> \"SG_0_Node_6\" [fontsize=14, label=\"[NA]\nIris-setosa\n\"]"));

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
      Frame train = Scope.track(parseTestFile("smalldata/iris/iris.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._ignored_columns = new String[]{"C1", "C2", "C3", "C4"};
      p._seed = 0xFEED;
      p._ntrees = 1;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      Scope.track_generic(model);
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
  
  private void assertMojoJSONEqualsFixture(Model model, String fixtureFile) throws IOException {
    final File modelFile = folder.newFile();
    model.exportMojo(modelFile.getAbsolutePath(), true);
    final File treeOutput = folder.newFile();
    try {
      PrintMojo.main(new String[]{"--input", modelFile.getAbsolutePath(), "--output", treeOutput.getAbsolutePath(), "--format", "json"});
      fail("Expected PrintMojo to call System.exit()");
    } catch (PreventedExitException e) {
      // expected
    }
    final String treeJson = FileUtils.readFileToString(treeOutput);
    assertFalse(treeJson.isEmpty());
    final String expectedTreeJson = IOUtils.toString(getClass().getResourceAsStream(fixtureFile));
    assertEquals(
            removeMOJOVersion(removeH2OVersion(expectedTreeJson)),
            removeMOJOVersion(removeH2OVersion(treeJson))
    );
  }
  
  private void assertMojoPngGenerated(Model model, String[] expectedFileNames) throws IOException {
      final Path modelPath = folder.newFile().toPath();
      model.exportMojo(modelPath.toAbsolutePath().toString(), true);
      final Path treeOutputPath = folder.newFile("exampleh2o.png").toPath();
      try {
        PrintMojo.main(new String[]{"--input", modelPath.toAbsolutePath().toString(), "--output", treeOutputPath.toAbsolutePath().toString(), "--format", "png"});
        fail("Expected PrintMojo to call System.exit()");
      } catch (PreventedExitException e) {
        // expected
      }
      int numberOfFiles = expectedFileNames.length;
      if(numberOfFiles > 1) {
        List<Path> fileNames = Files.list(treeOutputPath)
               .sorted(Comparator.reverseOrder())
               .collect(Collectors.toList());
        for (int i = 0; i < numberOfFiles; i++) {
            assertTrue(fileNames.get(i).endsWith(expectedFileNames[i]));
        } 
      } else {
        assertTrue(treeOutputPath.endsWith(expectedFileNames[0]));
      }
  }
  
  private String removeH2OVersion(String json) {
    return json.replaceAll("\"h2o_version\": \"[\\d\\.]+\"", "h2o_version");
  }

  private String removeMOJOVersion(String json) {
    return json.replaceAll("\"mojo_version\": [\\d\\.]+", "mojo_version");
  }  
  
  @Test
  public void testMojoCategoricalJson() throws IOException {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/testng/airlines.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xFEED;
      p._ntrees = 1;
      p._max_depth = 3;
      p._ignored_columns = new String[] { "Origin", "Dest", "IsDepDelayed" };

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      Scope.track_generic(model);
      assertMojoJSONEqualsFixture(model, "categorical.json");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMojoCategoricalOneHotJson() throws IOException {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/testng/airlines.csv"));

      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._train = train._key;
      p._seed = 0xFEED;
      p._response_column = "IsDepDelayed";
      p._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;
      p._ntrees = 2;
      p._max_depth = 3;
      p._ignored_columns = new String[] { "Origin", "Dest" };

      GBMModel model = new GBM(p).trainModel().get();
      Scope.track_generic(model);
      assertMojoJSONEqualsFixture(model, "categoricalOneHot.json");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMojoGBMJson() throws IOException {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/extdata/prostate.csv"));

      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._train = train._key;
      p._response_column = "CAPSULE";
      p._ignored_columns = new String[]{"ID"};
      p._seed = 1;
      p._ntrees = 2;
      p._max_depth = 3;

      GBMModel model = new GBM(p).trainModel().get();
      Scope.track_generic(model);
      assertMojoJSONEqualsFixture(model, "gbmProstate.json");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMojoCategoricalPng() throws IOException {
    try {
      Scope.enter();
      String[] expectedFileNames = {"exampleh2o.png"};
      Frame train = Scope.track(parseTestFile("smalldata/testng/airlines.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xFEED;
      p._ntrees = 1;
      p._max_depth = 3;
      p._ignored_columns = new String[] { "Origin", "Dest", "IsDepDelayed" };

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      Scope.track_generic(model);
      assertMojoPngGenerated(model, expectedFileNames);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testMojoCategoricalOneHotPng() throws IOException {
    try {
      Scope.enter();
      String[] expectedFileNames = new String[2];
      expectedFileNames[0]="Tree1_ClassNO.png";
      expectedFileNames[1]="Tree0_ClassNO.png";
      
      Frame train = Scope.track(parseTestFile("smalldata/testng/airlines.csv"));

      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._train = train._key;
      p._seed = 0xFEED;
      p._response_column = "IsDepDelayed";
      p._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;
      p._ntrees = 2;
      p._max_depth = 3;
      p._ignored_columns = new String[] { "Origin", "Dest" };

      GBMModel model = new GBM(p).trainModel().get();
      Scope.track_generic(model);
      assertMojoPngGenerated(model, expectedFileNames);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testMojoGBMPng() throws IOException {
    try {
      Scope.enter();
      String[] expectedFileNames = new String[2];
      expectedFileNames[0]="Tree1.png";
      expectedFileNames[1]="Tree0.png";
      Frame train = Scope.track(parseTestFile("smalldata/extdata/prostate.csv"));

      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._train = train._key;
      p._response_column = "CAPSULE";
      p._ignored_columns = new String[]{"ID"};
      p._seed = 1;
      p._ntrees = 2;
      p._max_depth = 3;

      GBMModel model = new GBM(p).trainModel().get();
      Scope.track_generic(model);
      assertMojoPngGenerated(model, expectedFileNames);
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
