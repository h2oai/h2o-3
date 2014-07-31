package water.api;

import water.H2O;
import water.Iced;
import water.util.BeanUtils;

import java.lang.reflect.Field;

// TODO: move into hex.schemas!


/**
 * An instance of a ModelParameters schema contains the metadata for a single Model build parameter (e.g., K for KMeans).
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
  public String default_value;

  @API(help="actual value as set by the user and / or modified by the ModelBuilder, e.g., 10")
  public String actual_value;

  @API(help="the importance of the parameter, used by the UI, e.g. \"critical\", \"extended\" or \"expert\"")
  public String level;

  @API(help="other fields that must be set before setting this one, e.g. \"response_column\"")
  public String[] dependencies;

  // TODO: change to a richer type:
  @API(help="list of validation expressions for use by the front-end and back-end")
  public String[] validation;
// [
//     "type": "regexp", "value": "[0-9]*\.?[0-9]+",
//     "type": "backend", "value": "/models/my_model/parameters/lambda?validate_value=%s"
// ]

  @API(help="list of valid values for use by the front-end")
  public String[] values;


  public ModelParameterSchemaV2() {
  }

  public ModelParameterSchemaV2(ModelParametersSchema schema, ModelParametersSchema default_schema, Field f) {
    try {
      this.name = f.getName();
      Object o;

      o = f.get(default_schema);
      this.default_value = (o == null ? null : o.toString());

      o = f.get(schema);
      this.actual_value = (o == null ? null : o.toString());

      boolean is_enum = Enum.class.isAssignableFrom(f.getType());
      this.type = (is_enum ? "enum" : f.getType().toString());

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

        this.validation = annotation.validation();
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
