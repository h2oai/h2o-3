package water.api;

import water.AutoBuffer;
import water.Iced;
import water.IcedWrapper;
import water.api.SchemaMetadata.FieldMetadata;
import water.util.PojoUtils;

import java.util.ArrayList;

/**
 * Schema for the metadata for a Schema.
 */
public class SchemaMetadataBase<I extends SchemaMetadata, S extends SchemaMetadataBase<I, S>> extends SchemaV3<I, SchemaMetadataBase<I, S>> {

  @API(help="Version number of the Schema.")
  public int version;

  /** The simple schema (class) name, e.g. DeepLearningParametersV2, used in the schema metadata.  Must not be changed after creation (treat as final).  */
  @API(help="Simple name of the Schema.  NOTE: the schema_names form a single namespace.")
  public String name ;

  /** The simple schema superclass name, e.g. ModelSchema, used in the schema metadata.  Must not be changed after creation (treat as final).  */
  @API(help="Simple name of the superclass of the Schema.  NOTE: the schema_names form a single namespace.")
  public String superclass ;

  @API(help="Simple name of H2O type that this Schema represents.  Must not be changed after creation (treat as final).")
  public String type;

  @API(help="All the public fields of the schema", direction=API.Direction.OUTPUT)
  public FieldMetadataBase[] fields;

  @API(help="Documentation for the schema in Markdown format with GitHub extensions", direction=API.Direction.OUTPUT)
  String markdown;

  /**
   * Schema for the metadata for the field of a Schema.
   */
  public static class FieldMetadataBase<I extends FieldMetadata, S extends FieldMetadataBase<I, S>> extends SchemaV3<I, S> {
    @API(help="Field name in the Schema", direction=API.Direction.OUTPUT)
    public String name;

    @API(help="Type for this field", direction=API.Direction.OUTPUT)
    public String type;

    @API(help="Type for this field is itself a Schema.", direction=API.Direction.OUTPUT)
    public boolean is_schema;

    @API(help="Schema name for this field, if it is_schema, or the name of the enum, if it's an enum.")
    public String schema_name;

    @API(help="Value for this field", direction=API.Direction.OUTPUT)
    public Iced value;

    @API(help="A short help description to appear alongside the field in a UI", direction=API.Direction.OUTPUT)
    public String help;

    @API(help="The label that should be displayed for the field if the name is insufficient", direction=API.Direction.OUTPUT)
    public String label;

    @API(help="Is this field required, or is the default value generally sufficient?", direction=API.Direction.OUTPUT)
    public boolean required;

    @API(help="How important is this field?  The web UI uses the level to do a slow reveal of the parameters", values={"critical", "secondary", "expert"}, direction=API.Direction.OUTPUT)
    public API.Level level;

    @API(help="Is this field an input, output or inout?", values={"INPUT", "OUTPUT", "INOUT"}, direction=API.Direction.OUTPUT)
    public API.Direction direction;

    @API(help="Is the field inherited from the parent schema?", direction = API.Direction.OUTPUT)
    public boolean is_inherited;

    @API(help="If this field is inherited from a class higher in the hierarchy which one?", direction = API.Direction.OUTPUT)
    public String inherited_from;

    @API(help="Is the field gridable (i.e., it can be used in grid call)", direction = API.Direction.OUTPUT)
    public boolean is_gridable;

    // The following are markers for *input* fields.

    @API(help="For enum-type fields the allowed values are specified using the values annotation;  this is used in UIs to tell the user the allowed values, and for validation", direction=API.Direction.OUTPUT)
    String[] values;

    @API(help="Should this field be rendered in the JSON representation?", direction=API.Direction.OUTPUT)
    boolean json;

    @API(help="For Vec-type fields this is the set of other Vec-type fields which must contain mutually exclusive values; for example, for a SupervisedModel the response_column must be mutually exclusive with the weights_column", direction=API.Direction.OUTPUT)
    String[] is_member_of_frames;

    @API(help="For Vec-type fields this is the set of Frame-type fields which must contain the named column; for example, for a SupervisedModel the response_column must be in both the training_frame and (if it's set) the validation_frame", direction=API.Direction.OUTPUT)
    String[] is_mutually_exclusive_with;

  /**
   * FieldMetadataBase has its own serializer so that value get serialized as its native
   * type.  Autobuffer assumes all classes that have their own serializers should be
   * serialized as JSON objects, and wraps them in {}, so this can't just be handled by a
   * customer serializer in IcedWrapper.
   *
   * @param ab
   * @return
   */
  public final AutoBuffer writeJSON_impl(AutoBuffer ab) {
    boolean isOut = direction == API.Direction.OUTPUT;
    ab.putJSONStr("name", name);                                      ab.put1(',');
    ab.putJSONStr("type", type);                                      ab.put1(',');
    ab.putJSONStrUnquoted("is_schema", is_schema ? "true" : "false"); ab.put1(',');
    ab.putJSONStr("schema_name", schema_name);                        ab.put1(',');

    if (value instanceof IcedWrapper) {
      ab.putJSONStr("value").put1(':');
      ((IcedWrapper) value).writeUnwrappedJSON(ab);                   ab.put1(',');
    } else {
      ab.putJSONStr("value").put1(':').putJSON(value);                ab.put1(',');
    }

    ab.putJSONStr("help", help);                                      ab.put1(',');
    ab.putJSONStr("label", label);                                    ab.put1(',');
    ab.putJSONStrUnquoted("required", isOut? "null" : required ? "true" : "false");   ab.put1(',');
    ab.putJSONStr("level", level.toString());                         ab.put1(',');
    ab.putJSONStr("direction", direction.toString());                 ab.put1(',');
    ab.putJSONStrUnquoted("is_inherited", is_inherited ? "true" : "false"); ab.put1(',');
    ab.putJSONStr("inherited_from", inherited_from); ab.put1(',');
    ab.putJSONStrUnquoted("is_gridable", isOut? "null" : is_gridable ? "true" : "false"); ab.put1(',');
    ab.putJSONAStr("values", values);                                 ab.put1(',');
    ab.putJSONStrUnquoted("json", json ? "true" : "false");           ab.put1(',');
    ab.putJSONAStr("is_member_of_frames", is_member_of_frames);       ab.put1(',');
    ab.putJSONAStr("is_mutually_exclusive_with", is_mutually_exclusive_with);
    return ab;
  }


  } // FieldMetadataBase

  @Override
  public I createImpl() {
    SchemaMetadata impl = new SchemaMetadata();
    return (I)impl;
  }

  @Override
  public I fillImpl(I impl) {
    impl.fields = new ArrayList<FieldMetadata>(this.fields.length);
    int i = 0;
    for (FieldMetadataBase s : this.fields)
      impl.fields.add((FieldMetadata)s.createImpl());
    return impl;
  }

  @Override
  public SchemaMetadataBase fillFromImpl(SchemaMetadata impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.CONSISTENT, new String[] {"fields"});
    this.fields = new FieldMetadataBase[impl.fields.size()];
    int i = 0;
    for (FieldMetadata f : impl.fields)
      this.fields[i++] = new FieldMetadataV3().fillFromImpl(f); // TODO: version!
    return this;
  }
}
