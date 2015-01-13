package water.api;

import water.Iced;
import water.api.SchemaMetadata.FieldMetadata;
import water.util.PojoUtils;

import java.util.ArrayList;

/**
 * Schema for the metadata for a Schema.
 */
public class SchemaMetadataBase<I extends SchemaMetadata, S extends SchemaMetadataBase<I, S>> extends Schema<I, SchemaMetadataBase<I, S>> {

  @API(help="Version number of the Schema.")
  public int version;

  /** The simple schema (class) name, e.g. DeepLearningParametersV2, used in the schema metadata.  Must not be changed after creation (treat as final).  */
  @API(help="Simple name of the Schema.  NOTE: the schema_names form a single namespace.")
  public String name ;

  @API(help="Simple name of H2O type that this Schema represents.  Must not be changed after creation (treat as final).")
  public String type;

  @API(help="All the public fields of the schema", direction=API.Direction.OUTPUT)
  public FieldMetadataBase[] fields;

  @API(help="Documentation for the schema in Markdown format with GitHub extensions", direction=API.Direction.OUTPUT)
  String markdown;

  /**
   * Schema for the metadata for the field of a Schema.
   */
  public static class FieldMetadataBase<I extends FieldMetadata, S extends FieldMetadataBase<I, S>> extends Schema<I, S> {
    @API(help="Field name in the Schema", direction=API.Direction.OUTPUT)
    String name;

    @API(help="Type for this field", direction=API.Direction.OUTPUT)
    public String type;

    @API(help="Type for this field is itself a Schema.", direction=API.Direction.OUTPUT)
    public boolean is_schema;

    @API(help="Value for this field", direction=API.Direction.OUTPUT)
    public Iced value;

    @API(help="A short help description to appear alongside the field in a UI", direction=API.Direction.OUTPUT)
    String help;

    @API(help="The label that should be displayed for the field if the name is insufficient", direction=API.Direction.OUTPUT)
    String label;

    @API(help="Is this field required, or is the default value generally sufficient?", direction=API.Direction.OUTPUT)
    boolean required;

    @API(help="How important is this field?  The web UI uses the level to do a slow reveal of the parameters", values={"critical", "secondary", "expert"}, direction=API.Direction.OUTPUT)
    API.Level level;

    @API(help="Is this field an input, output or inout?", values={"INPUT", "OUTPUT", "INOUT"}, direction=API.Direction.OUTPUT)
    API.Direction direction;

    // The following are markers for *input* fields.

    @API(help="For enum-type fields the allowed values are specified using the values annotation;  this is used in UIs to tell the user the allowed values, and for validation", direction=API.Direction.OUTPUT)
    String[] values;

    @API(help="Should this field be rendered in the JSON representation?", direction=API.Direction.OUTPUT)
    boolean json;

    @API(help="For Vec-type fields this is the set of other Vec-type fields which must contain mutually exclusive values; for example, for a SupervisedModel the response_column must be mutually exclusive with the weights_column", direction=API.Direction.OUTPUT)
    String[] is_member_of_frames;

    @API(help="For Vec-type fields this is the set of Frame-type fields which must contain the named column; for example, for a SupervisedModel the response_column must be in both the training_frame and (if it's set) the validation_frame", direction=API.Direction.OUTPUT)
    String[] is_mutually_exclusive_with;

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
      this.fields[i++] = new FieldMetadataV1().fillFromImpl(f); // TODO: version!
    return this;
  }
}
