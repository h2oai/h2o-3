package water.api;

import water.Iced;

import java.util.Map;

/*
 * Docs REST API handler, which provides endpoint handlers for the autogeneration of
 * Markdown (and in the future perhaps HTML and PDF) documentation for REST API endpoints
 * and payload entities (aka Schemas).
 */
public class DocsHandler<I extends DocsHandler.DocsPojo, S extends DocsBase<I, S>> extends Handler<I, DocsBase<I, S>> {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return 1; }

  /**
   * In some cases the REST API can be directly backed by a back-end Iced class.
   * In this synthetic docs we don't have a domain class that would serve
   * that purpose, so we define one here.
   */
  protected static final class DocsPojo extends Iced {
    // Inputs
    String http_method; // GET, etc.
    int num;
    String path;
    String classname;
    String schemaname;

    // Outputs
    Route[] routes;
    SchemaMetadata[] schemas;
  }


  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return a list of all REST API Routes. */
  public DocsBase listRoutes(int version, DocsPojo docsPojo) {
    docsPojo.routes = new Route[RequestServer.numRoutes()];
    int i = 0;
    for (Route route : RequestServer.routes()) {
      docsPojo.routes[i++] = route;
    }
    return schema(version).fillFromImpl(docsPojo);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return the metadata for a REST API Route, specified either by number or path. */
  public DocsBase fetchRoute(int version, DocsPojo docsPojo) {
    Route route = null;
    if (null != docsPojo.path && null != docsPojo.http_method) {
      route = RequestServer.lookup(docsPojo.http_method, docsPojo.path);
    } else {
      int i = 0;
      for (Route r : RequestServer.routes()) {
        if (i++ == docsPojo.num) {
          route = r;
          break;
        }
      }

      docsPojo.routes = new Route[null == route ? 0 : 1];
      if (null != route) {
        docsPojo.routes[0] = route;
      }
    }
    DocsBase result = schema(version).fillFromImpl(docsPojo);
    result.routes[0].markdown = route.markdown(null).toString();
    return result;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  @Deprecated
  /** Fetch the metadata for a Schema by its full internal classname, e.g. "hex.schemas.DeepLearningV2.DeepLearningParametersV2".  TODO: Do we still need this? */
  public DocsBase fetchSchemaMetadataByClass(int version, DocsPojo docsPojo) {
    DocsBase result = schema(version).fillFromImpl(docsPojo);
    result.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw IllegalArgumentException if the classname isn't found:
    SchemaMetadataBase meta = new SchemaMetadataV1().fillFromImpl(SchemaMetadata.createSchemaMetadata(docsPojo.classname));
    result.schemas[0] = meta;
    return result;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Fetch the metadata for a Schema by its simple Schema name (e.g., "DeepLearningParametersV2"). */
  public DocsBase fetchSchemaMetadata(int version, DocsPojo docsPojo) {
    DocsBase result = schema(version).fillFromImpl(docsPojo);
    result.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw IllegalArgumentException if the classname isn't found:
    SchemaMetadataBase meta = new SchemaMetadataV1().fillFromImpl(new SchemaMetadata(Schema.schema(docsPojo.schemaname)));
    result.schemas[0] = meta;
    return result;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Fetch the metadata for all the Schemas. */
  public DocsBase listSchemas(int version, DocsPojo docsPojo) {
    // TODO: remove this temporary hack once we register the schemas by following the schemas used by the handlers:
    Schema.register(SchemaMetadataV1.class);

    DocsBase result = schema(version).fillFromImpl(docsPojo);
    Map<String, Class<? extends Schema>> ss = Schema.schemas();
    result.schemas = new SchemaMetadataBase[ss.size()];

    // NOTE: this will throw IllegalArgumentException if the classname isn't found:
    int i = 0;
    for (Class<? extends Schema> schema_class : ss.values()) {
      // No hardwired version! YAY!  FINALLY!
      result.schemas[i++] = (SchemaMetadataBase)Schema.schema(version, SchemaMetadata.class).fillFromImpl(new SchemaMetadata(Schema.schema(schema_class)));
    }
    return result;
  }


  @Override protected DocsBase schema(int version) {
    if (version == 1)
      return new DocsV1();
    else
      throw new IllegalArgumentException("Bad version for Docs schema: " + version);
  } // schema()

}
