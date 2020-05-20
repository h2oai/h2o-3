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
    frame_params=['training_frame', 'validation_frame', 'blending_frame'],
    validate_frames="""
training_frame <- .validate.H2OFrame(training_frame, required=is.null(blending_frame))
validation_frame <- .validate.H2OFrame(validation_frame, required=FALSE)
blending_frame <- .validate.H2OFrame(blending_frame, required=is.null(training_frame))
if (is.null(training_frame)) training_frame <- blending_frame  # guarantee presence of default metrics
""",
    validate_params="""
# Get the base models from model IDs (if any) that will be used for constructing model summary
if(!is.list(base_models) && is.vector(x)) {
  base_models <- if (inherits(base_models, "H2OGrid")) list(base_models) else as.list(base_models)
}

# Get base model IDs that will be passed to REST API later
if (length(base_models) == 0) stop('base_models is empty')

# If base_models contains models instead of ids, replace with model id
for (i in 1:length(base_models)) {
  if (inherits(base_models[[i]], c('H2OModel', 'H2OGrid'))) {
    base_models[[i]] <- h2o.keyof(base_models[[i]])
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
model@model <- .h2o.fill_stackedensemble(model@model, model@parameters, model@allparams)

# Get the actual models (that were potentially expanded from H2OGrid on the backend)
baselearners <- lapply(model@model$base_models, function(base_model) {
  if (is.character(base_model))
    base_model <- h2o.getModel(base_model)
  base_model
})

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
""",
    module="""
.h2o.fill_stackedensemble <- function(model, parameters, allparams) {
  # Store base models for the Stacked Ensemble in user-readable form
  model$base_models <- unlist(lapply(parameters$base_models, function (base_model) base_model$name))
  model$metalearner_model <- h2o.getModel(model$metalearner$name)
  return(model)
}
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
library(h2o)
h2o.init()

# Import a sample binary outcome train/test set
train <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
test <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

# Identify predictors and response
y <- "response"
x <- setdiff(names(train), y)

# For binary classification, response should be a factor
train[, y] <- as.factor(train[, y])
test[, y] <- as.factor(test[, y])

# Number of CV folds
nfolds <- 5

# Train & Cross-validate a GBM
my_gbm <- h2o.gbm(x = x,
                  y = y,
                  training_frame = train,
                  distribution = "bernoulli",
                  ntrees = 10,
                  max_depth = 3,
                  min_rows = 2,
                  learn_rate = 0.2,
                  nfolds = nfolds,
                  fold_assignment = "Modulo",
                  keep_cross_validation_predictions = TRUE,
                  seed = 1)

# Train & Cross-validate a RF
my_rf <- h2o.randomForest(x = x,
                          y = y,
                          training_frame = train,
                          ntrees = 50,
                          nfolds = nfolds,
                          fold_assignment = "Modulo",
                          keep_cross_validation_predictions = TRUE,
                          seed = 1)

# Train a stacked ensemble using the GBM and RF above
ensemble <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                model_id = "my_ensemble_binomial",
                                base_models = list(my_gbm, my_rf))
"""
)
