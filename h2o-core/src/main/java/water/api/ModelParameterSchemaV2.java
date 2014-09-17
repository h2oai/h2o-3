package water.api;

import water.H2O;
import water.Iced;
import water.util.BeanUtils;
import water.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

// TODO: move into hex.schemas!


/**
 * An instance of a ModelParameters schema contains the metadata for a single Model build parameter (e.g., K for KMeans).
 * TODO: add a superclass.
 */
public class ModelParameterSchemaV2 extends Schema<Iced, ModelParameterSchemaV2> {
  @API(help="name in the JSON, e.g. \"lambda\"")
  public String name;

  @API(help="label in the UI, e.g. \"lambda\"")
  public String label;

  @API(help="help for the UI, e.g. \"regularization multiplier, typically used for foo bar baz etc.\"")
  public String help;

  @API(help="the field is required")
  public boolean required;

  @API(help="Java type, e.g. \"double\"")
  public String type;

  @API(help="default value, e.g. 1")
  public String default_value; // TODO: we would like this to be a primitive so that the client doesn't have to parse it. . .  Problem is Icer serialization blows up if the field is an Object

  @API(help="actual value as set by the user and / or modified by the ModelBuilder, e.g., 10")
  public String actual_value; // TODO: we would like this to be a primitive so that the client doesn't have to parse it. . .

  @API(help="the importance of the parameter, used by the UI, e.g. \"critical\", \"extended\" or \"expert\"")
  public String level;

  @API(help="other fields that must be set before setting this one, e.g. \"response_column\"")
  public String[] dependencies;

  @API(help="list of valid values for use by the front-end")
  public String[] values;


  public ModelParameterSchemaV2() {
  }

  /** For a given Class generate a client-friendly type name (e.g., int[][] or Frame). */
  private static String consType(Class clz) {
    boolean is_enum = Enum.class.isAssignableFrom(clz);
    boolean is_array = clz.isArray();

    if (is_enum)
      return "enum";

    if (is_array)
      return consType(clz.getComponentType()) + "[]";

    if (water.Model.class.isAssignableFrom(clz))
      return "Model";

    if (water.fvec.Frame.class.isAssignableFrom(clz))
      return "Frame";

    if (water.fvec.Vec.class.isAssignableFrom(clz))
      return "Vec";

    if (water.Key.class.isAssignableFrom(clz))
      return "Key";

    if (String.class.isAssignableFrom(clz))
      return "string"; // lower-case, to be less Java-centric

    if (clz.equals(Boolean.TYPE) || clz.equals(Integer.TYPE) || clz.equals(Long.TYPE) || clz.equals(Float.TYPE) || clz.equals(Double.TYPE))
      return clz.toString();

    Log.warn("Don't know how to generate a client-friendly type name for class: " + clz.toString());
    return clz.toString();
  }

  private static String consValue(Object o) {
    if (null == o)
      return null;

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

  public ModelParameterSchemaV2(ModelParametersSchema schema, ModelParametersSchema default_schema, Field f) {
    try {
      this.name = f.getName();
      boolean is_array = f.getType().isArray();
      Object o;

      o = f.get(default_schema);
      this.default_value = consValue(o);


      o = f.get(schema);
      this.actual_value = consValue(o);

      boolean is_enum = Enum.class.isAssignableFrom(f.getType());
      this.type = consType(f.getType());

      API annotation = f.getAnnotation(API.class);

      if (null != annotation) {
        String l = annotation.label();
        this.label = (null == l || l.isEmpty() ? f.getName() : l);
        this.help = annotation.help();
        this.required = annotation.required();

        this.level = annotation.level().toString();
        this.dependencies = annotation.dependsOn();

        this.values = annotation.values();

        // If the field is an enum then the values annotation field had better be set. . .
        if (is_enum && (null == this.values || 0 == this.values.length)) {
          throw H2O.fail("Didn't find values annotation for enum field: " + this.name);
        }
      }
    }
    catch (Exception e) {
      throw H2O.fail("Caught exception accessing field: " + f + " for schema object: " + this + ": " + e.toString());
    }
  }

  public ModelParameterSchemaV2 fillFromImpl(Iced iced) {
    BeanUtils.copyProperties(this, iced, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    return this;
  }

  public Iced createImpl() {
    // should never get called
    throw H2O.fail("createImpl should never get called in ModelParameterSchemaV2!");
  }
}
