package hex.genmodel.attributes;

import com.google.gson.*;
import hex.genmodel.*;
import hex.genmodel.attributes.parameters.ColumnSpecifier;
import hex.genmodel.attributes.parameters.KeyValue;
import hex.genmodel.attributes.parameters.ParameterKey;
import water.logging.Logger;
import water.logging.LoggerFactory;

import java.io.BufferedReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for extracting model details from JSON
 */
public class ModelJsonReader {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModelJsonReader.class);

    public static final String MODEL_DETAILS_FILE = "experimental/modelDetails.json";

    /**
     * @param mojoReaderBackend
     * @return {@link JsonObject} representing serialized ModelV3 class.
     */
    public static JsonObject parseModelJson(final MojoReaderBackend mojoReaderBackend) {
        return parseModelJson(mojoReaderBackend, MODEL_DETAILS_FILE);
    }
    
    public static JsonObject parseModelJson(final MojoReaderBackend mojoReaderBackend, final String relativeFilePath) {

        try (BufferedReader fileReader = mojoReaderBackend.getTextFile(relativeFilePath)) {
            final Gson gson = new GsonBuilder().create();

            return gson.fromJson(fileReader, JsonObject.class);
        } catch (Exception e){
            return null;
        }
    }

    /**
     * Extracts a Table array from H2O's model serialized into JSON.
     *
     * @param modelJson Full JSON representation of a model
     * @param tablePath Path in the given JSON to the desired table array. Levels are dot-separated.
     * @return An instance of {@link Table} [], if there was a table array found by following the given path. Otherwise null.
     */
    public static Table[] readTableArray(final JsonObject modelJson, final String tablePath) {
        Table[] tableArray;
        Objects.requireNonNull(modelJson);
        JsonElement jsonElement = findInJson(modelJson, tablePath);
        if (jsonElement.isJsonNull())
            return null;
        JsonArray jsonArray = jsonElement.getAsJsonArray();
        tableArray = new Table[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            Table table = readTableJson(jsonArray.get(i).getAsJsonObject());
            tableArray[i] = table;
        }
        return tableArray;
    }
    
    private static Table readTableJson(JsonObject tableJson){
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
            LOG.debug(String.format("Table '%s' doesn't exist in MojoModel dump.", tablePath));
            return null;
        }
        return readTableJson(potentialTableJson.getAsJsonObject());
    }

    public static <T> void fillObjects(final List<T> objects, final JsonArray from) {
        for (int i = 0; i < from.size(); i++) {
            final JsonElement jsonElement = from.get(i);
            fillObject(objects.get(i), jsonElement, "");
        }
    }

    public static void fillObject(final Object object, final JsonElement from, final String elementPath) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(elementPath);

        final JsonElement jsonSourceObject = findInJson(from, elementPath);

        if (jsonSourceObject.isJsonNull()) {
            LOG.warn(String.format("Element '%s' not found in JSON. Skipping. Object '%s' is not populated by values.",
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
                if (type.isAssignableFrom(Object.class)) {
                    final JsonElement jsonElement = jsonSourceObj.get(fieldName);
                    if (jsonElement != null) {
                        // There might be a "type" element at the same leven in the JSON tree, serving as a hint. 
                        // Especially useful for numeric types.
                        final JsonElement typeElement = jsonSourceObj.get("type");
                        final TypeHint typeHint;
                        if (!typeElement.isJsonNull()) {
                            typeHint = TypeHint.fromStringIgnoreCase(typeElement.getAsString());
                        } else {
                            typeHint = null;
                        }
                        value = convertBasedOnJsonType(jsonElement, typeHint);
                    }
                } else if (type.isAssignableFrom(double.class) || type.isAssignableFrom(Double.class)) {
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
                System.err.println(String.format("Field '%s' could not be accessed. Ignoring.", fieldName));
            } catch (ClassCastException | UnsupportedOperationException e) {
                System.err.println(String.format("Field '%s' could not be casted to '%s'. Ignoring.", fieldName, type.toString()));
            }
        }
    }
    
    public static double[] readDoubleArray(JsonArray array) {
        final double[] result = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsDouble();
        }
        return result;
    }


    private static final Pattern ARRAY_PATTERN = Pattern.compile("\\[\\]");
    /**
     * TypeHint contained in the model's JSON. There might be more types contained than listed here - these are the ones
     * used.
     */
    private enum TypeHint {
        INT, FLOAT, DOUBLE, LONG, DOUBLE_ARR, FLOAT_ARR, STRING_ARR, STRING_ARR_ARR, INT_ARR, LONG_ARR, OBJECT_ARR;

        private static TypeHint fromStringIgnoreCase(final String from) {
            final Matcher matcher = ARRAY_PATTERN.matcher(from);
            final boolean isArray = matcher.find();
            final String transformedType = matcher.replaceAll("_ARR");
            try {
                return valueOf(transformedType.toUpperCase());
            } catch (IllegalArgumentException e) {
                if (isArray) {
                    return OBJECT_ARR;
                } else {
                    return null;
                }
            }
        }
    }

    /**
     * Convers a {@link JsonElement} to a corresponding Java class instance, covering all basic "primitive" types (String, numbers. ..)
     * + selected Iced classes.
     *
     * @param convertFrom JsonElement to convert from
     * @param typeHint    Optional {@link TypeHint} value. Might be null.
     * @return
     */
    private static Object convertBasedOnJsonType(final JsonElement convertFrom, final TypeHint typeHint) {
        final Object convertTo;

        if (convertFrom.isJsonNull()) {
            convertTo = null;
        } else if (convertFrom.isJsonArray()) {
            final JsonArray array = convertFrom.getAsJsonArray();
            if (typeHint == null) {
                convertTo = null;
            } else {
                switch (typeHint) {
                    case OBJECT_ARR:
                        final Object[] arrO = new Object[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            JsonElement e = array.get(i);
                            if (e.isJsonPrimitive()) {
                                arrO[i] = convertBasedOnJsonType(e, null);
                            } else {
                                arrO[i] = convertJsonObject(e.getAsJsonObject());
                            }
                        }
                        convertTo = arrO;
                        break;
                    case DOUBLE_ARR:
                        final double[] arrD = new double[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            arrD[i] = array.get(i).getAsDouble();
                        }
                        convertTo = arrD;
                        break;
                    case FLOAT_ARR:
                        final double[] arrF = new double[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            arrF[i] = array.get(i).getAsDouble();
                        }
                        convertTo = arrF;
                        break;
                    case STRING_ARR:
                        final String[] arrS = new String[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            arrS[i] = array.get(i).getAsString();
                        }
                        convertTo = arrS;
                        break;
                    case STRING_ARR_ARR:
                        final String[][] arrSS = new String[array.size()][];
                        for (int i = 0; i < array.size(); i++) {
                            final JsonArray arr2 = array.get(i).getAsJsonArray();
                            arrSS[i] = new String[arr2.size()];
                            for (int j = 0; j < arr2.size(); j++) {
                                arrSS[i][j] = arr2.get(j).getAsString();
                            }
                        }
                        convertTo = arrSS;
                        break;
                    case INT_ARR:
                        final int[] arrI = new int[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            arrI[i] = array.get(i).getAsInt();
                        }
                        convertTo = arrI;
                        break;
                    case LONG_ARR:
                        final long[] arrL = new long[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            arrL[i] = array.get(i).getAsLong();
                        }
                        convertTo = arrL;
                        break;
                    default:
                        convertTo = null;
                        break;
                }
            }
        } else if (convertFrom.isJsonPrimitive()) {
            final JsonPrimitive convertedPrimitive = convertFrom.getAsJsonPrimitive();
            if (convertedPrimitive.isBoolean()) {
                convertTo = convertedPrimitive.getAsBoolean();
            } else if (convertedPrimitive.isString()) {
                convertTo = convertedPrimitive.getAsString();
            } else if (convertedPrimitive.isNumber()) {
                if (typeHint == null) {
                    convertTo = convertedPrimitive.getAsDouble();
                } else {
                    switch (typeHint) {
                        case INT:
                            convertTo = convertedPrimitive.getAsInt();
                            break;
                        case FLOAT:
                            convertTo = convertedPrimitive.getAsFloat();
                            break;
                        case DOUBLE:
                            convertTo = convertedPrimitive.getAsDouble();
                            break;
                        case LONG:
                            convertTo = convertedPrimitive.getAsLong();
                            break;
                        default:
                            convertTo = convertedPrimitive.getAsDouble();
                    }
                }
            } else {
                convertTo = null;
            }
        } else if (convertFrom.isJsonObject()) {
            convertTo = convertJsonObject(convertFrom.getAsJsonObject());
        } else {
            convertTo = null;
        }

        return convertTo;

    }

    private static Object convertJsonObject(final JsonObject convertFrom) {
        final JsonElement meta = convertFrom.get("__meta");
        if (meta == null || meta.isJsonNull()) return null;

        final String schemaName = findInJson(meta, "schema_name").getAsString();

        if ("FrameKeyV3".equals(schemaName) || "ModelKeyV3".equals(schemaName)) {
            final String name = convertFrom.get("name").getAsString();
            final String type = convertFrom.get("type").getAsString();
            final ParameterKey.Type convertedType = convertKeyType(type);
            final String url = convertFrom.get("URL").getAsString();
            return new ParameterKey(name, convertedType, url);
        } else if ("ColSpecifierV3".equals(schemaName)) {
            final String columnName = convertFrom.get("column_name").getAsString();
            final JsonElement is_member_of_frames = convertFrom.get("is_member_of_frames");
            final String[] memberOfFrames;
            if (is_member_of_frames.isJsonArray()) {
                memberOfFrames = convertStringJsonArray(convertFrom.get("is_member_of_frames").getAsJsonArray());
            } else {
                memberOfFrames = null;
            }
            return new ColumnSpecifier(columnName, memberOfFrames);
        } else if ("KeyValueV3".equals(schemaName)) {
            return new KeyValue(
                convertFrom.get("key").getAsString(),
                convertFrom.get("value").getAsDouble()
            );
        } else {
            LOG.error(String.format("Error reading MOJO JSON: Object not supported: \n %s ", convertFrom.toString()));
            return null;
        }

    }

    private static String[] convertStringJsonArray(final JsonArray jsonArray) {
        Objects.requireNonNull(jsonArray);

        if (jsonArray.isJsonNull()) return null;
        final String[] strings = new String[jsonArray.size()];

        for (int i = 0; i < jsonArray.size(); i++) {
            final JsonElement potentialStringMember = jsonArray.get(i);
            if (!potentialStringMember.isJsonNull()) {
                strings[i] = jsonArray.get(i).getAsString();
            }
        }

        return strings;
    }

    /**
     * Converts a string key type to enum representation. All unknown keys are considered to be
     * Type.Generic.
     *
     * @param type A Key type in String representation to be converted
     * @return An instance of {@link ParameterKey.Type} enum
     */
    private static final ParameterKey.Type convertKeyType(final String type) {
        if ("Key<Frame>".equals(type)) {
            return ParameterKey.Type.FRAME;
        } else if ("Key<Model>".equals(type)) {
            return ParameterKey.Type.MODEL;
        } else return ParameterKey.Type.GENERIC;
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
    protected static JsonElement findInJson(final JsonElement jsonElement, final String jsonPath) {

        final String[] route = JSON_PATH_PATTERN.split(jsonPath);
        JsonElement result = jsonElement;

        for (String key : route) {
            key = key.trim();
            if (key.isEmpty())
                continue;

            if (result == null) {
                break;
            }

            if (result.isJsonObject()) {
                result = ((JsonObject) result).get(key);
            } else if (result.isJsonArray()) {
                int value = Integer.valueOf(key) - 1;
                result = ((JsonArray) result).get(value);
            } else break;
        }

        if (result == null) {
            return JsonNull.INSTANCE;
        } else {
            return result;   
        }
    }

    /**
     *
     * @param jsonElement A (potentially complex) element to search in
     * @param jsonPath    Path in the given JSON to the desired table. Levels are dot-separated.
     *                    E.g. 'model._output.variable_importances'.
     * @return True if the element exists under the given path in the target JSON, otherwise false
     */
    public static boolean elementExists(JsonElement jsonElement, String jsonPath){
        final boolean isEmpty = findInJson(jsonElement, jsonPath).isJsonNull();
        return !isEmpty;
    }
}
