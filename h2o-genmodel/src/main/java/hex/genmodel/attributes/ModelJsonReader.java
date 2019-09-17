package hex.genmodel.attributes;

import com.google.gson.*;
import hex.genmodel.*;
import hex.genmodel.attributes.metrics.SerializedName;

import java.io.BufferedReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Utility class for extracting model details from JSON
 */
public class ModelJsonReader {

    public static final String MODEL_DETAILS_FILE = "experimental/modelDetails.json";

    /**
     * @param mojoReaderBackend
     * @return {@link JsonObject} representing the deserialized Json.
     */
    public static JsonObject parseModelJson(final MojoReaderBackend mojoReaderBackend) {

        try (BufferedReader fileReader = mojoReaderBackend.getTextFile(MODEL_DETAILS_FILE)) {
            final Gson gson = new GsonBuilder().create();

            return gson.fromJson(fileReader, JsonObject.class);
        } catch (Exception e){
            return null;
        }
    }

    /**
     * Extracts a Table from H2O's model serialized into JSON.
     *
     * @param modelJson Full JSON representation of a model
     * @param tablePath Path in the given JSON to the desired table. Levels are dot-separated.
     * @return An instance of {@link Table}, if there was a table found by following the given path. Otherwise null.
     */
    public static Table readTable(final JsonObject modelJson, final String tablePath) {
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
        final String[] columnFormats;
        final Table.ColumnType[] columnTypes;
        final Object[][] data;


        // Extract column attributes
        final JsonArray columns = findInJson(tableJson, "columns").getAsJsonArray();
        final int columnCount = columns.size();
        columnHeaders = new String[columnCount];
        columnTypes = new Table.ColumnType[columnCount];
        columnFormats = new String[columnCount];

        for (int i = 0; i < columnCount; i++) {
            final JsonObject column = columns.get(i).getAsJsonObject();
            columnHeaders[i] = column.get("description").getAsString();
            columnTypes[i] = Table.ColumnType.extractType(column.get("type").getAsString());
            columnFormats[i] = column.get("format").getAsString();
        }


        // Extract data
        JsonArray dataColumns = findInJson(tableJson, "data").getAsJsonArray();
        data = new Object[columnCount][rowCount];
        for (int i = 0; i < columnCount; i++) {
            JsonArray column = dataColumns.get(i).getAsJsonArray();
            for (int j = 0; j < rowCount; j++) {
                final JsonElement cellValue = column.get(j);
                if (cellValue == null || !cellValue.isJsonPrimitive()) {
                    data[i][j] = null;
                    continue;
                }
                JsonPrimitive primitiveValue = cellValue.getAsJsonPrimitive();


                switch (columnTypes[i]) {
                    case LONG:
                        if (primitiveValue.isNumber()) {
                            data[i][j] = primitiveValue.getAsLong();
                        } else {
                            data[i][j] = null;
                        }
                        break;
                    case DOUBLE:
                        if (!primitiveValue.isJsonNull()) { // isNumber skips NaNs
                            data[i][j] = primitiveValue.getAsDouble();
                        } else {
                            data[i][j] = null;
                        }
                        break;
                    case FLOAT:
                        if (!primitiveValue.isJsonNull()) { // isNumber skips NaNs
                            data[i][j] = primitiveValue.getAsFloat();
                        } else {
                            data[i][j] = null;
                        }
                    case INT:
                        if (primitiveValue.isNumber()) {
                            data[i][j] = primitiveValue.getAsInt();
                        } else {
                            data[i][j] = null;
                        }
                    case STRING:
                        data[i][j] = primitiveValue.getAsString();
                        break;
                }

            }
        }
    
        return new Table(tableJson.get("name").getAsString(), tableJson.get("description").getAsString(),
                new String[rowCount], columnHeaders, columnTypes, null, columnFormats, data);
    }

    public static void fillObject(final Object object, final JsonElement from, final String elementPath) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(elementPath);

        final JsonElement jsonSourceObject = findInJson(from, elementPath);

        if (jsonSourceObject instanceof JsonNull) {
            System.out.println(String.format("Element '%s' not found in JSON. Skipping. Object '%s' is not populated by values.",
                    elementPath, object.getClass().getName()));
            return;
        }

        final JsonObject jsonSourceObj = jsonSourceObject.getAsJsonObject();

        final Class<?> aClass = object.getClass();
        final Field[] declaredFields = aClass.getFields();

        for (int i = 0; i < declaredFields.length; i++) {
            Field field = declaredFields[i];
            if (Modifier.isTransient(field.getModifiers())) continue;

            final Class<?> type = field.getType();
            final SerializedName serializedName = field.getAnnotation(SerializedName.class);
            final String fieldName;
            if (serializedName == null) {
                String name = field.getName();
                fieldName = name.charAt(0) == '_' ? name.substring(1) : name;
            } else {
                fieldName = serializedName.value();
            }

            try {
                field.setAccessible(true);
                assert field.isAccessible();
                Object value = null;
                if (type.isAssignableFrom(double.class) || type.isAssignableFrom(Double.class)) {
                    final JsonElement jsonElement = jsonSourceObj.get(fieldName);
                    if (jsonElement != null && !jsonElement.isJsonNull()) value = jsonElement.getAsDouble();
                } else if (type.isAssignableFrom(int.class) || type.isAssignableFrom(Integer.class)) {
                    final JsonElement jsonElement = jsonSourceObj.get(fieldName);
                    if (jsonElement != null && !jsonElement.isJsonNull()) value = jsonElement.getAsInt();
                } else if (type.isAssignableFrom(long.class) || type.isAssignableFrom(Long.class)) {
                    final JsonElement jsonElement = jsonSourceObj.get(fieldName);
                    if (jsonElement != null && !jsonElement.isJsonNull()) value = jsonElement.getAsLong();
                } else if (type.isAssignableFrom(String.class)) {
                    final JsonElement jsonElement = jsonSourceObj.get(fieldName);
                    if (jsonElement != null && !jsonElement.isJsonNull()) value = jsonElement.getAsString();
                } else if (type.isAssignableFrom(Table.class)) {
                    final JsonElement jsonElement = jsonSourceObj.get(fieldName);
                    if (jsonElement != null && !jsonElement.isJsonNull()) value = readTable(jsonElement.getAsJsonObject(),  serializedName != null ? serializedName.insideElementPath() : "");
                }
                if (value != null) field.set(object, value);
            } catch (IllegalAccessException e) {
                System.out.println(String.format("Field '%s' could not be accessed. Ignoring.", fieldName));
            } catch (ClassCastException | UnsupportedOperationException e) {
                System.out.println(String.format("Field '%s' could not be casted to '%s'. Ignoring.", fieldName, type.toString()));
            }
        }


    }


    private static final Pattern JSON_PATH_PATTERN = Pattern.compile("\\.|\\[|\\]");

    /**
     * Finds an element in GSON's JSON document representation
     *
     * @param jsonElement A (potentially complex) element to search in
     * @param jsonPath    Path in the given JSON to the desired table. Levels are dot-separated.
     *                    E.g. 'model._output.variable_importances'.
     * @return JsonElement, if found. Otherwise {@link JsonNull}.
     */
    private static JsonElement findInJson(final JsonElement jsonElement, final String jsonPath) {

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
     *
     * @param jsonElement A (potentially complex) element to search in
     * @param jsonPath    Path in the given JSON to the desired table. Levels are dot-separated.
     *                    E.g. 'model._output.variable_importances'.
     * @return True if the element exists under the given path in the target JSON, otherwise false
     */
    public static boolean elementExists(JsonElement jsonElement, String jsonPath){
        final boolean isEmpty = findInJson(jsonElement, jsonPath) instanceof JsonNull;
        return !isEmpty;
    }
}
