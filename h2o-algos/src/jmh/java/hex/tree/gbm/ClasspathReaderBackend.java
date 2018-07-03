package hex.tree.gbm;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

class ClasspathReaderBackend implements MojoReaderBackend {

  private final String _dir;

  private ClasspathReaderBackend(String dir) { _dir = dir; }

  @Override
  public BufferedReader getTextFile(String filename) {
    InputStream is = GbmMojoScoringBench.class.getResourceAsStream(_dir + "/" + filename);
    return new BufferedReader(new InputStreamReader(is));
  }

  @Override
  public byte[] getBinaryFile(String filename) throws IOException {
    return IOUtils.toByteArray(GbmMojoScoringBench.class.getResource(_dir + "/" + filename));
  }

  @Override
  public boolean exists(String filename) {
    return GbmMojoScoringBench.class.getResource(_dir + "/" + filename) != null;
  }

  static MojoModel loadMojo(String dir) throws IOException {
    return ModelMojoReader.readFrom(new ClasspathReaderBackend(dir));
  }

}
