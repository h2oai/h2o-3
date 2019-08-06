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
     *  Extracts model parameters from serialized into JSON H2O's model 
     * @param modelJson Full JSON representation of a model
     * @param parametersPath Path in the given JSON to the array of parameters
     * @return array of {@link ModelParameter} or null, if parameters were not found in JSON
     */
    public static ModelParameter[] readModelParameters(final JsonObject modelJson, final String parametersPath) {
        Objects.requireNonNull(modelJson);
        JsonElement modelParametersJsonElement = findInJson(modelJson, parametersPath);
        if (modelParametersJsonElement.isJsonNull()) {
            System.out.println(String.format("Failed to extract element '%s' MojoModel dump.", parametersPath));
            return null;
        }
        final JsonArray arrayOfParametersJson = modelParametersJsonElement.getAsJsonArray();
        ModelParameter[] modelParameters = new ModelParameter[arrayOfParametersJson.size()]; 
        int idx = 0;
        for(JsonElement parameter : arrayOfParametersJson) {
            ModelParameter mp = new ModelParameter();
            fillObject(mp, parameter, "");
            modelParameters[idx] = mp;
            idx++;
        }
        return modelParameters;
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

        JsonElement jsonSourceElement;
        if(elementPath.equals("")) {
            jsonSourceElement = from;
        } else {
            jsonSourceElement = findInJson(from, elementPath);

            if (jsonSourceElement instanceof JsonNull) {
                System.out.println(String.format("Element '%s' not found in JSON. Skipping. Object '%s' is not populated by values.",
                        elementPath, object.getClass().getName()));
                return;
            }
        }

        final JsonObject jsonSourceObj = jsonSourceElement.getAsJsonObject();

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
                final JsonElement jsonElement = jsonSourceObj.get(fieldName);
                boolean notNullCondition = jsonElement != null && !jsonElement.isJsonNull();
                if (type.isAssignableFrom(Object.class)) {
                    if (notNullCondition) {
                        try {
                            JsonElement typeJElement = jsonSourceObj.get("type");
                            if (typeJElement != null && !typeJElement.isJsonNull()) {
                                String typeOfObject = typeJElement.getAsString();

                                if (typeOfObject.equals("boolean")) {
                                    value = jsonElement.getAsBoolean();
                                } else if (typeOfObject.equals("int")) {
                                    value = jsonElement.getAsInt();
                                } else if (typeOfObject.equals("double")) {
                                    value = jsonElement.getAsDouble();
                                } else if (typeOfObject.equals("float")) {
                                    value = jsonElement.getAsFloat();
                                } else if (typeOfObject.equals("long")) {
                                    value = jsonElement.getAsLong();
                                } else if (typeOfObject.equals("enum")) {
                                    value = jsonElement.getAsString();
                                } else if (typeOfObject.startsWith("Key")) {
                                    value = readKey(jsonElement);
                                } else if (typeOfObject.equals("VecSpecifier")) {
                                    value = readVecSpecifier(jsonElement);
                                } else if (typeOfObject.equals("string[]")) {
                                    value = readStringArray(jsonElement);
                                } else {
                                    throw new IllegalStateException("Unhandled type encountered during parsing of the Json");
                                } 
                            }
                        } catch (Exception ex) {
                            System.out.println(String.format("Field '%s' could not be set as type " + jsonSourceObj.get("type") + ". Ignoring.", fieldName)); 
                        }
                    }
                } else if (type.isAssignableFrom(double.class) || type.isAssignableFrom(Double.class)) {
                    if (notNullCondition) value = jsonElement.getAsDouble();
                } else if (type.isAssignableFrom(int.class) || type.isAssignableFrom(Integer.class)) {
                    if (notNullCondition) value = jsonElement.getAsInt();
                } else if (type.isAssignableFrom(long.class) || type.isAssignableFrom(Long.class)) {
                    if (notNullCondition) value = jsonElement.getAsLong();
                } else if (type.isAssignableFrom(String.class)) {
                    if (notNullCondition) value = jsonElement.getAsString();
                } else if (type.isAssignableFrom(boolean.class) || type.isAssignableFrom(Boolean.class)) {
                    if (notNullCondition) value = jsonElement.getAsBoolean();
                } else if (type.isAssignableFrom(Table.class)) {
                    if (notNullCondition) value = readTable(jsonElement.getAsJsonObject(),  serializedName != null ? serializedName.insideElementPath() : ""); // Would break if we don't use serializedName
                }
                if (value != null) field.set(object, value);
            } catch (IllegalAccessException e) {
                System.out.println(String.format("Field '%s' could not be accessed. Ignoring.", fieldName));
            } catch (NumberFormatException e) {
                System.out.println(String.format("Field '%s' could not be set as NumberFormatException happened during extraction fromJSON. Ignoring.", fieldName));
            } catch (ClassCastException | UnsupportedOperationException e) {
                System.out.println(String.format("Field '%s' could not be casted to '%s'. Ignoring.", fieldName, type.toString()));
            }
        }


    }

    private static Key readKey(JsonElement jsonElement) {
        Key key = new Key();
        fillObject(key, jsonElement.getAsJsonObject(), "");
        return key;
    }
    
    private static VecSpecifier readVecSpecifier(JsonElement jsonElement) {
        VecSpecifier vecSpecifier = new VecSpecifier();
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        fillObject(vecSpecifier, jsonObject, "");
        vecSpecifier._is_member_of_frames = readStringArray(jsonObject.get("is_member_of_frames"));
        return vecSpecifier;
    }

    private static String[] readStringArray(JsonElement jsonElement) {
        if(jsonElement == null || jsonElement.isJsonNull()) {
            return null;
        } else {
            JsonArray is_member_of_frames = jsonElement.getAsJsonArray();
            String[] isMemberOfFrames = new String[is_member_of_frames.size()];
            int index = 0;
            for (JsonElement elem : is_member_of_frames) {
                isMemberOfFrames[index] = elem.getAsString();
                index++;
            }
            return isMemberOfFrames;
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
