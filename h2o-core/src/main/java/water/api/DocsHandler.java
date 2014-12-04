package water.api;

import java.util.Map;

/*
 * Docs REST API handler, which provides endpoint handlers for the autogeneration of
 * Markdown (and in the future perhaps HTML and PDF) documentation for REST API endpoints
 * and payload entities (aka Schemas).
 */
public class DocsHandler extends Handler {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return 1; }


  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return a list of all REST API Routes. */
  public DocsBase listRoutes(int version, DocsV1 docs) {
    docs.routes = new RouteBase[RequestServer.numRoutes()];
    int i = 0;
    for (Route route : RequestServer.routes()) {
      docs.routes[i++] = (RouteBase)Schema.schema(version, Route.class).fillFromImpl(route);
    }
    return docs;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return the metadata for a REST API Route, specified either by number or path. */
  public DocsBase fetchRoute(int version, DocsV1 docs) {
    // DocsPojo docsPojo = docs.createAndFillImpl();

    Route route = null;
    if (null != docs.path && null != docs.http_method) {
      route = RequestServer.lookup(docs.http_method, docs.path);
    } else {
      int i = 0;
      for (Route r : RequestServer.routes()) {
        if (i++ == docs.num) {
          route = r;
          break;
        }
      }

      docs.routes = new RouteBase[null == route ? 0 : 1];
      if (null != route) {
        docs.routes[0] = (RouteBase)Schema.schema(version, Route.class).fillFromImpl(route);
      }
    }
    docs.routes[0].markdown = route.markdown(null).toString();
    return docs;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  @Deprecated
  /** Fetch the metadata for a Schema by its full internal classname, e.g. "hex.schemas.DeepLearningV2.DeepLearningParametersV2".  TODO: Do we still need this? */
  public DocsBase fetchSchemaMetadataByClass(int version, DocsV1 docs) {
    docs.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw IllegalArgumentException if the classname isn't found:
    SchemaMetadataBase meta = (SchemaMetadataBase)Schema.schema(version, SchemaMetadata.class).fillFromImpl(SchemaMetadata.createSchemaMetadata(docs.classname));
    docs.schemas[0] = meta;
    return docs;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Fetch the metadata for a Schema by its simple Schema name (e.g., "DeepLearningParametersV2"). */
  public DocsBase fetchSchemaMetadata(int version, DocsV1 docs) {
    docs.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw IllegalArgumentException if the classname isn't found:
    SchemaMetadataBase meta = (SchemaMetadataBase)Schema.schema(version, SchemaMetadata.class).fillFromImpl(new SchemaMetadata(Schema.schema(docs.schemaname)));
    docs.schemas[0] = meta;
    return docs;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Fetch the metadata for all the Schemas. */
  public DocsBase listSchemas(int version, DocsV1 docs) {
    Map<String, Class<? extends Schema>> ss = Schema.schemas();
    docs.schemas = new SchemaMetadataBase[ss.size()];

    // NOTE: this will throw IllegalArgumentException if the classname isn't found:
    int i = 0;
    for (Class<? extends Schema> schema_class : ss.values()) {
      // No hardwired version! YAY!  FINALLY!
      docs.schemas[i++] = (SchemaMetadataBase)Schema.schema(version, SchemaMetadata.class).fillFromImpl(new SchemaMetadata(Schema.schema(schema_class)));
    }
    return docs;
  }
}
