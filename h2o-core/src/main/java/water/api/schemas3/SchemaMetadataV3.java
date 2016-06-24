package water.api.schemas3;

import water.api.API;
import water.api.SchemaMetadata;
import water.util.PojoUtils;

import java.util.ArrayList;


public class SchemaMetadataV3 extends SchemaV3<SchemaMetadata, SchemaMetadataV3> {

  @API(help="Version number of the Schema.")
  public int version;

  /**
   * The simple schema (class) name, e.g. DeepLearningParametersV2, used in the schema metadata.  Must not be
   * changed after creation (treat as final).
   */
  @API(help="Simple name of the Schema. NOTE: the schema_names form a single namespace.")
  public String name ;

  @API(help="[DEPRECATED] This field is always the same as name.", direction=API.Direction.OUTPUT)
  public String label;

  /**
   * The simple schema superclass name, e.g. ModelSchemaV3, used in the schema metadata.  Must not be changed after
   * creation (treat as final).
   */
  @API(help="Simple name of the superclass of the Schema.  NOTE: the schema_names form a single namespace.")
  public String superclass ;

  @API(help="Simple name of H2O type that this Schema represents. Must not be changed after creation (treat as final).")
  public String type;

  @API(help="All the public fields of the schema", direction=API.Direction.OUTPUT)
  public FieldMetadataV3[] fields;

  @API(help="Documentation for the schema in Markdown format with GitHub extensions", direction=API.Direction.OUTPUT)
  String markdown;


  public SchemaMetadataV3() {}
  public SchemaMetadataV3(SchemaMetadata impl) { super(impl); }

  @Override
  public SchemaMetadata createImpl() {
    return new SchemaMetadata();
  }

  @Override
  public SchemaMetadata fillImpl(SchemaMetadata impl) {
    impl.fields = new ArrayList<>(this.fields.length);
    int i = 0;
    for (FieldMetadataV3 s : this.fields)
      impl.fields.add(s.createImpl());
    return impl;
  }

  @Override
  public SchemaMetadataV3 fillFromImpl(SchemaMetadata impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.CONSISTENT, new String[] {"fields"});
    this.fields = new FieldMetadataV3[impl.fields.size()];
    int i = 0;
    for (SchemaMetadata.FieldMetadata f : impl.fields)
      this.fields[i++] = new FieldMetadataV3().fillFromImpl(f); // TODO: version!
    return this;
  }

}
