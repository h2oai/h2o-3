package hex;

import hex.genmodel.Writer;
import water.api.StreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipOutputStream;

/**
 */
public abstract class FeatureTransformerWriter<T extends FeatureTransformer> extends Writer implements StreamWriter {

  public FeatureTransformerWriter(T transformer) {
    this.transformer = transformer;
  }

  protected T transformer;
  
  @Override
  public String version() {
    return null;
  }

  protected abstract void writeTransformerData() throws IOException;

  @Override
  protected void writeData() throws IOException {
    writeTransformerData();
  }

  // Note: this method is here only because we can't place it at Writer, as we trying to avoid dependencies to core (StreamWriter) from hex.genmodel module
  @Override 
  public void writeTo(OutputStream os) {
    ZipOutputStream zos = new ZipOutputStream(os);
    try {
      writeTo(zos);
      zos.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
