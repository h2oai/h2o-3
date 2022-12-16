
extensions = dict(
    required_params=[],
    frame_params=[],
    validate_required_params="",
    set_required_params="",
    module="""
.h2o.fill_pipeline<- function(model, parameters, allparams) {
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
