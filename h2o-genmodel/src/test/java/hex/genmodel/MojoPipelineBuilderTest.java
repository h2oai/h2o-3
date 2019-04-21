package hex.genmodel;

import com.google.common.io.ByteStreams;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.RegressionModelPrediction;
import hex.genmodel.tools.BuildPipeline;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;

import static org.junit.Assert.*;

public class MojoPipelineBuilderTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private MojoPipelineBuilder builder;
  private File targetMojoPipelineFile;
  private File kmeansMojoFile;
  private File glmMojoFile;

  @Before
  public void setup() throws IOException {
    builder = new MojoPipelineBuilder();
    targetMojoPipelineFile = tmp.newFile("mojo-pipeline.zip");
    kmeansMojoFile = copyMojoFileResource("kmeans_model.zip");
    glmMojoFile = copyMojoFileResource("glm_model.zip");
  }

  @Test
  public void testPipelineBuilder() throws IOException, PredictException {
    builder
            .addModel("clustering", kmeansMojoFile)
            .addMapping("CLUSTER", "clustering", 0)
            .addMainModel("regression", glmMojoFile)
            .buildPipeline(targetMojoPipelineFile);

    checkPipeline(targetMojoPipelineFile);
  }

  @Test
  public void testBuildPipelineTool() throws IOException, PredictException {
    String[] args = {
            "--mapping", "CLUSTER=kmeans_model:0",
            "--input", kmeansMojoFile.getAbsolutePath(), glmMojoFile.getAbsolutePath(),
            "--output", targetMojoPipelineFile.getAbsolutePath()
    };
    BuildPipeline.main(args);

    checkPipeline(targetMojoPipelineFile);
  }

  private static void checkPipeline(File pipelineFile) throws IOException, PredictException {
    MojoModel mojoPipeline = MojoModel.load(pipelineFile.getAbsolutePath());
    EasyPredictModelWrapper mojoPipelineWr = new EasyPredictModelWrapper(mojoPipeline);

    // prostate row #6
    RowData rd6 = prow(71, 1, 3.0, 2.0, 3.3, 0.0, 8.0);
    RegressionModelPrediction p6 = (RegressionModelPrediction) mojoPipelineWr.predict(rd6);
    assertEquals(0.7812266, p6.value, 1e-7);

    // prostate row #4
    RowData rd4 = prow(76, 2, 2.0, 1.0, 51.2, 20.0, 7.0);
    RegressionModelPrediction p4 = (RegressionModelPrediction) mojoPipelineWr.predict(rd4);
    assertEquals(0.5690164, p4.value, 1e-7);
  }

  private static RowData prow(double age, int race, double dpros, double dcaps, double psa, double vol, double gleason) {
    RowData rd = new RowData();
    rd.put("AGE", age);
    rd.put("RACE", String.valueOf(race));
    rd.put("DPROS", dpros);
    rd.put("DCAPS", dcaps);
    rd.put("PSA", psa);
    rd.put("VOL", vol);
    rd.put("GLEASON", gleason);
    return rd;
  }

  private File copyMojoFileResource(String name) throws IOException {
    File target = tmp.newFile(name);
    try (FileOutputStream fos = new FileOutputStream(target);
         InputStream is = getClass().getResourceAsStream("/hex/genmodel/algos/pipeline/" + name)) {
      ByteStreams.copy(is, fos);
    }
    return target;
  }

}