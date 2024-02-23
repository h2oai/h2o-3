
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
  model$transformers <- unlist(lapply(model$transformers, function(k) .h2o.fetch_datatransformer(k$name)))
  # class(model) <- "H2OPipeline"
  return(model)
}
.h2o.fetch_datatransformer <- function(id) {
  resp <- .h2o.__remoteSend(method="GET", h2oRestApiVersion=3, page=paste0("Pipeline/DataTransformer/", id))
  tr <- new("H2ODataTransformer", id=resp$key$name, name=resp$name, description=resp$description)
  return (tr)
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
