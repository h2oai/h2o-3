package water.api;

import water.H2O;
import water.Iced;
import water.Keyed;
import water.util.Log;
import water.util.ReflectionUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;

/**
 * The metadata info on all the fields in a Schema.  This is used to help Schema be self-documenting,
 * and to generate language bindings for route handlers and entities.
 */
public final class SchemaMetadata extends Iced {

  public Map<String, FieldMetadata> fields;

  static public final class FieldMetadata extends Iced {
    /**
     * Field name in the POJO.    Set through reflection.
     */
    String name;

    /**
     * Type for this field.  Set through reflection.
     */
    public String type;

    /**
     * Value for this field.  Set through reflection.
     */
    public String value;

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

    public FieldMetadata(String name, String type, String value, String help, String label, boolean required, API.Level level, API.Direction direction, String[] values, boolean json) {
      // from the Field
      this.name = name;
      this.type = type;
      this.value = value;

      // from the @API annotation
      this.help = help;
      this.label = label;
      this.required = required;
      this.level = level;
      this.direction = direction;
      this.values = values;
      this.json = json;
    }

    /**
     * Create a new FieldMetadata object for the given Field of the given Schema.
     * @param schema water.api.Schema object
     * @param f java.lang.reflect.Field for the Schema class
     */
    public FieldMetadata(Schema schema, Field f) {
      try {
        this.name = f.getName();
        boolean is_array = f.getType().isArray();
        Object o;

        f.setAccessible(true); // handle private and protected fields
        o = f.get(schema);
        this.value = consValue(o);

        boolean is_enum = Enum.class.isAssignableFrom(f.getType());
        this.type = consType(f.getType());

        API annotation = f.getAnnotation(API.class);

        if (null != annotation) {
          String l = annotation.label();
          this.help = annotation.help();
          this.label = (null == l || l.isEmpty() ? f.getName() : l);
          this.required = annotation.required();
          this.level = annotation.level();
          this.direction = annotation.direction();
          this.values = annotation.values();
          this.json = annotation.json();

          // If the field is an enum then the values annotation field had better be set. . .
          if (is_enum && (null == this.values || 0 == this.values.length)) {
            throw H2O.fail("Didn't find values annotation for enum field: " + this.name);
          }
        }
      }
      catch (Exception e) {
        throw H2O.fail("Caught exception accessing field: " + f + " for schema object: " + this + ": " + e.toString());
      }
    } // FieldMetadata(Schema, Field)

    /**
     * Factory method to create a new FieldMetadata instance if the Field has an @API annotation.
     * @param schema water.api.Schema object
     * @param f java.lang.reflect.Field for the Schema class
     * @return a new FieldMetadata instance if the Field has an @API annotation, else null
     */
    public static FieldMetadata createIfApiAnnotation(Schema schema, Field f) {
      f.setAccessible(true); // handle private and protected fields

      if (null != f.getAnnotation(API.class))
        return new FieldMetadata(schema, f);

      Log.warn("Skipping field that lacks an annotation: " + schema.toString() + "." + f);
      return null;
    }

    /** For a given Class generate a client-friendly type name (e.g., int[][] or Frame). */
    private static String consType(Class clz) {
      boolean is_enum = Enum.class.isAssignableFrom(clz);
      boolean is_array = clz.isArray();

      // built-in Java types:
      if (is_enum)
        return "enum";

      if (String.class.isAssignableFrom(clz))
        return "string"; // lower-case, to be less Java-centric

      if (clz.equals(Boolean.TYPE) || clz.equals(Byte.TYPE) || clz.equals(Short.TYPE) || clz.equals(Integer.TYPE) || clz.equals(Long.TYPE) || clz.equals(Float.TYPE) || clz.equals(Double.TYPE))
        return clz.toString();

      if (is_array)
        return consType(clz.getComponentType()) + "[]";

      // H2O-specific types:
      if (hex.Model.class.isAssignableFrom(clz))
        return "Model";

      if (water.fvec.Frame.class.isAssignableFrom(clz))
        return "Frame";

      if (water.fvec.Vec.class.isAssignableFrom(clz))
        return "Vec";

      if (water.Key.class.isAssignableFrom(clz))
        return "Key";

      if (water.api.JobV2.class.isAssignableFrom(clz))
        return "Job";

      if (water.api.ModelMetricsBase.class.isAssignableFrom(clz))
        return "Key";

      if (CloudV1.class.isAssignableFrom(clz))
        return "Cloud";

      if (CloudV1.Node.class.isAssignableFrom(clz))
        return "Node";

      Log.warn("Don't know how to generate a client-friendly type name for class: " + clz.toString());
      return clz.toString();
    }

    private static String consValue(Object o) {
      if (null == o)
        return null;

      if (water.Keyed.class.isAssignableFrom(o.getClass())) {
        Keyed k = (Keyed)o;
        return k._key.toString();
      }

      if (! o.getClass().isArray())
        return o.toString();

      StringBuilder sb = new StringBuilder();
      sb.append("[");
      for (int i = 0; i < Array.getLength(o); i++) {
        if (i > 0) sb.append(", ");
        sb.append(consValue(Array.get(o, i)));
      }
      sb.append("]");
      return sb.toString();
    }
  } // FieldMetadata

  public SchemaMetadata() {
    fields = new TreeMap<String, FieldMetadata>();
  }

  public SchemaMetadata(Schema schema) {
    fields = new TreeMap<String, FieldMetadata>();

    String field_name = null;
    // Fields up to but not including Schema
    for (Field field : ReflectionUtils.getFieldsUpTo(schema.getClass(), Schema.class)) {
      field_name = field.getName();
      FieldMetadata fmd = FieldMetadata.createIfApiAnnotation(schema, field);
      if (null != fmd) // skip transient or other non-annotated fields
        fields.put(field_name, fmd);  // NOTE: we include non-JSON fields here; remove them later if we don't want them
    }
  }
}
