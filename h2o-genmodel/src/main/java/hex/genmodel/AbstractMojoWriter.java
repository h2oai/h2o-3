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

public abstract class AbstractMojoWriter extends Writer{
  /**
   * Reference to the model being written. Use this in the subclasses to retreive information from your model.
   */
  private ModelDescriptor model;

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

  @Override
  public String version() {
    return mojoVersion();
  }

  @Override
  protected void writeData() throws IOException {
    writeModelData();
  }
  
  /**
   * Override in subclasses to write the actual model data.
   */
  protected abstract void writeModelData() throws IOException;

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

}
