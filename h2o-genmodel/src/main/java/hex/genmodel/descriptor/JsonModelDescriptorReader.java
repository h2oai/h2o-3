package hex.genmodel.descriptor;

import com.google.gson.*;
import hex.ModelCategory;
import hex.genmodel.*;
import hex.genmodel.algos.tree.SharedTreeModelJsonDescriptorReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 */
public abstract class JsonModelDescriptorReader implements ModelDescriptorReader {

    private final MojoReaderBackend _mojoReaderBackend;
    protected final MojoModel _mojoModel;

    public JsonModelDescriptorReader(MojoReaderBackend mojoReaderBackend, MojoModel mojoModel) {
        this._mojoReaderBackend = mojoReaderBackend;
        this._mojoModel = mojoModel;
    }

    public static JsonModelDescriptorReader get(final MojoReaderBackend mojoReaderBackend) {
        try {
            final MojoModel mojoModel = ModelMojoReader.readFrom(mojoReaderBackend, false);
            final String algoName = mojoModel.algoName;

            switch (algoName) {
                case "Distributed Random Forest":
                case "Gradient Boosting Method":
                case "Gradient Boosting Machine":
                case "Isolation Forest":
                    return new SharedTreeModelJsonDescriptorReader(mojoReaderBackend, mojoModel);
                default:
                    return new JsonModelDescriptorReader(mojoReaderBackend, mojoModel) {
                        @Override
                        protected void readModelSpecificDescription(JsonObject descriptionJson, ModelDescriptorBuilder modelDescriptorBuilder) {
                            // Do nothing on purpose, the general implementation only fills basic model information
                        }
                    };
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }
    
    @Override
    public ModelDescriptor getDescription() {
        // MojoModel is created and as soon as this method is over, it is left for garbage collection,
        // as the lifetime of ModelDescriptor might be completely different than any directly used MojoModel.
        final ModelDescriptorBuilder modelDescriptorBuilder = new ModelDescriptorBuilder(_mojoModel);
        final JsonObject descriptionJson = parseJson();

        readGeneralModelDescription(descriptionJson, modelDescriptorBuilder);
        readModelSpecificDescription(descriptionJson, modelDescriptorBuilder);

        return modelDescriptorBuilder.build();
    }

    private void readGeneralModelDescription(final JsonObject modelJson, final ModelDescriptorBuilder modelDescriptorBuilder) {
        final Table model_summary_table = extractTableFromJson(modelJson, "output.model_summary");
        modelDescriptorBuilder.modelSummary(model_summary_table);
    }

    protected abstract void readModelSpecificDescription(final JsonObject descriptionJson, final ModelDescriptorBuilder modelDescriptorBuilder);

    public static final String MODEL_DETAILS_FILE = "experimental/modelDetails.json";

    protected JsonObject parseJson() {

        try (BufferedReader fileReader = _mojoReaderBackend.getTextFile(MODEL_DETAILS_FILE)) {
            final Gson gson = new GsonBuilder().create();

            return gson.fromJson(fileReader, JsonObject.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file inside MOJO " + MODEL_DETAILS_FILE, e);
        }
    }


    /**
     * Extracts a Table from H2O's model serialized into JSON.
     *
     * @param modelJson Full JSON representation of a model
     * @param tablePath Path in the given JSON to the desired table. Levels are dot-separated.
     * @return An instance of {@link Table}, if there was a table found by following the given path. Otherwise null.
     */
    protected Table extractTableFromJson(final JsonObject modelJson, final String tablePath) {
        Objects.requireNonNull(modelJson);
        JsonElement potentialTableJson = findInJson(modelJson, tablePath);
        if (potentialTableJson.isJsonNull()) {
            System.out.println(String.format("Failed to extract element '%s' MojoModel dump.",
                    tablePath));
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

                switch (columnTypes[i]) {
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
                new String[rowCount], columnHeaders, columnTypes, "", data);
    }


    private static final Pattern JSON_PATH_PATTERN = Pattern.compile("\\.|\\[|\\]");

    /**
     * Finds ane lement in GSON's JSON document representation
     *
     * @param jsonElement A (potentially complex) element to search in
     * @param jsonPath    Path in the given JSON to the desired table. Levels are dot-separated.
     *                    E.g. 'model._output.variable_importances'.
     * @return JsonElement, if found. Otherwise {@link JsonNull}.
     */
    private static JsonElement findInJson(JsonElement jsonElement, String jsonPath) {

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

    /**
     * Helper class for building a new instance of {@link ModelDescriptor}.
     * It enforces presence of basic model information.
     */
    protected class ModelDescriptorBuilder {
        // Mandatory
        private final String _h2oVersion;
        private final hex.ModelCategory _category;
        private final String _uuid;
        private final boolean _supervised;
        private final int _nfeatures;
        private final int _nclasses;
        private final boolean _balanceClasses;
        private final double _defaultThreshold;
        private final double[] _priorClassDistrib;
        private final double[] _modelClassDistrib;
        private final String _offsetColumn;
        private final String[][] _domains;
        private final String[] _names;
        private final String _algoName;

        // Optional
        private VariableImportances _variableImportances = null;
        private Table _modelSummary = null;

        public ModelDescriptorBuilder(final MojoModel mojoModel) {
            _category = mojoModel._category;
            _uuid = mojoModel._uuid;
            _supervised = mojoModel.isSupervised();
            _nfeatures = mojoModel.nfeatures();
            _nclasses = mojoModel._nclasses;
            _balanceClasses = mojoModel._balanceClasses;
            _defaultThreshold = mojoModel._defaultThreshold;
            _priorClassDistrib = mojoModel._priorClassDistrib;
            _modelClassDistrib = mojoModel._modelClassDistrib;
            _h2oVersion = mojoModel._h2oVersion;
            _offsetColumn = mojoModel._offsetColumn;
            _domains = mojoModel._domains;
            _names = mojoModel._names;
            _algoName = mojoModel.getClass().getName();
        }

        public ModelDescriptorBuilder variableImportances(VariableImportances variableImportances) {
            _variableImportances = variableImportances;
            return this;
        }

        public ModelDescriptorBuilder modelSummary(Table modelSummary) {
            _modelSummary = modelSummary;
            return this;
        }

        /**
         * Builds the final instance of {@link ModelDescriptor}, using information provided by the serialized model and
         * which corresponding implementations of {@link ModelDescriptorReader} are able to provide.
         *
         * @return A new instance of {@link ModelDescriptor}
         */
        public ModelDescriptor build() {
            return new ModelDescriptor() {
                @Override
                public String[][] scoringDomains() {
                    return _domains;
                }

                @Override
                public String projectVersion() {
                    return _h2oVersion;
                }

                @Override
                public String algoName() {
                    return _algoName;
                }

                @Override
                public String algoFullName() {
                    return _algoName;
                }

                @Override
                public String offsetColumn() {
                    return _offsetColumn;
                }

                @Override
                public String weightsColumn() {
                    return null;
                }

                @Override
                public String foldColumn() {
                    return null;
                }

                @Override
                public ModelCategory getModelCategory() {
                    return _category;
                }

                @Override
                public boolean isSupervised() {
                    return _supervised;
                }

                @Override
                public int nfeatures() {
                    return _nfeatures;
                }

                @Override
                public int nclasses() {
                    return _nclasses;
                }

                @Override
                public String[] columnNames() {
                    return _names;
                }

                @Override
                public boolean balanceClasses() {
                    return _balanceClasses;
                }

                @Override
                public double defaultThreshold() {
                    return _defaultThreshold;
                }

                @Override
                public double[] priorClassDist() {
                    return _priorClassDistrib;
                }

                @Override
                public double[] modelClassDist() {
                    return _modelClassDistrib;
                }

                @Override
                public String uuid() {
                    return _uuid;
                }

                @Override
                public String timestamp() {
                    return null;
                }

                @Override
                public VariableImportances variableImportances() {
                    return _variableImportances;
                }

                @Override
                public Table modelSummary() {
                    return _modelSummary;
                }
            };
        }
    }
}
