package water.api;

import water.H2O;
import water.Iced;
import water.IcedWrapper;
import water.Weaver;
import water.api.SchemaMetadataBase.FieldMetadataBase;
import water.exceptions.H2OIllegalArgumentException;
import water.util.IcedHashMapBase;
import water.util.Log;
import water.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The metadata info on all the fields in a Schema.  This is used to help Schema be self-documenting,
 * and to generate language bindings for route handlers and entities.
 */
public final class SchemaMetadata extends Iced {

  public int version;
  public String name;
  public String superclass;
  public String type;

  public List<FieldMetadata> fields;
  public String markdown;

  // TODO: combine with ModelParameterSchemaV2.
  static public final class FieldMetadata extends Iced {
    /**
     * Field name in the POJO.    Set through reflection.
     */
    public String name;

    /**
     * Type for this field.  Set through reflection.
     */
    public String type;

    /**
     * Type for this field is itself a Schema.  Set through reflection.
     */
    public boolean is_schema;

    /**
     * Schema name for this field, if it is_schema.  Set through reflection.
     */
    public String schema_name;

    /**
     * Value for this field.  Set through reflection.
     */
    public Iced value;

    /**
     *  A short help description to appear alongside the field in a UI.  Set from the @API annotation.
     */
    String help;

    /**
     * The label that should be displayed for the field if the name is insufficient.  Set from the @API annotation.
     */
    String label;

    /**
     * Is this field required, or is the default value generally sufficient?  Set from the @API annotation.
     */
    boolean required;

    /**
     * How important is this field?  The web UI uses the level to do a slow reveal of the parameters.  Set from the @API annotation.
     */
    API.Level level;

    /**
     * Is this field an input, output or inout?  Set from the @API annotation.
     */
    API.Direction direction;

    /**
     * Is this field inherited from a class higher in the hierarchy?
     */
    public boolean is_inherited;

    /**
     * If this field is inherited from a class higher in the hierarchy which one?
     */
    public String inherited_from;

    /**
     * Is this field gridable?  Set from the @API annotation.
     */
    public boolean is_gridable;

    // The following are markers for *input* fields.

    /**
     * For enum-type fields the allowed values are specified using the values annotation.
     * This is used in UIs to tell the user the allowed values, and for validation.
     * Set from the @API annotation.
     */
    String[] values;

    /**
     * Should this field be rendered in the JSON representation?  Set from the @API annotation.
     */
    boolean json;

    /**
     * For Vec-type fields this is the set of Frame-type fields which must contain the named column.
     * For example, for a SupervisedModel the response_column must be in both the training_frame
     * and (if it's set) the validation_frame.
     */
    String[] is_member_of_frames;

    /**
     * For Vec-type fields this is the set of other Vec-type fields which must contain
     * mutually exclusive values.  For example, for a SupervisedModel the response_column
     * must be mutually exclusive with the weights_column.
     */
    String[] is_mutually_exclusive_with;


    public FieldMetadata() { }

    /**
     * Create a new FieldMetadata object for the given Field of the given Schema.
     * @param schema water.api.Schema object
     * @param f java.lang.reflect.Field for the Schema class
     */
    public FieldMetadata(Schema schema, Field f, List<Field>superclassFields) {
      super();
      try {
        f.setAccessible(true); // handle private and protected fields
        // Get annotation directly
        API annotation = f.getAnnotation(API.class);

        this.name = f.getName();
        Object o = f.get(schema);
        this.value = consValue(o);

        // Enum is a field of enum type or of String type with defined and fixed set of values!
        boolean is_enum = isEnum(f.getType(), annotation) || (f.getType().isArray() && isEnum(f.getType().getComponentType(), annotation));
        boolean is_fake_enum = isFakeEnum(f.getType(), annotation) || (f.getType().isArray() && isFakeEnum(f.getType().getComponentType(), annotation));

        this.is_schema = Schema.class.isAssignableFrom(f.getType())
                         || (f.getType().isArray() && Schema.class.isAssignableFrom(f.getType().getComponentType()));

        this.type = consType(schema, ReflectionUtils.findActualFieldClass(schema.getClass(), f), f.getName(), annotation);

        // Note, this has to work when the field is null.  In addition, if the field's type is a base class we want to see if we have a versioned schema for its Iced type and, if so, use it.
        if (this.is_schema) {
          // First, get the class of the field: NOTE: this gets the actual type for genericized fields, but not for arrays of genericized fields
          Class<? extends Schema> schema_class = f.getType().isArray() ? (Class<? extends Schema>)f.getType().getComponentType() : ReflectionUtils.findActualFieldClass(schema.getClass(), f);

          // Now see if we have a versioned schema for its Iced type:
          Class<? extends Schema>  versioned_schema_class = Schema.schemaClass(schema.getSchemaVersion(), Schema.getImplClass(schema_class));

          // If we found a versioned schema class for its iced type use it, else fall back to the type of the field:
          if (null != versioned_schema_class) {
            this.schema_name = versioned_schema_class.getSimpleName();
          } else {
            this.schema_name = schema_class.getSimpleName();
          }
        } else if ((is_enum || is_fake_enum) && !f.getType().isArray()) {
          // We have enums of the same name defined in a few classes (e.g., Loss and Initialization)
          this.schema_name = getEnumSchemaName(is_enum ? f.getType() : annotation.valuesProvider());
        } else if ((is_enum || is_fake_enum) && f.getType().isArray()) {
          // We have enums of the same name defined in a few classes (e.g., Loss and Initialization)
          this.schema_name = getEnumSchemaName(is_enum ? f.getType().getComponentType() : annotation.valuesProvider());
        }

        this.is_inherited = (superclassFields.contains(f));
        if (this.is_inherited)
            this.inherited_from = f.getDeclaringClass().getSimpleName();

        if (null != annotation) {
          // String l = annotation.label();
          this.help = annotation.help();
          // this.label = (null == l || l.isEmpty() ? f.getName() : l);
          this.required = annotation.required();
          this.level = annotation.level();
          this.direction = annotation.direction();
          this.is_gridable = annotation.gridable();
          this.values = annotation.valuesProvider() == ValuesProvider.NULL ? annotation.values() : getValues(annotation.valuesProvider());
          this.json = annotation.json();
          this.is_member_of_frames = annotation.is_member_of_frames();
          this.is_mutually_exclusive_with = annotation.is_mutually_exclusive_with(); // TODO: need to form the transitive closure

          // If the field is an enum then the values annotation field had better be set. . .
          if (is_enum && (null == this.values || 0 == this.values.length)) {
            throw H2O.fail("Didn't find values annotation for enum field: " + this.name);
          }
        }
      }
      catch (Exception e) {
        throw H2O.fail("Caught exception accessing field: " + f + " for schema object: " + schema + ": " + e.toString());
      }
    } // FieldMetadata(Schema, Field)

    /**
     * Factory method to create a new FieldMetadata instance if the Field has an @API annotation.
     * @param schema water.api.Schema object
     * @param f java.lang.reflect.Field for the Schema class
     * @return a new FieldMetadata instance if the Field has an @API annotation, else null
     */
    public static FieldMetadata createIfApiAnnotation(Schema schema, Field f, List<Field> superclassFields) {
      f.setAccessible(true); // handle private and protected fields

      if (null != f.getAnnotation(API.class))
        return new FieldMetadata(schema, f, superclassFields);

      Log.warn("Skipping field that lacks an annotation: " + schema.toString() + "." + f);
      return null;
    }

    /** For a given Class generate a client-friendly type name (e.g., int[][] or Frame). */
    public static String consType(Schema schema, Class clz, String field_name, API annotation) {
      boolean is_enum = isEnum(clz, null) || isFakeEnum(clz, annotation);
      boolean is_array = clz.isArray();

      // built-in Java types:
      if (is_enum)
        return "enum";

      if (String.class.isAssignableFrom(clz))
        return "string"; // lower-case, to be less Java-centric

      if (clz.equals(Boolean.TYPE) || clz.equals(Byte.TYPE) || clz.equals(Short.TYPE) || clz.equals(Integer.TYPE) || clz.equals(Long.TYPE) || clz.equals(Float.TYPE) || clz.equals(Double.TYPE))
        return clz.toString();

      if (is_array)
        return consType(schema, clz.getComponentType(), field_name, annotation) + "[]";

      if (Map.class.isAssignableFrom(clz)) {
        if (IcedHashMapBase.class.isAssignableFrom(clz)) {
          String type0 = ReflectionUtils.findActualClassParameter(clz, 0).getSimpleName();
          String type1 = ReflectionUtils.findActualClassParameter(clz, 1).getSimpleName();
          if ("String".equals(type0)) type0 = "string";
          if ("String".equals(type1)) type1 = "string";
          return "Map<" + type0 + "," + type1 + ">";
        } else {
          Log.warn("Schema Map field isn't a subclass of IcedHashMap, so its metadata won't have type parameters: " + schema.getClass().getSimpleName() + "." + field_name);
          return "Map";
        }
      }


      if (List.class.isAssignableFrom(clz))
        return "List";

      // H2O-specific types:
      // TODO: NOTE, this is a mix of Schema types and Iced types; that's not right. . .
      // Should ONLY have schema types.
      // Also, this mapping could/should be moved to Schema.
      if (water.Key.class.isAssignableFrom(clz)) {
        Log.warn("Raw Key (not KeySchema) in Schema: " + schema.getClass() + " field: " + field_name);
        return "Key";
      }

      if (KeyV3.class.isAssignableFrom(clz)) {
        return "Key<" + KeyV3.getKeyedClassType((Class<? extends KeyV3>) clz) + ">";
      }

      if (Schema.class.isAssignableFrom(clz)) {
        return Schema.getImplClass((Class<Schema>)clz).getSimpleName();  // same as Schema.schema_type
      }

      if (Iced.class.isAssignableFrom(clz)) {
        if (clz == SchemaV3.Meta.class) {
          // Special case where we allow an Iced in a Schema so we don't get infinite meta-regress:
          return "Schema.Meta";
        } else {
          // Special cases: polymorphic metadata fields that can contain scalars, Schemas (any Iced, actually), or arrays of these:
          if (schema instanceof ModelParameterSchemaV3 && ("default_value".equals(field_name) || "actual_value".equals(field_name)))
            return "Polymorphic";

          if ((schema instanceof FieldMetadataV3 || schema instanceof FieldMetadataBase) && "value".equals(field_name))
            return "Polymorphic";

          if (((schema instanceof TwoDimTableBase || schema instanceof TwoDimTableV3) && "data".equals(field_name))) // IcedWrapper
            return "Polymorphic";

          Log.warn("WARNING: found non-Schema Iced field: " + clz.toString() + " in Schema: " + schema.getClass() + " field: " + field_name);
          return clz.getSimpleName();
        }
      }

      String msg = "Don't know how to generate a client-friendly type name for class: " + clz.toString() + " in Schema: " + schema.getClass() + " field: " + field_name;
      Log.warn(msg);
      throw H2O.fail(msg);
    }

    public static Iced consValue(Object o) {
      if (null == o)
        return null;

      Class clz = o.getClass();

      if (water.Iced.class.isAssignableFrom(clz))
        return (Iced)o;

      if (clz.isArray()) {
        return new IcedWrapper(o);
      }

/*
      if (water.Keyed.class.isAssignableFrom(o.getClass())) {
        Keyed k = (Keyed)o;
        return k._key.toString();
      }

      if (! o.getClass().isArray()) {
        if (Schema.class.isAssignableFrom(o.getClass())) {
          return new String(((Schema)o).writeJSON(new AutoBuffer()).buf());
        } else {
          return o.toString();
        }
      }

      StringBuilder sb = new StringBuilder();
      sb.append("[");
      for (int i = 0; i < Array.getLength(o); i++) {
        if (i > 0) sb.append(", ");
        sb.append(consValue(Array.get(o, i)));
      }
      sb.append("]");
      return sb.toString();
      */

      // Primitive type
      if (clz.isPrimitive())
        return new IcedWrapper(o);

      if (o instanceof Number)
        return new IcedWrapper(o);

      if (o instanceof Boolean)
        return new IcedWrapper(o);

      if (o instanceof String)
        return new IcedWrapper(o);

      if (o instanceof Enum)
        return new IcedWrapper(o);


      throw new H2OIllegalArgumentException("o", "consValue", o);
    }

  } // FieldMetadata

  public SchemaMetadata() {
    fields = new ArrayList<>();
  }

  public SchemaMetadata(Schema schema) {
    version = schema.getSchemaVersion();
    name = schema.getSchemaName();
    type = schema.getSchemaType();

    superclass = schema.getClass().getSuperclass().getSimpleName();
    // Get metadata of all annotated fields
    fields = getFieldMetadata(schema);
    // Also generates markdown
    markdown = schema.markdown(this, true, true).toString();
  }

  /**
   * Returns metadata of all annotated fields.
   *
   * @param schema a schema instance
   * @return list of field metadata
   */
  public static List<FieldMetadata> getFieldMetadata(Schema schema) {
    List<Field> superclassFields = Arrays.asList(Weaver.getWovenFields(schema.getClass().getSuperclass()));

    List<FieldMetadata> fields = new ArrayList<>();
    // Fields up to but not including Schema
    for (Field field : Weaver.getWovenFields(schema.getClass())) {
      FieldMetadata fmd = FieldMetadata.createIfApiAnnotation(schema, field, superclassFields);
      if (null != fmd) // skip transient or other non-annotated fields
        fields.add(fmd);  // NOTE: we include non-JSON fields here; remove them later if we don't want them
    }
    return fields;
  }

  public static SchemaMetadata createSchemaMetadata(String classname) throws IllegalArgumentException {
    try {
      Class<? extends Schema> clz = (Class<? extends Schema>) Class.forName(classname);
      Schema s = clz.newInstance();
      s.fillFromImpl(s.createImpl()); // get defaults

      return new SchemaMetadata(s);
    }
    catch (Exception e) {
      String msg = "Caught exception fetching schema: " + classname + ": " + e;
      Log.warn(msg);
      throw new IllegalArgumentException(msg);
    }
  }

  private static String[] getValues(Class<? extends ValuesProvider> valuesProvider) {
    String[] values;
    try {
      ValuesProvider vp = valuesProvider.newInstance();
      values = vp.values();
    } catch (Throwable e) {
      values = null;
    }
    return values;
  }

  // Enum is a field of enum type or of String type with defined and fixed set of values!
  private static boolean isEnum(Class<?> type, API annotation) {
    return Enum.class.isAssignableFrom(type);
  }

  private static boolean isFakeEnum(Class<?> type, API annotation) {
    return (annotation != null
            && annotation.valuesProvider() != ValuesProvider.NULL
            && String.class.isAssignableFrom(type));
  }

  private static String getEnumSchemaName(Class<?> type) {
    StringBuffer sb = new StringBuffer(type.getCanonicalName());
    sb.delete(0, sb.indexOf(".")+1);
    sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
    return sb.toString().replace(".", "").replace("$", "");
  }
}
