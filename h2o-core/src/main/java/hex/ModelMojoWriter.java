package hex;

import org.joda.time.DateTime;
import water.H2O;
import water.api.StreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Base class for all models' MOJO writers.
 *
 * <p/> The function of a MOJO writer is simply to serialize the model into a Zip archive consisting of several
 * text/binary files. This base class handles serialization of some parameters that are common to all `Model`s, but
 * anything specific to a particular Model should be implemented in that Model's corresponding ModelMojoWriter subclass.
 *
 * <p/> When implementing a subclass, you have to override two functions:
 * <dl>
 *    <dt>{@link #writeExtraModelInfo()}</dt>
 *    <dd>to serialize any "simple" values (what counts as simple is entirely up to you). Within this class you can
 *        use {@link #writekv(String, Object)} in order to serialize any value under the given key. The value
 *        will be converted to string using its <code>toString()</code> method. The only restriction is that the
 *        value's string representation must not contain a newline.</dd>
 *    <dt>{@link #writeModelData()}</dt>
 *    <dd>to serialize any additional data (either text or binary). You can use
 *        {@link #writeBinaryFile(String, byte[])} to add arbitrary blobs of data to the archive; or
 *        {@link #startWritingTextFile(String)} / {@link #writeln(String)} / {@link #finishWritingTextFile()} to
 *        create new text files.</dd>
 * </dl>
 *
 * After subclassing this class, you should also override the {@link Model#getMojo()} method in your model's class to
 * return an instance of your new child class.
 *
 * @param <M> model class that your ModelMojoWriter serializes
 * @param <P> model parameters class that corresponds to your model
 * @param <O> model output class that corresponds to your model
 */
public abstract class ModelMojoWriter<M extends Model<M, P, O>, P extends Model.Parameters, O extends Model.Output>
        extends StreamWriter
{
  protected M model;
  private StringBuilder tmpfile;
  private String tmpname;
  private ZipOutputStream zos;
  // When this flag is set to true, `writeln()` is disallowed and `writekv()` is allowed
  private boolean writingIniFile = false;


  //--------------------------------------------------------------------------------------------------------------------
  // Inheritance interface: ModelMojoWriter subclasses are expected to override these methods to provide custom behavior
  //--------------------------------------------------------------------------------------------------------------------

  public ModelMojoWriter(M model) {
    this.model = model;
  }

  /** Overwrite in subclasses to write any additional information into the model.ini/[info] section. */
  protected abstract void writeExtraModelInfo() throws IOException;

  /** Overwrite in subclasses to write the actual model data. */
  protected abstract void writeModelData() throws IOException;


  //--------------------------------------------------------------------------------------------------------------------
  // Utility functions: subclasses should use these to implement the behavior they need
  //--------------------------------------------------------------------------------------------------------------------

  protected final void writekv(String key, Object value) {
    assert writingIniFile : "This function should only be used from `writeExtraModelInfo`";
    String valStr = value == null? "null" : value.toString();
    if (valStr.contains("\n"))
      throw new RuntimeException("The `value` must not contain newline characters, got: " + valStr);
    tmpfile.append(key).append(" = ").append(valStr).append('\n');
  }

  protected final void startWritingTextFile(String filename) {
    assert tmpfile == null : "Previous text file was not closed";
    assert !writingIniFile;
    tmpfile = new StringBuilder();
    tmpname = filename;
  }

  protected final void writeln(String s) {
    assert tmpfile != null : "No text file is currently being written";
    assert !writingIniFile : "Please use `writekv(key, value)` instead";
    tmpfile.append(s);
    tmpfile.append('\n');
  }

  protected final void finishWritingTextFile() throws IOException {
    writeBinaryFile(tmpname, tmpfile.toString().getBytes(Charset.forName("UTF-8")));
    tmpfile = null;
  }

  protected final void writeBinaryFile(String filename, byte[] bytes) throws IOException {
    ZipEntry archiveEntry = new ZipEntry(filename);
    archiveEntry.setSize(bytes.length);
    zos.putNextEntry(archiveEntry);
    zos.write(bytes);
    zos.closeEntry();
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Used from `ModelsHandler.fetchMojo()` to serialize the Mojo into a StreamingSchema.
   * The structure of the zip will be
   * as follows:
   *    domains/
   *        d000.txt
   *        d001.txt
   *        ...
   *    trees/
   *        t00_000.bin
   *        ...
   *    model.ini
   * Each domain file is a plain text file with one line per category (not quoted).
   * Each tree file is a binary file that is equivalent to `_bit` array in the model's `score()` function. The first 2
   * digits in the tree file's name correspond to the class index, the last tree are the tree index (since trees are
   * stored in a double-array Key&lt;CompressedTree>[ntrees][nclasses].
   */
  @Override public final void writeTo(OutputStream os) {
    zos = new ZipOutputStream(os);
    try {
      writeModelInfo();
      writeDomains();
      writeModelData();
      zos.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Create the model.ini file containing the generic model parameters.
   *
   * The model.ini file has 3 sections: [info], [columns] and [domains]. For example:
   *    [info]
   *    algo = Random Forest
   *    n_trees = 100
   *    n_columns = 25
   *    n_domains = 3
   *    ...
   *    h2o_version = 3.9.10.0
   *
   *    [columns]
   *    col1
   *    col2
   *    ...
   *
   *    [domains]
   *    5: 13 d000.txt
   *    6: 7 d001.txt
   *    12: 124 d002.txt
   *
   * Here the [info] section lists general model information; [columns] is the list of all column names in the input
   * dataframe; and [domains] section maps column numbers (for categorical features) to their domain definition files
   * together with the number of categories to be read from that file.
   */
  private void writeModelInfo() throws IOException {
    int n_categoricals = 0;
    for (String[] domain : model._output._domains)
      if (domain != null)
        n_categoricals++;

    startWritingTextFile("model.ini");
    writeln("[info]");
    writingIniFile = true;
    writekv("algorithm", model._parms.fullName());
    writekv("category", model._output.getModelCategory());
    writekv("uuid", model.checksum());
    writekv("supervised", model._output.isSupervised());
    writekv("n_features", model._output.nfeatures());
    writekv("n_classes", model._output.nclasses());
    writekv("n_columns", model._output._names.length);
    writekv("n_domains", n_categoricals);
    writekv("balance_classes", model._parms._balance_classes);
    writekv("default_threshold", model.defaultThreshold());
    writekv("prior_class_distrib", Arrays.toString(model._output._priorClassDist));
    writekv("model_class_distrib", Arrays.toString(model._output._modelClassDist));
    writeExtraModelInfo();
    writekv("timestamp", new DateTime().toString());
    writekv("h2o_version", H2O.ABV.projectVersion());
    writekv("mojo_version", "1.0");
    writekv("license", "Apache License Version 2.0");
    writingIniFile = false;
    writeln("");
    writeln("[columns]");
    for (String name : model._output._names) {
      writeln(name);
    }
    writeln("");
    writeln("[domains]");
    String format = "%d: %d d%03d.txt";
    int domIndex = 0;
    for (int colIndex = 0; colIndex < model._output._names.length; colIndex++) {
      if (model._output._domains[colIndex] != null)
        writeln(String.format(format, colIndex, model._output._domains[colIndex].length, domIndex++));
    }
    finishWritingTextFile();
  }

  /** Create files containing domain definitions for each categorical column. */
  private void writeDomains() throws IOException {
    int domIndex = 0;
    for (String[] domain : model._output._domains) {
      if (domain == null) continue;
      startWritingTextFile(String.format("domains/d%03d.txt", domIndex++));
      for (String category : domain) {
        writeln(category.replaceAll("\n", "\\n"));  // replace newlines with "\n" escape sequences
      }
      finishWritingTextFile();
    }
  }
}
