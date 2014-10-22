package water.api;

import water.api.SchemaMetadata.FieldMetadata;
import water.util.PojoUtils;

import java.util.ArrayList;

/**
 * Schema for the metadata for a Schema.
 */
public class SchemaMetadataBase extends Schema<SchemaMetadata, SchemaMetadataBase> {

  @API(help="All the public fields of the schema")
  public FieldMetadataBase[] fields;

  @API(help="Documentation for the schema in Markdown format with GitHub extensions")
  String markdown;

  /**
   * Schema for the metadata for the field of a Schema.
   */
  public static class FieldMetadataBase extends Schema<FieldMetadata, FieldMetadataBase> {
    @API(help="Field name in the Schema")
    String name;

    @API(help="Type for this field")
    public String type;

    @API(help="Type for this field is itself a Schema.")
    public boolean is_schema;

    @API(help="Value for this field")
    public String value;

    @API(help="A short help description to appear alongside the field in a UI")
    String help;

    @API(help="The label that should be displayed for the field if the name is insufficient")
    String label;

    @API(help="Is this field required, or is the default value generally sufficient?")
    boolean required;

    @API(help="How important is this field?  The web UI uses the level to do a slow reveal of the parameters")
    API.Level level;

    @API(help="Is this field an input, output or inout?")
    API.Direction direction;

    // The following are markers for *input* fields.

    @API(help="For enum-type fields the allowed values are specified using the values annotation;  this is used in UIs to tell the user the allowed values, and for validation")
    String[] values;

    @API(help="Should this field be rendered in the JSON representation?")
    boolean json;

    @Override
    public FieldMetadata createImpl() {
      return new FieldMetadata(this.name, this.type, this.is_schema, this.value, this.help, this.label, this.required, this.level, this.direction, this.values, this.json);
    }

    @Override
    public FieldMetadataBase fillFromImpl(FieldMetadata impl) {
      PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.CONSISTENT, new String[] {});
      return this;
    }
  } // FieldMetadataBase

  @Override
  public SchemaMetadata createImpl() {
    SchemaMetadata impl = new SchemaMetadata();
    impl.fields = new ArrayList<FieldMetadata>(this.fields.length);

    int i = 0;
    for (FieldMetadataBase s : this.fields)
      impl.fields.add(s.createImpl());
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
