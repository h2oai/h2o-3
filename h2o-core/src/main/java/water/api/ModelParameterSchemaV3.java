package water.api;

import java.lang.reflect.Field;

import water.AutoBuffer;
import water.H2O;
import water.Iced;
import water.IcedWrapper;
import water.api.SchemaMetadata.FieldMetadata;
import water.util.PojoUtils;

// TODO: move into hex.schemas!


/**
 * An instance of a ModelParameters schema contains the metadata for a single Model build parameter (e.g., K for KMeans).
 * TODO: add a superclass.
 * TODO: refactor this into with FieldMetadataBase.
 */
public class ModelParameterSchemaV3 extends SchemaV3<Iced, ModelParameterSchemaV3> {
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // CAREFUL: This class has its own JSON serializer.  If you add a field here you probably also want to add it to the serializer!
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  @API(help="name in the JSON, e.g. \"lambda\"", direction=API.Direction.OUTPUT)
  public String name;

  @API(help="help for the UI, e.g. \"regularization multiplier, typically used for foo bar baz etc.\"", direction=API.Direction.OUTPUT)
  public String help;

  @API(help="the field is required", direction=API.Direction.OUTPUT)
  public boolean required;

  @API(help="Java type, e.g. \"double\"", direction=API.Direction.OUTPUT)
  public String type;

  @API(help="default value, e.g. 1", direction=API.Direction.OUTPUT)
  public Iced default_value;

  @API(help="actual value as set by the user and / or modified by the ModelBuilder, e.g., 10", direction=API.Direction.OUTPUT)
  public Iced actual_value;

  @API(help="the importance of the parameter, used by the UI, e.g. \"critical\", \"extended\" or \"expert\"", direction=API.Direction.OUTPUT)
  public String level;

  @API(help="list of valid values for use by the front-end", direction=API.Direction.OUTPUT)
  public String[] values;

  @API(help="For Vec-type fields this is the set of other Vec-type fields which must contain mutually exclusive values; for example, for a SupervisedModel the response_column must be mutually exclusive with the weights_column")
  public String[] is_member_of_frames;

  @API(help="For Vec-type fields this is the set of Frame-type fields which must contain the named column; for example, for a SupervisedModel the response_column must be in both the training_frame and (if it's set) the validation_frame")
  public String[] is_mutually_exclusive_with;

  @API(help="Parameter can be used in grid call", direction=API.Direction.OUTPUT)
  public boolean gridable;

  public ModelParameterSchemaV3() {
  }

  /** TODO: refactor using SchemaMetadata. */
  public ModelParameterSchemaV3(ModelParametersSchema schema, ModelParametersSchema default_schema, Field f) {
    f.setAccessible(true);
    try {
      this.name = f.getName();
      API annotation = f.getAnnotation(API.class);

      boolean is_array = f.getType().isArray();
      Object o;

      o = f.get(default_schema);
      this.default_value = FieldMetadata.consValue(o);

      o = f.get(schema);
      this.actual_value = FieldMetadata.consValue(o);

      boolean is_enum = Enum.class.isAssignableFrom(f.getType());
      this.type = FieldMetadata.consType(schema, f.getType(), f.getName(), annotation);

      if (null != annotation) {
        // String l = annotation.label();
        // this.label = (null == l || l.isEmpty() ? f.getName() : l);
        this.help = annotation.help();
        this.required = annotation.required();

        this.level = annotation.level().toString();

        this.values = annotation.values();

        // If the field is an enum then the values annotation field had better be set. . .
        if (is_enum && (null == this.values || 0 == this.values.length)) {
          throw H2O.fail("Didn't find values annotation for enum field: " + this.name);
        }

        // NOTE: we just set the raw value here.  We compute the transitive closure
        // before serializing to JSON.  We have to do this automagically since we
        // need to combine the values from multiple fields in multiple levels of the
        // inheritance hierarchy.
        this.is_member_of_frames = annotation.is_member_of_frames();

        this.is_mutually_exclusive_with = annotation.is_mutually_exclusive_with(); // NOTE: later we walk all the fields in the Schema and form the transitive closure of these lists.

        this.gridable = annotation.gridable();
      }
    }
    catch (Exception e) {
      throw H2O.fail("Caught exception accessing field: " + f + " for schema object: " + this + ": " + e.toString());
    }
  }

  public ModelParameterSchemaV3 fillFromImpl(Iced iced) {
    PojoUtils.copyProperties(this, iced, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    return this;
  }

  public Iced createImpl() {
    // should never get called
    throw H2O.fail("createImpl should never get called in ModelParameterSchemaV2!");
  }

  /**
   * ModelParameterSchema has its own serializer so that default_value and actual_value
   * get serialized as their native types.  Autobuffer assumes all classes that have their
   * own serializers should be serialized as JSON objects, and wraps them in {}, so this
   * can't just be handled by a customer serializer in IcedWrapper.
   *
   * @param ab
   * @return
   */
  public final AutoBuffer writeJSON_impl(AutoBuffer ab) {
    ab.putJSONStr("name", name);                                    ab.put1(',');
    ab.putJSONStr("help", help);                                    ab.put1(',');
    ab.putJSONStrUnquoted("required", required ? "true" : "false"); ab.put1(',');
    ab.putJSONStr("type", type);                                    ab.put1(',');

    if (default_value instanceof IcedWrapper) {
      ab.putJSONStr("default_value").put1(':');
      ((IcedWrapper) default_value).writeUnwrappedJSON(ab);            ab.put1(',');
    } else {
      ab.putJSONStr("default_value").put1(':').putJSON(default_value); ab.put1(',');
    }

    if (actual_value instanceof IcedWrapper) {
      ab.putJSONStr("actual_value").put1(':');
      ((IcedWrapper) actual_value).writeUnwrappedJSON(ab);             ab.put1(',');
    } else {
      ab.putJSONStr("actual_value").put1(':').putJSON(actual_value);   ab.put1(',');
    }

    ab.putJSONStr("level", level);                                            ab.put1(',');
    ab.putJSONAStr("values", values);                                         ab.put1(',');
    ab.putJSONAStr("is_member_of_frames", is_member_of_frames);               ab.put1(',');
    ab.putJSONAStr("is_mutually_exclusive_with", is_mutually_exclusive_with); ab.put1(',');
    ab.putJSONStrUnquoted("gridable", gridable ? "true" : "false");
    return ab;
  }
}
