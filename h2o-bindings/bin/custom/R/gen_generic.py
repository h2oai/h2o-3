extensions = dict(
    required_params=[],  # empty to override defaults in gen_defaults
    validate_required_params="""
# Required args: either model_key or path
if (is.null(model_key) && is.null(path)) stop("argument 'model_key' or 'path' must be provided")
""",
    set_required_params="",
    skip_default_set_params_for=["model_id", "path", "model_key"],
    set_params="""
  if (!is.null(model_id)) {
    parms$model_id <- model_id
  } else if(!missing(path)) {
    splited <- strsplit(path, "/")[[1]]
    parms$model_id <- strsplit(splited[length(splited)], '\\\\.')[[1]][1]
  }
  if (!missing(model_key)) {
    parms$model_key <- model_key
  } else if (!missing(path)) {
    parms$path <- path
  }
"""
)


doc = dict(
    preamble="""
Imports a generic model into H2O. Such model can be used then used for scoring and obtaining
additional information about the model. The imported model has to be supported by H2O.
""",
    examples="""
# library(h2o)
# h2o.init()

# generic_model <- h2o.genericModel(path="/path/to/model.zip", model_id="my_model")
# predictions <- h2o.predict(generic_model, dataset)
"""
)
