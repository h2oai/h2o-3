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

public abstract class Writer {

  private String targetdir;
  private StringBuilder tmpfile;
  private String tmpname;
  private ZipOutputStream zos;
  // Local key-value store: these values will be written to the model.ini/[info] section
  protected Map<String, String> lkv;

  //--------------------------------------------------------------------------------------------------------------------
  // Inheritance interface: ModelMojoWriter subclasses are expected to override these methods to provide custom behavior
  //--------------------------------------------------------------------------------------------------------------------

  public Writer() {
    this.lkv = new LinkedHashMap<>(20);  // Linked so as to preserve the order of entries in the output
  }

  public abstract String version();

  protected abstract void writeData() throws IOException;


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

  protected void writelnkv(String key, String value) {
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

  public void writeTo(ZipOutputStream zos, String zipDirectory) throws IOException {
    initWriting(zos, zipDirectory);
    addCommonWriterInfo();
  }

  
  protected void addCommonWriterInfo() throws IOException {
    // nothing by default
  }

  protected void initWriting(ZipOutputStream zos, String targetdir) {
    this.zos = zos;
    this.targetdir = targetdir;
  }

  private static byte[] toBytes(Object value) {
    return String.valueOf(value).getBytes(Charset.forName("UTF-8"));
  }

}
