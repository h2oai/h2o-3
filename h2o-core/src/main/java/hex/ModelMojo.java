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
 * Serialize the model into a zipped file containing multiple raw data files. The structure of the zip will be
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
 *
 * The model.ini file has 3 sections: [info], [columns] and [domains]:
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
 *    5: d000.txt
 *    6: d001.txt
 *    12: d002.txt
 *
 * The [info] section lists general model information; [columns] contains the list of all column names; and [domains]
 *
 */
public class ModelMojo<M extends Model<M, P, O>, P extends Model.Parameters, O extends Model.Output>
        extends StreamWriter {
  protected M model;
  private StringBuilder tmpfile;
  private String tmpname;
  private ZipOutputStream zos;


  //--------------------------------------------------------------------------------------------------------------------
  // Inheritance interface: ModelMojo subclasses are expected to override these methods to provide custom behavior
  //--------------------------------------------------------------------------------------------------------------------

  public ModelMojo(M model) {
    this.model = model;
  }

  /** Overwrite in subclasses to write any additional information into the model.ini/[info] section. */
  protected void writeExtraModelInfo() throws IOException {}

  /** Overwrite in subclasses to write the actual model data. */
  protected void writeModelData() throws IOException {}


  //--------------------------------------------------------------------------------------------------------------------
  // Utility functions: subclasses should use these to implement the behavior they need
  //--------------------------------------------------------------------------------------------------------------------

  protected final void startWritingTextFile(String filename) {
    assert tmpfile == null : "Previous text file was not closed";
    tmpfile = new StringBuilder();
    tmpname = filename;
  }

  protected final void writeln(String s) {
    assert tmpfile != null : "No text file is currently being written";
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

  /** Used from `ModelsHandler.fetchMojo()` to serialize the Mojo into a StreamingSchema. */
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

  /** Create the model.ini file containing the generic model parameters. */
  private void writeModelInfo() throws IOException {
    int n_categoricals = 0;
    for (String[] domain : model._output._domains)
      if (domain != null)
        n_categoricals++;

    startWritingTextFile("model.ini");
    writeln("[info]");
    writeln("algorithm = " + model._parms.fullName());
    writeln("category = " + model._output.getModelCategory());
    writeln("uuid = " + model.checksum());
    writeln("supervised = " + (model._output.isSupervised() ? "true" : "false"));
    writeln("n_features = " + model._output.nfeatures());
    writeln("n_classes = " + model._output.nclasses());
    writeln("n_columns = " + model._output._names.length);
    writeln("n_domains = " + n_categoricals);
    writeln("balance_classes = " + model._parms._balance_classes);
    writeln("default_threshold = " + model.defaultThreshold());
    writeln("prior_class_distrib = " + Arrays.toString(model._output._priorClassDist));
    writeln("model_class_distrib = " + Arrays.toString(model._output._modelClassDist));
    writeExtraModelInfo();
    writeln("timestamp = " + new DateTime().toString());
    writeln("h2o_version = " + H2O.ABV.projectVersion());
    writeln("mojo_version = 1.0");
    writeln("license = Apache License Version 2.0");
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
