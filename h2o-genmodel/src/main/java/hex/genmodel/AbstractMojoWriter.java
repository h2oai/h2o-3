package hex.genmodel;

import hex.genmodel.utils.StringEscapeUtils;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class AbstractMojoWriter {
  /**
   * Reference to the model being written. Use this in the subclasses to retreive information from your model.
   */
  private ModelDescriptor model;

  private String targetdir;
  private StringBuilder tmpfile;
  private String tmpname;
  private ZipOutputStream zos;
  // Local key-value store: these values will be written to the model.ini/[info] section
  private Map<String, String> lkv;


  //--------------------------------------------------------------------------------------------------------------------
  // Inheritance interface: ModelMojoWriter subclasses are expected to override these methods to provide custom behavior
  //--------------------------------------------------------------------------------------------------------------------

  public AbstractMojoWriter(ModelDescriptor model) {
    this.model = model;
    this.lkv = new LinkedHashMap<>(20);  // Linked so as to preserve the order of entries in the output
  }

  /**
   * Version of the mojo file produced. Follows the <code>major.minor</code>
   * format, where <code>minor</code> is a 2-digit number. For example "1.00",
   * "2.05", "2.13". See README in mojoland repository for more details.
   */
  public abstract String mojoVersion();

  /**
   * Override in subclasses to write the actual model data.
   */
  protected abstract void writeModelData() throws IOException;


  //--------------------------------------------------------------------------------------------------------------------
  // Utility functions: subclasses should use these to implement the behavior they need
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Write a simple value to the model.ini/[info] section. Here "simple" means a value that can be stringified with
   * .toString(), and its stringified version does not span multiple lines.
   */
  protected final void writekv(String key, Object value) throws IOException {
    String valStr = value == null ? "null" : value.toString();
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

  protected final void writekv(String key, float[] value) throws IOException {
    writekv(key, Arrays.toString(value));
  }

  /**
   * Write a binary file to the MOJO archive.
   */
  protected final void writeblob(String filename, byte[] blob) throws IOException {
    ZipEntry archiveEntry = new ZipEntry(targetdir + filename);
    archiveEntry.setSize(blob.length);
    zos.putNextEntry(archiveEntry);
    zos.write(blob);
    zos.closeEntry();
  }

  /**
   * Write a text file to the MOJO archive (or rather open such file for writing).
   */
  protected final void startWritingTextFile(String filename) {
    assert tmpfile == null : "Previous text file was not closed";
    tmpfile = new StringBuilder();
    tmpname = filename;
  }

  /**
   * Write a single line of text to a previously opened text file, escape new line characters if enabled.
   */
  protected final void writeln(String s, boolean escapeNewlines) {
    assert tmpfile != null : "No text file is currently being written";
    tmpfile.append(escapeNewlines ? StringEscapeUtils.escapeNewlines(s) : s);
    tmpfile.append('\n');
  }

  private void writelnkv(String key, String value, boolean escapeNewlines) {
    assert tmpfile != null : "No text file is currently being written";
    tmpfile.append(escapeNewlines ? StringEscapeUtils.escapeNewlines(key) : key);
    tmpfile.append(" = ");
    tmpfile.append(escapeNewlines ? StringEscapeUtils.escapeNewlines(value) : value);
    tmpfile.append('\n');
  }

  private void writelnkv(String key, String value) {
    writelnkv(key, value, false);
  }

  /**
   * Write a single line of text to a previously opened text file.
   */
  protected final void writeln(String s) {
    writeln(s, false);
  }

  /**
   * Finish writing a text file.
   */
  protected final void finishWritingTextFile() throws IOException {
    assert tmpfile != null : "No text file is currently being written";
    writeblob(tmpname, toBytes(tmpfile));
    tmpfile = null;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  protected void writeTo(ZipOutputStream zos) throws IOException {
    writeTo(zos, "");
  }

  public final void writeTo(ZipOutputStream zos, String zipDirectory) throws IOException {
    initWriting(zos, zipDirectory);
    addCommonModelInfo();
    writeModelData();
    writeModelInfo();
    writeDomains();
    writeExtraInfo();
  }

  protected void writeExtraInfo() throws IOException {
    // nothing by default
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

    writekv("h2o_version", model.projectVersion());
    writekv("mojo_version", mojoVersion());
    writekv("license", "Apache License Version 2.0");
    writekv("algo", model.algoName().toLowerCase());
    writekv("algorithm", model.algoFullName());
    writekv("endianness", ByteOrder.nativeOrder());
    writekv("category", model.getModelCategory());
    writekv("uuid", model.uuid());
    writekv("supervised", model.isSupervised());
    writekv("n_features", model.nfeatures());
    writekv("n_classes", model.nclasses());
    writekv("n_columns", model.columnNames().length);
    writekv("n_domains", n_categoricals);
    writekv("balance_classes", model.balanceClasses());
    writekv("default_threshold", model.defaultThreshold());
    writekv("prior_class_distrib", Arrays.toString(model.priorClassDist()));
    writekv("model_class_distrib", Arrays.toString(model.modelClassDist()));
    writekv("timestamp", model.timestamp());
  }

  /**
   * Create the model.ini file containing 3 sections: [info], [columns] and [domains]. For example:
   * [info]
   * algo = Random Forest
   * n_trees = 100
   * n_columns = 25
   * n_domains = 3
   * ...
   * h2o_version = 3.9.10.0
   * <p>
   * [columns]
   * col1
   * col2
   * ...
   * <p>
   * [domains]
   * 5: 13 d000.txt
   * 6: 7 d001.txt
   * 12: 124 d002.txt
   * <p>
   * Here the [info] section lists general model information; [columns] is the list of all column names in the input
   * dataframe; and [domains] section maps column numbers (for categorical features) to their domain definition files
   * together with the number of categories to be read from that file.
   */
  private void writeModelInfo() throws IOException {
    startWritingTextFile("model.ini");
    writeln("[info]");
    for (Map.Entry<String, String> kv : lkv.entrySet()) {
      writelnkv(kv.getKey(), kv.getValue());
    }

    writeln("\n[columns]");
    for (String name : model.columnNames()) {
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

  /**
   * Create files containing domain definitions for each categorical column.
   */
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

  private static byte[] toBytes(Object value) {
    return String.valueOf(value).getBytes(Charset.forName("UTF-8"));
  }

}
