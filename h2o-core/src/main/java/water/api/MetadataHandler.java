package water.api;

import water.util.MarkdownBuilder;

import java.util.Map;

/*
 * Docs REST API handler, which provides endpoint handlers for the autogeneration of
 * Markdown (and in the future perhaps HTML and PDF) documentation for REST API endpoints
 * and payload entities (aka Schemas).
 */
public class MetadataHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return a list of all REST API Routes and a Markdown Table of Contents. */
  public MetadataV3 listRoutes(int version, MetadataV3 docs) {
    MarkdownBuilder builder = new MarkdownBuilder();
    builder.comment("Preview with http://jbt.github.io/markdown-editor");
    builder.heading1("REST API Routes Table of Contents");
    builder.hline();

    builder.tableHeader("HTTP method", "URI pattern", "Input schema", "Output schema", "Summary");

    docs.routes = new RouteBase[RequestServer.numRoutes()];
    int i = 0;
    for (Route route : RequestServer.routes()) {
      docs.routes[i++] = (RouteBase)Schema.schema(version, Route.class).fillFromImpl(route);

      builder.tableRow(
              route._http_method,
              route._url_pattern.toString().replace("(?<", "{").replace(">.*)", "}"),
              Handler.getHandlerMethodInputSchema(route._handler_method).getSimpleName(),
              Handler.getHandlerMethodOutputSchema(route._handler_method).getSimpleName(),
              route._summary);
    }

    docs.markdown = builder.toString();
    return docs;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return the metadata for a REST API Route, specified either by number or path. */
  public MetadataV3 fetchRoute(int version, MetadataV3 docs) {
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
  public MetadataV3 fetchSchemaMetadataByClass(int version, MetadataV3 docs) {
    docs.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw an exception if the classname isn't found:
    SchemaMetadataBase meta = (SchemaMetadataBase)Schema.schema(version, SchemaMetadata.class).fillFromImpl(SchemaMetadata.createSchemaMetadata(docs.classname));
    docs.schemas[0] = meta;
    return docs;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Fetch the metadata for a Schema by its simple Schema name (e.g., "DeepLearningParametersV2"). */
  public MetadataV3 fetchSchemaMetadata(int version, MetadataV3 docs) {
    if ("void".equals(docs.schemaname)) {
      docs.schemas = new SchemaMetadataBase[0];
      return docs;
    }

    docs.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw an exception if the classname isn't found:
    SchemaMetadataBase meta = (SchemaMetadataBase)Schema.schema(version, SchemaMetadata.class).fillFromImpl(new SchemaMetadata(Schema.newInstance(docs.schemaname)));
    docs.schemas[0] = meta;
    return docs;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Fetch the metadata for all the Schemas. */
  public MetadataV3 listSchemas(int version, MetadataV3 docs) {
    Map<String, Class<? extends Schema>> ss = Schema.schemas();
    docs.schemas = new SchemaMetadataBase[ss.size()];

    // NOTE: this will throw an exception if the classname isn't found:
    int i = 0;
    for (Class<? extends Schema> schema_class : ss.values()) {
      // No hardwired version! YAY!  FINALLY!
      docs.schemas[i++] = (SchemaMetadataBase)Schema.schema(version, SchemaMetadata.class).fillFromImpl(new SchemaMetadata(Schema.newInstance(schema_class)));
    }
    return docs;
  }
}
