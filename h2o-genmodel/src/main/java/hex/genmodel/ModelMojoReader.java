package hex.genmodel;

import com.google.gson.*;
import hex.genmodel.descriptor.Table;
import hex.genmodel.utils.ParseUtils;
import hex.genmodel.utils.StringEscapeUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;


/**
 * Helper class to deserialize a model from MOJO format. This is a counterpart to `ModelMojoWriter`.
 */
public abstract class ModelMojoReader<M extends MojoModel> {

  protected M _model;

  protected MojoReaderBackend _reader;
  private Map<String, Object> _lkv;


  public static MojoModel readFrom(MojoReaderBackend reader) throws IOException {
    try {
      Map<String, Object> info = parseModelInfo(reader);
      if (! info.containsKey("algorithm"))
        throw new IllegalStateException("Unable to find information about the model's algorithm.");
      String algo = String.valueOf(info.get("algorithm"));
      ModelMojoReader mmr = ModelMojoFactory.INSTANCE.getMojoReader(algo);
      mmr._lkv = info;
      mmr._reader = reader;
      mmr.readAll();
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

  private void readAll() throws IOException {
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
    _model._mojo_version = ((Number) readkv("mojo_version")).doubleValue();
    checkMaxSupportedMojoVersion();
    readModelData();
    extractModelDetails();
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

    public static final String MODEL_DETAILS_FILE = "experimental/modelDetails.json";

    protected JsonObject parseJson() {

        try (BufferedReader fileReader = _reader.getTextFile(MODEL_DETAILS_FILE)) {
            final Gson gson = new GsonBuilder().create();

          return gson.fromJson(fileReader, JsonObject.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file inside MOJO " + MODEL_DETAILS_FILE, e);
        }
    }

  protected Table extractTableFromJson(final JsonObject modelJson, final String tablePath) {
    Objects.requireNonNull(modelJson);
    JsonElement potentialTableJson = findInJson(modelJson, tablePath);
    if (potentialTableJson.isJsonNull()) {
      System.out.println(String.format("Failed to extract element '%s' MojoModel dump with ID '%s'.",
              tablePath, _model._uuid));
      return null;
    }
    final JsonObject tableJson = potentialTableJson.getAsJsonObject();
    final int rowCount = tableJson.get("rowcount").getAsInt();
    
    final String[] columnHeaders;
    final Table.ColumnType[] columnTypes;
    final Object[][] data;
    
    
    // Extract column attributes
    final JsonArray columns = findInJson(tableJson, "columns").getAsJsonArray();
    final int columnCount = columns.size();
    columnHeaders = new String[columnCount];
    columnTypes = new Table.ColumnType[columnCount];

    for (int i = 0; i < columnCount; i++) {
      final JsonObject column = columns.get(i).getAsJsonObject();
      columnHeaders[i] = column.get("description").getAsString();
      columnTypes[i] = Table.ColumnType.extractType(column.get("type").getAsString());
    }
    
    
    // Extract data
    JsonArray dataColumns = findInJson(tableJson, "data").getAsJsonArray();
    data = new Object[columnCount][rowCount];
    for (int i = 0; i < columnCount; i++) {
      JsonArray column = dataColumns.get(i).getAsJsonArray();
      for (int j = 0; j < rowCount; j++) {
        final JsonPrimitive primitiveValue = column.get(j).getAsJsonPrimitive();
       
        switch (columnTypes[i]){
          case LONG:
            data[i][j] = primitiveValue.getAsLong();
            break;
          case DOUBLE:
            data[i][j] = primitiveValue.getAsDouble();
            break;
          case STRING:
            data[i][j] = primitiveValue.getAsString();
            break;
        }
        
      }
    }
    
    return new Table(tableJson.get("name").getAsString(), tableJson.get("description").getAsString(),
            new String[rowCount], columnHeaders,columnTypes, "", data);
  }

  private void extractModelDetails() {
    // Extract additional information from model dump
    final JsonObject modelJson = parseJson();

    // First check the JSON dump is available
    if (modelJson == null) {
      System.out.println(String.format("Unable to parse JSON dump of MojoModel with ID '%s'. Additional model metrics were not extracted.",
              _model._uuid));
      return;
    }

    processModelMetrics(modelJson);
  }

  protected void processModelMetrics(JsonObject modelJson) {
    _model._model_summary = extractTableFromJson(modelJson, "output.model_summary");
  }

  private static final Pattern JSON_PATH_PATTERN = Pattern.compile("\\.|\\[|\\]");

  public static JsonElement findInJson(JsonElement jsonElement, String jsonPath) {

    final String[] route = JSON_PATH_PATTERN.split(jsonPath);
    JsonElement result = jsonElement;

    for (String key : route) {
      key = key.trim();
      if (key.isEmpty())
        continue;

      if (result == null) {
        result = JsonNull.INSTANCE;
        break;
      }

      if (result.isJsonObject()) {
        result = ((JsonObject) result).get(key);
      } else if (result.isJsonArray()) {
        int value = Integer.valueOf(key) - 1;
        result = ((JsonArray) result).get(value);
      } else break;
    }

    return result;
  }

}
