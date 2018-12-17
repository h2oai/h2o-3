package hex.mojopipeline;

import ai.h2o.mojos.runtime.frame.*;
import ai.h2o.mojos.runtime.MojoPipeline;
import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Scope;
import water.TestUtil;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.ParseSetup;

import java.io.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class MojoPipelineTest extends TestUtil {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Parameterized.Parameter
  public String testCase;

  private String dataFile;
  private String mojoFile;

  @Parameterized.Parameters(name="{0}")
  public static Object[] data() {
    String testDir = System.getenv("MOJO_PIPELINE_TEST_DIR");
    if (testDir == null) {
      return new Object[0];
    }
    return new File(testDir).list();
  }

  @BeforeClass
  public static void stall() {
    stall_till_cloudsize(1);
  }

  @Before
  public void checkLicense() {
    Assume.assumeNotNull(System.getenv("DRIVERLESS_AI_LICENSE_FILE"));
  }

  @Before
  public void extractData() throws IOException  {
    String testDir = System.getenv("MOJO_PIPELINE_TEST_DIR");
    Assume.assumeNotNull(testDir);

    File source = new File(new File(testDir, testCase), "mojo.zip");
    File target = tmp.newFolder(testCase);
    extractZip(source, target);
    dataFile = new File(new File(target, "mojo-pipeline"), "example.csv").getAbsolutePath();
    mojoFile = new File(new File(target, "mojo-pipeline"), "pipeline.mojo").getAbsolutePath();
  }

  @Test
  public void transform() throws Exception {
    try {
      Scope.enter();
      // get the expected data
      MojoPipeline model = null;
      MojoFrame expected = null;
      try {
        model = MojoPipeline.loadFrom(mojoFile);
        expected = transformDirect(model, dataFile);
      } catch (Exception e) {
        Assume.assumeNoException(e);
      }
      assertNotNull(model);
      assertNotNull(expected);

      final ByteVec mojoData = makeNfsFileVec(mojoFile);
      final Frame t = Scope.track(loadData(model));

      hex.mojopipeline.MojoPipeline mp = new hex.mojopipeline.MojoPipeline(mojoData);
      Frame transformed = mp.transform(t, false);

      System.out.println(transformed.toTwoDimTable().toString());
      assertFrameEquals(transformed, expected);
    } finally {
      Scope.exit();
    }
  }

  private MojoFrame transformDirect(MojoPipeline model, String dataPath) throws Exception {
    final String[] labels = model.getInputMeta().getColumnNames();
    final String[][] data = loadData(dataPath, labels);

    MojoFrameBuilder fb = model.getInputFrameBuilder();
    MojoRowBuilder rb = fb.getMojoRowBuilder();
    for (String[] row : data) {
      for (int i = 0; i < row.length; i += 1)
        rb.setValue(labels[i], row[i]);
      rb = fb.addRow(rb);
    }
    MojoFrame input = fb.toMojoFrame();
    return model.transform(input);
  }

  private Frame loadData(MojoPipeline model) throws Exception {
    final MojoFrameMeta meta = model.getInputMeta();
    // H2O CSV Parser is not able to parse "TwoSigma" dataset correctly - make a String Frame instead
    if ("test_mojo_twosigma_1".equals(testCase)) {
      String[] colNames = meta.getColumnNames();
      return toFrame(loadData(dataFile, colNames), colNames);
    } else {
      return parseCsv(dataFile, meta);
    }
  }

  private static Frame parseCsv(final String dataFile, final MojoFrameMeta meta) {
    return parse_test_file(dataFile, new ParseSetupTransformer() {
      @Override
      public ParseSetup transformSetup(ParseSetup guessedSetup) {
        byte[] columnTypes = guessedSetup.getColumnTypes();
        for (int i = 0; i < meta.size(); i++) {
          if ((columnTypes[i] == Vec.T_NUM) && ! meta.getColumnType(i).isnumeric) {
            columnTypes[i] = Vec.T_STR;
          }
          if ((columnTypes[i] == Vec.T_TIME) && meta.getColumnType(i) != MojoColumn.Type.Time64) {
            columnTypes[i] = Vec.T_STR;
          }
        }
        return guessedSetup;
      }
    });
  }

  private String[][] loadData(String dataPath, String[] labels) throws Exception {
    final String[][] data;
    try (CSVReader reader = new CSVReader(new FileReader(dataPath))) {
      List<String[]> rows = reader.readAll();
      assert Arrays.equals(labels, rows.get(0)); // Check header
      data = rows.subList(1, rows.size()).toArray(new String[0][]);
    }
    // TwoSigma Hack
    if ("test_mojo_twosigma_1".equals(testCase)) {
      for (int i = 0; i < data.length; i++) {
        data[i] = fixRowTwoSigma(data[i], labels.length);
      }
    }
    return data;
  }

  private static String[] fixRowTwoSigma(String[] row, int cols) {
    if (row.length == cols) {
      return row;
    }
    String[] fixed = new String[cols];
    int col = 0;
    fixed[col++] = row[0];
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i < row.length - cols + 1; i++) {
      sb.append(row[i]);
      sb.append(",");
    }
    fixed[col++] = sb.deleteCharAt(sb.length() - 1).toString();
    for (int i = 2 + row.length - cols; i < row.length; i++) {
      fixed[col++] = row[i];
    }
    assert col == cols;
    return fixed;
  }

  // Helper Functions

  private static byte[] rep(byte val, int cnt) {
    byte[] ary = new byte[cnt];
    Arrays.fill(ary, val);
    return ary;
  }

  private static void extractZip(File source, File target) throws IOException {
    try (ZipFile zipFile = new ZipFile(source)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        File entryDestination = new File(target,  entry.getName());
        if (entry.isDirectory()) {
          if (! entryDestination.mkdirs()) {
            throw new IOException("Failed to create directory: " + entryDestination);
          }
        } else {
          if (! entryDestination.getParentFile().exists() && ! entryDestination.getParentFile().mkdirs()) {
            throw new IOException("Failed to create directory: " + entryDestination.getParentFile());
          }
          try (InputStream in = zipFile.getInputStream(entry);
               OutputStream out = new FileOutputStream(entryDestination)) {
            IOUtils.copy(in, out);
          }
        }
      }
    }
  }

  private static Frame toFrame(String[][] data, String[] colNames) {
    TestFrameBuilder fb = new TestFrameBuilder()
            .withColNames(colNames)
            .withVecTypes(rep(Vec.T_STR, colNames.length));
    for (int i = 0; i < colNames.length; i++) {
      String[] vals = new String[data.length];
      for (int j = 0; j < vals.length; j++) {
        vals[j] = data[j][i];
      }
      fb.withDataForCol(i, vals);
    }
    return fb.build();
  }

  private static void assertFrameEquals(Frame actual, MojoFrame expected) {
    assertArrayEquals(actual.names(), expected.getColumnNames());
    for (int i = 0; i < expected.getNcols(); i++) {
      double[] vals = (double[]) expected.getColumn(i).getData();
      Vec expectedVec = Scope.track(dvec(vals));
      assertVecEquals(expectedVec, actual.vec(i), 1e-6);
    }
  }

}
