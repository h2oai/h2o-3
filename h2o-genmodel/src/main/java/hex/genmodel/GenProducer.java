package hex.genmodel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Better name is probably GenTransformer but due to naming conflict with feature transformers let's call it GenProducer - something that
 * produce extra columns based on existing ones  (e.g encodings or predictions)
 */
public abstract class GenProducer {

  public abstract double[] produce(double[] row, double[] dataToProduce);

  /**
   *  Discriminator method
   * @param file
   * @return
   * @throws IOException
   */
  public static GenProducer load(String file) throws IOException {
    File f = new File(file);
    if (!f.exists())
      throw new FileNotFoundException("File " + file + " cannot be found.");
    MojoReaderBackend cr = f.isDirectory()? new FolderMojoReaderBackend(file)
            : new ZipfileMojoReaderBackend(file);
    if( cr.exists("model.ini")) {
      return ModelMojoReader.readFrom(cr);
    }
    else {
      return TransformerMojoReader.readFrom(cr, false);
    }
  }
}
