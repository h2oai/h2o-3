package water.api;

import hex.ModelBuilder;
import water.Iced;
import water.TypeMap;
import water.util.MarkdownBuilder;

import java.net.MalformedURLException;
import java.util.Map;

/**
 * Docs REST API handler, which provides endpoint handlers for the autogeneration of
 * Markdown (and in the future perhaps HTML and PDF) documentation for REST API endpoints
 * and payload entities (aka Schemas).
 */
public class MetadataHandler extends Handler {

  /** Return a list of all REST API Routes and a Markdown Table of Contents. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public MetadataV3 listRoutes(int version, MetadataV3 docs) {
    MarkdownBuilder builder = new MarkdownBuilder();
    builder.comment("Preview with http://jbt.github.io/markdown-editor");
    builder.heading1("REST API Routes Table of Contents");
    builder.hline();

    builder.tableHeader("HTTP method", "URI pattern", "Input schema", "Output schema", "Summary");

    docs.routes = new RouteBase[RequestServer.numRoutes()];
    int i = 0;
    for (Route route : RequestServer.routes()) {
      RouteBase schema = schemaForRoute(version, route);
      docs.routes[i] = schema;

      // ModelBuilder input / output schema hackery
      MetadataV3 look = new MetadataV3();
      look.routes = new RouteBase[1];
      look.routes[0] = schema;
      look.path = route._url;
      look.http_method = route._http_method;
      fetchRoute(version, look);

      schema.input_schema = look.routes[0].input_schema;
      schema.output_schema = look.routes[0].output_schema;

      builder.tableRow(
              route._http_method,
              route._url,
              Handler.getHandlerMethodInputSchema(route._handler_method).getSimpleName(),
              Handler.getHandlerMethodOutputSchema(route._handler_method).getSimpleName(),
              route._summary);
      i++;
    }

    docs.markdown = builder.toString();
    return docs;
  }

  /** Return the metadata for a REST API Route, specified either by number or path. */
  // Also called through reflection by RequestServer
  public MetadataV3 fetchRoute(int version, MetadataV3 docs) {
    Route route = null;
    if (docs.path != null && docs.http_method != null) {
      try {
        route = RequestServer.lookupRoute(new RequestUri(docs.http_method, docs.path));
      } catch (MalformedURLException e) {
        route = null;
      }
    } else {
      // Linear scan for the route, plus each route is asked for in-order
      // during doc-gen leading to an O(n^2) execution cost.
      if (docs.path != null)
        try { docs.num = Integer.parseInt(docs.path); }
        catch (NumberFormatException e) { /* path is not a number, it's ok */ }
      if (docs.num >= 0 && docs.num < RequestServer.numRoutes())
        route = RequestServer.routes().get(docs.num);
      // Crash-n-burn if route not found (old code thru an AIOOBE), so we
      // something similarly bad.
      docs.routes = new RouteBase[]{(RouteBase)SchemaServer.schema(version, Route.class).fillFromImpl(route)};
    }
    if (route == null) return null;

    Schema sinput, soutput;
    if( route._handler_class.equals(water.api.ModelBuilderHandler.class) ||
        route._handler_class.equals(water.api.GridSearchHandler.class)) {
      // GridSearchHandler uses the same logic as ModelBuilderHandler because there are no separate
      // ${ALGO}GridSearchParametersV3 classes, instead each field in ${ALGO}ParametersV3 is marked as either gridable
      // or not.
      String ss[] = route._url.split("/");
      String algoURLName = ss[3]; // {}/{3}/{ModelBuilders}/{gbm}/{parameters}
      String algoName = ModelBuilder.algoName(algoURLName); // gbm -> GBM; deeplearning -> DeepLearning
      String schemaDir = ModelBuilder.schemaDirectory(algoURLName);
      int version2 = Integer.valueOf(ss[1]);
      try {
        String inputSchemaName = schemaDir + algoName + "V" + version2;  // hex.schemas.GBMV3
        sinput = (Schema) TypeMap.getTheFreezableOrThrow(TypeMap.onIce(inputSchemaName));
      } catch (java.lang.ClassNotFoundException e) {
        // Not very pretty, but for some routes such as /99/Grid/glm we want to map to GLMV3 (because GLMV99 does not
        // exist), yet for others such as /99/Grid/svd we map to SVDV99 (because SVDV3 does not exist).
        sinput = (Schema) TypeMap.theFreezable(TypeMap.onIce(schemaDir + algoName + "V3"));
      }
      sinput.init_meta();
      soutput = sinput;
    } else {
      sinput  = Schema.newInstance(Handler.getHandlerMethodInputSchema (route._handler_method));
      soutput = Schema.newInstance(Handler.getHandlerMethodOutputSchema(route._handler_method));
    }
    docs.routes[0].input_schema = sinput.getClass().getSimpleName();
    docs.routes[0].output_schema = soutput.getClass().getSimpleName();
    docs.routes[0].markdown = route.markdown(sinput,soutput).toString();
    return docs;
  }

  /** Fetch the metadata for a Schema by its full internal classname, e.g. "hex.schemas.DeepLearningV2.DeepLearningParametersV2".  TODO: Do we still need this? */
  @Deprecated
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public MetadataV3 fetchSchemaMetadataByClass(int version, MetadataV3 docs) {
    docs.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw an exception if the classname isn't found:
    SchemaMetadataBase meta = (SchemaMetadataBase)SchemaServer.schema(version, SchemaMetadata.class).fillFromImpl(SchemaMetadata.createSchemaMetadata(docs.classname));
    docs.schemas[0] = meta;
    return docs;
  }

  /** Fetch the metadata for a Schema by its simple Schema name (e.g., "DeepLearningParametersV2"). */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public MetadataV3 fetchSchemaMetadata(int version, MetadataV3 docs) {
    if ("void".equals(docs.schemaname)) {
      docs.schemas = new SchemaMetadataBase[0];
      return docs;
    }

    docs.schemas = new SchemaMetadataBase[1];
    // NOTE: this will throw an exception if the classname isn't found:
    Schema schema = Schema.newInstance(docs.schemaname);
    // get defaults
    try {
      Iced impl = (Iced) schema.getImplClass().newInstance();
      schema.fillFromImpl(impl);
    }
    catch (Exception e) {
      // ignore if create fails; this can happen for abstract classes
    }
    SchemaMetadataBase meta = (SchemaMetadataBase)SchemaServer.schema(version, SchemaMetadata.class).fillFromImpl(new SchemaMetadata(schema));
    docs.schemas[0] = meta;
    return docs;
  }

  /** Fetch the metadata for all the Schemas. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public MetadataV3 listSchemas(int version, MetadataV3 docs) {
    Map<String, Class<? extends Schema>> ss = SchemaServer.schemas();
    docs.schemas = new SchemaMetadataBase[ss.size()];

    // NOTE: this will throw an exception if the classname isn't found:
    int i = 0;
    for (Class<? extends Schema> schema_class : ss.values()) {
      // No hardwired version! YAY!  FINALLY!

      Schema schema = Schema.newInstance(schema_class);
      // get defaults
      try {
        Iced impl = (Iced) schema.getImplClass().newInstance();
        schema.fillFromImpl(impl);
      }
      catch (Exception e) {
        // ignore if create fails; this can happen for abstract classes
      }

      docs.schemas[i++] = (SchemaMetadataBase)SchemaServer.schema(version, SchemaMetadata.class).fillFromImpl(new SchemaMetadata(schema));
    }
    return docs;
  }


  private RouteBase schemaForRoute(int version, Route route) {
    Schema<Route, ?> schema = SchemaServer.schema(version, Route.class);
    return (RouteBase) schema.fillFromImpl(route);
  }
}
