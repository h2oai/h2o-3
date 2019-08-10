rest_api_version = 99


def update_param(name, param):
    if name == 'metalearner_params':
        param['default_value'] = None
        return param
    if name == 'base_models':
        param['type'] = 'list'
        param['default_value'] = None
        return param
    return None  # param untouched


extensions = dict(
    frame_params=dict(training_frame=True, validation_frame=False, blending_frame=False),
    validate_params="""
# Get the base models from model IDs (if any) that will be used for constructing model summary
if(!is.list(base_models) && is.vector(x)) {
   base_models <- as.list(base_models)
}
baselearners <- lapply(base_models, function(base_model) {
  if (is.character(base_model))
    base_model <- h2o.getModel(base_model)
  base_model
})

# Get base model IDs that will be passed to REST API later
if (length(base_models) == 0) stop('base_models is empty')

# If base_models contains models instead of ids, replace with model id
for (i in 1:length(base_models)) {
  if (inherits(base_models[[i]], 'H2OModel')) {
    base_models[[i]] <- base_models[[i]]@model_id
  }
}
""",
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
parms$response_column <- args$y
""",
    skip_default_set_params_for=['training_frame', 'response_column', 'metalearner_params'],
    set_params="""
if (!missing(metalearner_params))
    parms$metalearner_params <- as.character(toJSON(metalearner_params, pretty = TRUE))
""",
    with_model="""
# Convert metalearner_params back to list if not NULL
if (!missing(metalearner_params)) {
    model@parameters$metalearner_params <- list(fromJSON(model@parameters$metalearner_params))[[1]] #Need the `[[ ]]` to avoid a nested list
}
model@model$model_summary <- capture.output({

  print_ln <- function(...) cat(..., sep = "\\n")

  print_ln(paste0("Number of Base Models: ", length(baselearners)))
  print_ln("\\nBase Models (count by algorithm type):")
  print(table(unlist(lapply(baselearners, function(baselearner) baselearner@algorithm))))


  print_ln("\\nMetalearner:\\n")
  print_ln(paste0(
    "Metalearner algorithm: ",
    ifelse(length(metalearner_algorithm) > 1, "glm", metalearner_algorithm)))

  if (metalearner_nfolds != 0) {
    print_ln("Metalearner cross-validation fold assignment:")
    print_ln(paste0(
      "  Fold assignment scheme: ",
      ifelse(length(metalearner_fold_assignment) > 1, "Random", metalearner_fold_assignment)))
    print_ln(paste0("  Number of folds: ", metalearner_nfolds))
    print_ln(paste0(
      "  Fold column: ",
      ifelse(is.null(metalearner_fold_column), "NULL", metalearner_fold_column )))
  }

  if (!missing(metalearner_params))
    print_ln(paste0("Metalearner hyperparameters: ", parms$metalearner_params))

})
class(model@model$model_summary) <- "h2o.stackedEnsemble.summary"
"""
)

doc = dict(
    preamble="""
Builds a Stacked Ensemble

Build a stacked ensemble (aka. Super Learner) using the H2O base
learning algorithms specified by the user.
""",
    params=dict(
        x="""
(Optional). A vector containing the names or indices of the predictor variables to use in building the model.
If x is missing, then all columns except y are used.  Training frame is used only to compute ensemble training metrics.
""",
        seed="""
Seed for random numbers; passed through to the metalearner algorithm. Defaults to -1 (time-based random number).
"""
    ),
    examples="""
# See example R code here:
# http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/stacked-ensembles.html
"""
)