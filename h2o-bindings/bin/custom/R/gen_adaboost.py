def update_param(name, param):
    if name == 'weak_learner_params':
        param['default_value'] = None
        return param
    return None  # param untouched

extensions = dict(
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 
                                 'max_confusion_matrix_size', 'distribution', 'offset_column', 'weak_learner_params'],
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
""",
    set_params="""
if (!missing(weak_learner_params))
    parms$weak_learner_params <- as.character(toJSON(weak_learner_params, pretty = TRUE, auto_unbox = TRUE))
"""
)

doc = dict(
    preamble="""
Build an AdaBoost model

Builds an AdaBoost model on an H2OFrame.
""",
    params=dict(
        weak_learner_params="Customized parameters for the weak_learner algorithm. E.g list(ntrees=3, max_depth=2, histogram_type='UniformAdaptive'))",
    ),
    returns="""
Creates a \linkS4class{H2OModel} object of the right type.
""",
    seealso="""
\code{\link{predict.H2OModel}} for prediction
""",
    examples="""
library(h2o)
h2o.init()

# Import the airlines dataset
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv"
data <- h2o.importFile(f)

# Set predictors and response; set response as a factor
data["CAPSULE"] <- as.factor(data["CAPSULE"])
predictors <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
response <- "CAPSULE"

# Train the AdaBoost model
h2o_adaboost <- h2o.adaBoost(x = predictors, y = response, training_frame = data, seed = 1234)
"""
)
