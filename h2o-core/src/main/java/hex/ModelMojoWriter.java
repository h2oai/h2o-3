package hex;

import hex.genmodel.utils.StringEscapeUtils;
import org.joda.time.DateTime;
import water.H2O;
import water.api.SchemaServer;
import water.api.StreamWriter;
import water.api.schemas3.ModelSchemaV3;
import water.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
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
        extends StreamWriter
{
  /** Reference to the model being written. Use this in the subclasses to retreive information from your model. */
  protected M model;

  private String targetdir;
  private StringBuilder tmpfile;
  private String tmpname;
  private ZipOutputStream zos;
  // Local key-value store: these values will be written to the model.ini/[info] section
  private Map<String, String> lkv;


  //--------------------------------------------------------------------------------------------------------------------
  // Inheritance interface: ModelMojoWriter subclasses are expected to override these methods to provide custom behavior
  //--------------------------------------------------------------------------------------------------------------------

  public ModelMojoWriter() {}

  public ModelMojoWriter(M model) {
    this.model = model;
    this.lkv = new LinkedHashMap<>(20);  // Linked so as to preserve the order of entries in the output
  }

  /**
   * Version of the mojo file produced. Follows the <code>major.minor</code>
   * format, where <code>minor</code> is a 2-digit number. For example "1.00",
   * "2.05", "2.13". See README in mojoland repository for more details.
   */
  public abstract String mojoVersion();

  /** Override in subclasses to write the actual model data. */
  protected abstract void writeModelData() throws IOException;


  //--------------------------------------------------------------------------------------------------------------------
  // Utility functions: subclasses should use these to implement the behavior they need
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Write a simple value to the model.ini/[info] section. Here "simple" means a value that can be stringified with
   * .toString(), and its stringified version does not span multiple lines.
   */
  protected final void writekv(String key, Object value) throws IOException {
    String valStr = value == null? "null" : value.toString();
    if (valStr.contains("\n"))
      throw new IOException("The `value` must not contain newline characters, got: " + valStr);
    if (lkv.containsKey(key))
      throw new IOException("Key " + key + " was already written");
    lkv.put(key, valStr);
  }
  protected final void writekv(String key, int[] value) throws IOException {
    writekv(key, Arrays.toString(value));
  }
  protected final void writekv(String key, double[] value) throws IOException {
    writekv(key, Arrays.toString(value));
  }

  /** Write a binary file to the MOJO archive. */
  protected final void writeblob(String filename, byte[] blob) throws IOException {
    ZipEntry archiveEntry = new ZipEntry(targetdir + filename);
    archiveEntry.setSize(blob.length);
    zos.putNextEntry(archiveEntry);
    zos.write(blob);
    zos.closeEntry();
  }

  /** Write a text file to the MOJO archive (or rather open such file for writing). */
  protected final void startWritingTextFile(String filename) {
    assert tmpfile == null : "Previous text file was not closed";
    tmpfile = new StringBuilder();
    tmpname = filename;
  }

  /** Write a single line of text to a previously opened text file, escape new line characters if enabled. */
  protected final void writeln(String s, boolean escapeNewlines) {
    assert tmpfile != null : "No text file is currently being written";
    tmpfile.append(escapeNewlines ? StringEscapeUtils.escapeNewlines(s) : s);
    tmpfile.append('\n');
  }

  /** Write a single line of text to a previously opened text file. */
  protected final void writeln(String s) {
    writeln(s, false);
  }

  /** Finish writing a text file. */
  protected final void finishWritingTextFile() throws IOException {
    assert tmpfile != null : "No text file is currently being written";
    writeblob(tmpname, StringUtils.toBytes(tmpfile));
    tmpfile = null;
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
  @Override public final void writeTo(OutputStream os) {
    ZipOutputStream zos = new ZipOutputStream(os);
    try {
      writeTo(zos);
      zos.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void writeTo(ZipOutputStream zos) throws IOException {
    writeTo(zos, "");
  }

  public final void writeTo(ZipOutputStream zos, String zipDirectory) throws IOException {
    initWriting(zos, zipDirectory);
    addCommonModelInfo();
    writeModelData();
    writeModelInfo();
    writeDomains();
    writeModelDetails();
    writeModelDetailsReadme();
  }

  private void initWriting(ZipOutputStream zos, String targetdir) {
    this.zos = zos;
    this.targetdir = targetdir;
  }

  private void addCommonModelInfo() throws IOException {
    int n_categoricals = 0;
    for (String[] domain : model.scoringDomains())
      if (domain != null)
        n_categoricals++;

    writekv("h2o_version", H2O.ABV.projectVersion());
    writekv("mojo_version", mojoVersion());
    writekv("license", "Apache License Version 2.0");
    writekv("algo", model._parms.algoName().toLowerCase());
    writekv("algorithm", model._parms.fullName());
    writekv("endianness", ByteOrder.nativeOrder());
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
    writekv("timestamp", new DateTime().toString());
  }

  /**
   * Create the model.ini file containing 3 sections: [info], [columns] and [domains]. For example:
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
    startWritingTextFile("model.ini");
    writeln("[info]");
    for (Map.Entry<String, String> kv : lkv.entrySet()) {
      writeln(kv.getKey() + " = " + kv.getValue());
    }

    writeln("\n[columns]");
    for (String name : model._output._names) {
      writeln(name);
    }

    writeln("\n[domains]");
    String format = "%d: %d d%03d.txt";
    int domIndex = 0;
    String[][] domains = model.scoringDomains();
    for (int colIndex = 0; colIndex < domains.length; colIndex++) {
      if (domains[colIndex] != null)
        writeln(String.format(format, colIndex, domains[colIndex].length, domIndex++));
    }
    finishWritingTextFile();
  }

  /** Create files containing domain definitions for each categorical column. */
  private void writeDomains() throws IOException {
    int domIndex = 0;
    for (String[] domain : model.scoringDomains()) {
      if (domain == null) continue;
      startWritingTextFile(String.format("domains/d%03d.txt", domIndex++));
      for (String category : domain) {
        writeln(category.replaceAll("\n", "\\n"));  // replace newlines with "\n" escape sequences
      }
      finishWritingTextFile();
    }
  }

  /** Create file that contains model details in JSON format.
   * This information is pulled from the models schema.
   */
  private void writeModelDetails() throws IOException{
    ModelSchemaV3 modelSchema = (ModelSchemaV3) SchemaServer.schema(3, model).fillFromImpl(model);
    startWritingTextFile("experimental/modelDetails.json");
    writeln(modelSchema.toJsonString());
    finishWritingTextFile();
  }
  private void writeModelDetailsReadme() throws IOException{
    startWritingTextFile("experimental/README.md");
    writeln("Outputting model information in JSON is an experimental feature and we appreciate any feedback.\n" +
                "The contents of this folder may change with another version of H2O.");
    finishWritingTextFile();
  }
}
