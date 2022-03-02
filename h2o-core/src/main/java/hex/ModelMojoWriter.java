package hex;

import hex.genmodel.AbstractMojoWriter;
import water.Key;
import water.api.SchemaServer;
import water.api.StreamWriteOption;
import water.api.StreamWriter;
import water.api.schemas3.ModelSchemaV3;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.ZipOutputStream;


/**
 * Base class for serializing models into the MOJO format.
 *
 * <p/> The function of a MOJO writer is simply to write the model into a Zip archive consisting of several
 * text/binary files. This base class handles serialization of some parameters that are common to all `Model`s, but
 * anything specific to a particular Model should be implemented in that Model's corresponding ModelMojoWriter subclass.
 *
 * <p/> When implementing a subclass, you have to override the single functions {@link #writeModelData()}. Within
 * this function you can use any of the following:
 * <ul>
 *   <li>{@link #writekv(String, Object)} to serialize any "simple" values (those that can be represented as a
 *       single-line string).</li>
 *   <li>{@link #writeblob(String, byte[])} to add arbitrary blobs of data to the archive.</li>
 *   <li>{@link #startWritingTextFile(String)} / {@link #writeln(String)} / {@link #finishWritingTextFile()} to
 *       add text files to the archive.</li>
 * </ul>
 *
 * After subclassing this class, you should also override the {@link Model#getMojo()} method in your model's class to
 * return an instance of your new child class.
 *
 * @param <M> model class that your ModelMojoWriter serializes
 * @param <P> model parameters class that corresponds to your model
 * @param <O> model output class that corresponds to your model
 */
public abstract class ModelMojoWriter<M extends Model<M, P, O>, P extends Model.Parameters, O extends Model.Output>
        extends AbstractMojoWriter
        implements StreamWriter
{

  protected M model;

  //--------------------------------------------------------------------------------------------------------------------
  // Inheritance interface: ModelMojoWriter subclasses are expected to override these methods to provide custom behavior
  //--------------------------------------------------------------------------------------------------------------------

  public ModelMojoWriter() {
    super(null);
  }

  public ModelMojoWriter(M model) {
    super(model.modelDescriptor());
    this.model = model;
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Used from `ModelsHandler.fetchMojo()` to serialize the Mojo into a StreamingSchema.
   * The structure of the zip will be the following:
   *    model.ini
   *    domains/
   *        d000.txt
   *        d001.txt
   *        ...
   *    (extra model files written by the subclasses)
   * Each domain file is a plain text file with one line per category (not quoted).
   */
  @Override public void writeTo(OutputStream os, StreamWriteOption... option) {
    ZipOutputStream zos = new ZipOutputStream(os);
    try {
      writeTo(zos);
      zos.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected final void writeModelKV() throws IOException {
    if (model._parms._dataTransformers != null) {
      writekv("transformers_count", model._parms._dataTransformers.length);
    }
    writeModelData();
  }
  
  protected abstract void writeModelData() throws IOException;

  @Override
  protected void writeExtraInfo() throws IOException {
    super.writeExtraInfo();
    writeModelDetails();
    writeModelDetailsReadme();
    writeDataTransformers();
  }
  
  protected void writeDataTransformers() throws IOException {
    if (model._parms._dataTransformers == null) return;
    for (int i = 0; i < model._parms._dataTransformers.length; i++) {
      Key<DataTransformer> key = model._parms._dataTransformers[i];
      DataTransformer mp = key.get();
      writemodel("transformers/transformer_"+i+"/", mp.asModel().getMojo());
    }
  }


  /** Create file that contains model details in JSON format.
   * This information is pulled from the models schema.
   */
  private void writeModelDetails() throws IOException {
    ModelSchemaV3 modelSchema = (ModelSchemaV3) SchemaServer.schema(3, model).fillFromImpl(model);
    startWritingTextFile("experimental/modelDetails.json");
    writeln(modelSchema.toJsonString());
    finishWritingTextFile();
  }

  private void writeModelDetailsReadme() throws IOException {
    startWritingTextFile("experimental/README.md");
    writeln("Outputting model information in JSON is an experimental feature and we appreciate any feedback.\n" +
                "The contents of this folder may change with another version of H2O.");
    finishWritingTextFile();
  }

  public void writeStringArrays(String[] sArrays, String title) throws IOException {
    startWritingTextFile(title);
    for (String sName : sArrays) {
      writeln(sName);
    }
    finishWritingTextFile();
  }
  
  public void writeRectangularDoubleArray(double[][] array, String title) throws IOException {
    assert null != array;
    assert null != title;

    writekv(title + "_size1", array.length);
    writekv(title + "_size2", array.length > 0 ? array[0].length : 0);

    writeDoubleArray(array, title);
  }
  
  public void writeDoubleArray(double[][] array, String title) throws IOException {
    assert null != array;
    assert null != title;
    write2DArray(array, title);
  }
  
  public void write2DStringArrays(String[][] sArrays, String title) throws IOException {
    startWritingTextFile(title);
    int numCols = sArrays.length;
    for (int index = 0; index < numCols; index++)
    for (String sName : sArrays[index]) {
      writeln(sName);
    }
    finishWritingTextFile();
  }

  public void write2DArray(double[][] array, String title) throws IOException {
    int totArraySize = 0;
    for (double[] row : array)
      totArraySize += row.length;

    ByteBuffer bb = ByteBuffer.wrap(new byte[totArraySize * 8]);
    for (double[] row : array)
      for (double val : row)
        bb.putDouble(val);
    writeblob(title, bb.array());
  }
  
  public void write3DIntArray(int[][][] array, String title) throws IOException {
    int totArraySize = 0;
    int outDim = array.length;
    for (int index = 0; index < outDim; index++) {
      for (int[] row : array[index])
        totArraySize += row.length;
    }

    ByteBuffer bb = ByteBuffer.wrap(new byte[totArraySize * 4]);
    for (int index = 0; index < outDim; index++)
      for (int[] row : array[index])
        for (int val : row)
          bb.putInt(val);
    writeblob(title, bb.array());
  }
  
  public void write3DArray(double[][][] array, String title) throws IOException {
    int totArraySize = 0;
    int outDim = array.length;
    for (int index = 0; index < outDim; index++) {
      for (double[] row : array[index])
        totArraySize += row.length;
    }

    ByteBuffer bb = ByteBuffer.wrap(new byte[totArraySize * 8]);
    for (int index = 0; index < outDim; index++)
      for (double[] row : array[index])
        for (double val : row)
          bb.putDouble(val);
    writeblob(title, bb.array());
  }
}
