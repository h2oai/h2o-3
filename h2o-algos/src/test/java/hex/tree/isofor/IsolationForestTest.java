package hex.tree.isofor;

import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.algos.isofor.IsolationForestMojoReader;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class IsolationForestTest extends TestUtil {

  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testBasic() {
    try {
      Scope.enter();
      Frame train = Scope.track(parse_test_file("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._sample_size = 5;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame preds = Scope.track(model.score(train));
      assertArrayEquals(new String[]{"predict", "mean_length"}, preds.names());
      assertEquals(train.numRows(), preds.numRows());

      assertTrue(model.testJavaScoring(train, preds, 1e-8));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMojo() throws IOException {
    try (final ByteArrayOutputStream mojoByteArrayOutputStream = new ByteArrayOutputStream()) {
      Scope.enter();
      Frame train = Scope.track(parse_test_file("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 100;
      p._sample_size = 5;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      model.getMojo().writeTo(mojoByteArrayOutputStream);

      final MojoReaderBackend readerBackend = MojoReaderBackendFactory.
              createReaderBackend(new ByteArrayInputStream(mojoByteArrayOutputStream.toByteArray()), MojoReaderBackendFactory.CachingStrategy.MEMORY);

      final MojoModel mojoModel = IsolationForestMojoReader.readFrom(readerBackend);
      assertNotNull(mojoModel);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMojo_largePathLength() throws IOException {
    try (final ByteArrayOutputStream mojoByteArrayOutputStream = new ByteArrayOutputStream()) {
      Scope.enter();
      Frame train = Scope.track(parse_test_file("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 1;
      p._sample_size = 5;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      model._output._min_path_length = 9223372036854775807L;
      model._output._max_path_length = 9223372036854775807L;

      model.getMojo().writeTo(mojoByteArrayOutputStream);

      final MojoReaderBackend readerBackend = MojoReaderBackendFactory.
              createReaderBackend(new ByteArrayInputStream(mojoByteArrayOutputStream.toByteArray()), MojoReaderBackendFactory.CachingStrategy.MEMORY);

      final MojoModel mojoModel = IsolationForestMojoReader.readFrom(readerBackend);
      assertNotNull(mojoModel);

    } finally {
      Scope.exit();
    }
  }

}
