package water.automl.api;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import water.api.Handler;
import water.api.Route;
import water.api.Schema;
import water.api.schemas3.JobV3;
import water.automl.api.schemas3.AutoMLBuildSpecV99;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class AutoMLBuilderHandler extends Handler {
  // TODO: this is a temporary hack.  There is a race here, since there's only one instance
  // of the handler.  RequestServer.serve() should either create a new instance each time or
  // pass down a request number so that we don't have a race on this state.
  Map<String, Object> postBody;

  @Override
  public Schema handle(int version, Route route, Properties parms, String post_body) throws Exception {
    this.postBody = (new com.google.gson.Gson()).fromJson(post_body, HashMap.class);
    return super.handle(version, route, parms, post_body);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLBuildSpecV99 build(int version, AutoMLBuildSpecV99 buildSpecSchema) {
    AutoMLBuildSpec buildSpec = buildSpecSchema.createAndFillImpl();

    if (this.postBody.containsKey("build_control")) {
      Map<String, Object> build_control = (Map)this.postBody.get("build_control");
      if (build_control.containsKey("stopping_criteria")) {
        Map<String, Object> stopping_criteria = (Map)build_control.get("stopping_criteria");

        if (stopping_criteria.containsKey("stopping_tolerance")) {
          // double stopping_tolerance = (double)stopping_criteria.get("stopping_tolerance");
        } else {
          // default
          buildSpec.build_control.stopping_criteria.set_stopping_tolerance(-1); // marker value which means "default"
        }
      }
    }

    AutoML aml;
    aml = AutoML.startAutoML(buildSpec);
    buildSpecSchema.job = new JobV3().fillFromImpl(aml.job());
    return buildSpecSchema;
  }
}
