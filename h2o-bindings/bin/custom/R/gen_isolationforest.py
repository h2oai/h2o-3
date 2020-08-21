def update_param(name, param):
    if name == 'stopping_metric':
        param['values'] = ['AUTO', 'anomaly_score']
        return param
    return None  # param untouched

extensions = dict(
    required_params=['training_frame', 'x'],
    validate_required_params="",
    set_required_params="""
parms$training_frame <- training_frame
if(!missing(x))
  parms$ignored_columns <- .verify_datacols(training_frame, x)$cols_ignore
""",
    skip_default_set_params_for=['training_frame', 'ignored_columns'],
)

doc = dict(
    preamble="""
Trains an Isolation Forest model
""",
    params=dict(
        x="""A vector containing the \code{character} names of the predictors in the model."""
    ),
    examples="""
library(h2o)
h2o.init()

# Import the cars dataset
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
cars <- h2o.importFile(f)

# Set the predictors
predictors <- c("displacement", "power", "weight", "acceleration", "year")

# Train the IF model
cars_if <- h2o.isolationForest(x = predictors, training_frame = cars,
                               seed = 1234, stopping_metric = "anomaly_score",
                               stopping_rounds = 3, stopping_tolerance = 0.1)
"""
)
