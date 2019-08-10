extensions = dict(
    required_params=dict(),  # empty to override defaults in gen_defaults
    validate_required_params="""
# Required args: either model_key or path
if (is.null(model_key) && is.null(path)) stop("argument 'model_key' or 'path' must be provided")
""",
    set_required_params="",
)


doc = dict(
    preamble="""
Imports a generic model into H2O. Such model can be used then used for scoring and obtaining
additional information about the model. The imported model has to be supported by H2O.
""",
    examples="""
library(h2o)
h2o.init()

generic_model <- h2o.genericModel("/path/to/model.zip")
predictions <- h2o.predict(generic_model, dataset)
"""
)
