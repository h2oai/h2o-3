package hex.genmodel;

import com.google.gson.*;
import hex.genmodel.descriptor.JsonModelDescriptorReader;
import hex.genmodel.descriptor.ModelDescriptorBuilder;
import hex.genmodel.descriptor.Table;
import hex.genmodel.utils.ParseUtils;
import hex.genmodel.utils.StringEscapeUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to deserialize a model from MOJO format. This is a counterpart to `ModelMojoWriter`.
 */
public abstract class ModelMojoReader<M extends MojoModel> {

  protected M _model;

  protected MojoReaderBackend _reader;
  private Map<String, Object> _lkv;


  /**
   * De-serializes a {@link MojoModel}, creating an instance of {@link MojoModel} useful for scoring
   * and model evaluation.
   *
   * @param reader An instance of {@link MojoReaderBackend} to read from existing MOJO. After the model is de-serialized,
   *               the {@link MojoReaderBackend} instance is automatically closed if it implements {@link Closeable}.
   * @return De-serialized {@link MojoModel}
   * @throws IOException Whenever there is an error reading the {@link MojoModel}'s data.
   */
  public static MojoModel readFrom(MojoReaderBackend reader) throws IOException {
    return readFrom(reader, false);
  }

  /**
   * De-serializes a {@link MojoModel}, creating an instance of {@link MojoModel} useful for scoring
   * and model evaluation.
   *
   * @param reader      An instance of {@link MojoReaderBackend} to read from existing MOJO
   * @param readModelDescriptor If true, 
   * @return De-serialized {@link MojoModel}
   * @throws IOException Whenever there is an error reading the {@link MojoModel}'s data.
   */
  public static MojoModel readFrom(MojoReaderBackend reader, final boolean readModelDescriptor) throws IOException {
    try {
      Map<String, Object> info = parseModelInfo(reader);
      if (! info.containsKey("algorithm"))
        throw new IllegalStateException("Unable to find information about the model's algorithm.");
      String algo = String.valueOf(info.get("algorithm"));
      ModelMojoReader mmr = ModelMojoFactory.INSTANCE.getMojoReader(algo);
      mmr._lkv = info;
      mmr._reader = reader;
      mmr.readAll(readModelDescriptor);
      return mmr._model;
    } finally {
      if (reader instanceof Closeable)
        ((Closeable) reader).close();
    }
  }

  public abstract String getModelName();

  //--------------------------------------------------------------------------------------------------------------------
  // Inheritance interface: ModelMojoWriter subclasses are expected to override these methods to provide custom behavior
  //--------------------------------------------------------------------------------------------------------------------

  protected abstract void readModelData() throws IOException;

  protected abstract M makeModel(String[] columns, String[][] domains, String responseColumn);

  /**
   * Maximal version of the mojo file current model reader supports. Follows the <code>major.minor</code>
   * format, where <code>minor</code> is a 2-digit number. For example "1.00",
   * "2.05", "2.13". See README in mojoland repository for more details.
   */
  public abstract String mojoVersion();


  //--------------------------------------------------------------------------------------------------------------------
  // Interface for subclasses
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Retrieve value from the model's kv store which was previously put there using `writekv(key, value)`. We will
   * attempt to cast to your expected type, but this is obviously unsafe. Note that the value is deserialized from
   * the underlying string representation using {@link ParseUtils#tryParse(String, Object)}, which occasionally may get
   * the answer wrong.
   * If the `key` is missing in the local kv store, null will be returned. However when assigning to a primitive type
   * this would result in an NPE, so beware.
   */
  @SuppressWarnings("unchecked")
  protected <T> T readkv(String key) {
    return (T) readkv(key, null);
  }

  /**
   * Retrieves the value associated with a given key. If value is not set of the key, a given default value is returned
   * instead. Uses same parsing logic as {@link ModelMojoReader#readkv(String)}. If default value is not null it's type
   * is used to assist the parser to determine the return type.
   * @param key name of the key
   * @param defVal default value
   * @param <T> return type
   * @return parsed value
   */
  @SuppressWarnings("unchecked")
  protected <T> T readkv(String key, T defVal) {
    Object val = _lkv.get(key);
    if (! (val instanceof RawValue))
      return val != null ? (T) val : defVal;
    return ((RawValue) val).parse(defVal);
  }

  /**
   * Retrieve binary data previously saved to the mojo file using `writeblob(key, blob)`.
   */
  protected byte[] readblob(String name) throws IOException {
    return _reader.getBinaryFile(name);
  }

  protected boolean exists(String name) {
    return _reader.exists(name);
  }

  /**
   * Retrieve text previously saved using `startWritingTextFile` + `writeln` as an array of lines. Each line is
   * trimmed to remove the leading and trailing whitespace.
   */
  protected Iterable<String> readtext(String name) throws IOException {
    return readtext(name, false);
  }

  /**
   * Retrieve text previously saved using `startWritingTextFile` + `writeln` as an array of lines. Each line is
   * trimmed to remove the leading and trailing whitespace. Removes escaping of the new line characters in enabled.
   */
  protected Iterable<String> readtext(String name, boolean unescapeNewlines) throws IOException {
    ArrayList<String> res = new ArrayList<>(50);
    BufferedReader br = _reader.getTextFile(name);
    try {
      String line;
      while (true) {
        line = br.readLine();
        if (line == null) break;
        if (unescapeNewlines)
          line = StringEscapeUtils.unescapeNewlines(line);
        res.add(line.trim());
      }
      br.close();
    } finally {
      try { br.close(); } catch (IOException e) { /* ignored */ }
    }
    return res;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  private void readAll(final boolean readModelDescriptor) throws IOException {
    String[] columns = (String[]) _lkv.get("[columns]");
    String[][] domains = parseModelDomains(columns.length);
    boolean isSupervised = readkv("supervised");
    _model = makeModel(columns, domains, isSupervised ? columns[columns.length - 1] : null);
    _model._uuid = readkv("uuid");
    _model._h2oVersion = readkv("h2o_version", "unknown");
    _model._category = hex.ModelCategory.valueOf((String) readkv("category"));
    _model._supervised = isSupervised;
    _model._nfeatures = readkv("n_features");
    _model._nclasses = readkv("n_classes");
    _model._balanceClasses = readkv("balance_classes");
    _model._defaultThreshold = readkv("default_threshold");
    _model._priorClassDistrib = readkv("prior_class_distrib");
    _model._modelClassDistrib = readkv("model_class_distrib");
    _model._offsetColumn = readkv("offset_column");
    _model._targetEncodingMap = parseTargetEncodingMap(_reader.getTextFile("feature_engineering/target_encoding.ini"));
    _model._mojo_version = ((Number) readkv("mojo_version")).doubleValue();
    checkMaxSupportedMojoVersion();
    readModelData();
    if (readModelDescriptor) {
      _model._modelDescriptor = readModelDescriptor();
    }
  }

  private ModelDescriptor readModelDescriptor() {
    final JsonObject modelJson = JsonModelDescriptorReader.parseModelJson(_reader);
    ModelDescriptorBuilder modelDescriptorBuilder = new ModelDescriptorBuilder(_model);

    final Table model_summary_table = JsonModelDescriptorReader.extractTableFromJson(modelJson, "output.model_summary");
    modelDescriptorBuilder.modelSummary(model_summary_table);

    readModelSpecificDescription(modelDescriptorBuilder, modelJson);
    return modelDescriptorBuilder.build();
  }

  protected void readModelSpecificDescription(final ModelDescriptorBuilder modelDescriptorBuilder, final JsonObject modelJson) {
    // Default behavior is empty on purpose
  }

  private static Map<String, Object> parseModelInfo(MojoReaderBackend reader) throws IOException {
    Map<String, Object> info = new HashMap<>();
    BufferedReader br = reader.getTextFile("model.ini");
    try {
      String line;
      int section = 0;
      int ic = 0;  // Index for `columns` array
      String[] columns = new String[0];  // array of column names, will be initialized later
      Map<Integer, String> domains = new HashMap<>();  // map of (categorical column index => name of the domain file)
      while (true) {
        line = br.readLine();
        if (line == null) break;
        line = line.trim();
        if (line.startsWith("#") || line.isEmpty()) continue;
        if (line.equals("[info]"))
          section = 1;
        else if (line.equals("[columns]")) {
          section = 2;  // Enter the [columns] section
          if (! info.containsKey("n_columns"))
            throw new IOException("`n_columns` variable is missing in the model info.");
          int n_columns = Integer.parseInt(((RawValue) info.get("n_columns"))._val);
          columns = new String[n_columns];
          info.put("[columns]", columns);
        } else if (line.equals("[domains]")) {
          section = 3; // Enter the [domains] section
          info.put("[domains]", domains);
        } else if (section == 1) {
          // [info] section: just parse key-value pairs and store them into the `info` map.
          String[] res = line.split("\\s*=\\s*", 2);
          info.put(res[0], res[0].equals("uuid")? res[1] : new RawValue(res[1]));
        } else if (section == 2) {
          // [columns] section
          if (ic >= columns.length)
            throw new IOException("`n_columns` variable is too small.");
          columns[ic++] = line;
        } else if (section == 3) {
          // [domains] section
          String[] res = line.split(":\\s*", 2);
          int col_index = Integer.parseInt(res[0]);
          domains.put(col_index, res[1]);
        }
      }
      br.close();
    } finally {
      try { br.close(); } catch (IOException e) { /* ignored */ }
    }
    return info;
  }

  private String[][] parseModelDomains(int n_columns) throws IOException {
    String[][] domains = new String[n_columns][];
    // noinspection unchecked
    Map<Integer, String> domass = (Map<Integer, String>) _lkv.get("[domains]");
    for (Map.Entry<Integer, String> e : domass.entrySet()) {
      int col_index = e.getKey();
      // There is a file with categories of the response column, but we ignore it.
      if (col_index >= n_columns) continue;
      String[] info = e.getValue().split(" ", 2);
      int n_elements = Integer.parseInt(info[0]);
      String domfile = info[1];
      String[] domain = new String[n_elements];
      BufferedReader br = _reader.getTextFile("domains/" + domfile);
      try {
        String line;
        int id = 0;  // domain elements counter
        while (true) {
          line = br.readLine();
          if (line == null) break;
          domain[id++] = line;
        }
        if (id != n_elements)
          throw new IOException("Not enough elements in the domain file");
        br.close();
      } finally {
        try { br.close(); } catch (IOException ioe) { /* ignored */ }
      }
      domains[col_index] = domain;
    }
    return domains;
  }

  Map<String, Map<String, int[]>> parseTargetEncodingMap(BufferedReader source) throws IOException {
    Map<String, Map<String, int[]>> encodingMap = new HashMap<>();
    Map<String, int[]> encodingsForColumn = null;
    String sectionName = null;
    try {
      String line;

      while (true) {
        line = source.readLine();
        if (line == null) { // EOF
          encodingMap.put(sectionName, encodingsForColumn);
          break;
        }
        line = line.trim();
        if(sectionName == null) {
          sectionName = matchNewSection(line);
          encodingsForColumn = new HashMap<>();
        } else { 
          String matchResult = matchNewSection(line);
          if(matchResult!= null) {
            encodingMap.put(sectionName, encodingsForColumn);
            encodingsForColumn = new HashMap<>();
            sectionName = matchResult;
            continue;
          }
          
          String[] res = line.split("\\s*=\\s*", 2);
          int[] numAndDenom = processNumeratorAndDenominator(res[1].split(" "));
          encodingsForColumn.put(res[0], numAndDenom);
        }
      }
      source.close();
    } finally {
      try {
        source.close();
      } catch (IOException e) { /* ignored */ }
    }
    return encodingMap;
  }

  private String matchNewSection(String line) {
    Pattern pattern = Pattern.compile("\\[(.*?)\\]");
    Matcher matcher = pattern.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    } else return null;
  }

  private int[] processNumeratorAndDenominator(String[] strings) {
    int[] intArray = new int[strings.length];
    int i = 0;
    for (String str : strings) {
      intArray[i] = Integer.parseInt(str);
      i++;
    }
    return intArray;
  }

  private static class RawValue {
    private final String _val;
    RawValue(String val) { _val = val; }
    @SuppressWarnings("unchecked")
    <T> T parse(T defVal) { return (T) ParseUtils.tryParse(_val, defVal); }
    @Override
    public String toString() { return _val; }
  }

  private void checkMaxSupportedMojoVersion() throws IOException {
    if(_model._mojo_version > Double.parseDouble(mojoVersion())){
      throw new IOException(String.format("MOJO version incompatibility - the model MOJO version (%.2f) is higher than the current h2o version (%s) supports. Please, use the older version of h2o to load MOJO model.", _model._mojo_version, mojoVersion()));
    }
  }
}
