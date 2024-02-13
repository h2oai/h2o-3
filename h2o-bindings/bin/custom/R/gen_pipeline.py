
extensions = dict(
    required_params=[],
    frame_params=[],
    validate_required_params="",
    set_required_params="",
    module="""
.h2o.fill_pipeline <- function(model, parameters, allparams) {
  if (!is.null(model$estimator)) {
    model$estimator_model <- h2o.getModel(model$estimator$name)
  } else {
    model$estimator_model <- NULL
  }
  model$transformers <- unlist(lapply(model$transformers, function(dt) new("H2ODataTransformer", id=dt$id, description=dt$description)))
  # class(model) <- "H2OPipeline"
  return(model)
}
"""
)

doc = dict(
    preamble="""
Build a pipeline model given a list of transformers and a final model.

Currently R model pipelines, as produced by AutoML for example, 
are only available as read-only models that can not be constructed and trained directly by the end-user.  
""",
)
