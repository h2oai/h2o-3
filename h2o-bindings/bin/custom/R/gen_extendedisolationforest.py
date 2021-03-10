extensions = dict(
    required_params=['training_frame', 'x'],
    validate_required_params="",
    set_required_params="""
parms$training_frame <- training_frame
if(!missing(x))
  parms$ignored_columns <- .verify_datacols(training_frame, x)$cols_ignore
""",
)

doc = dict(
    preamble="""
Trains an Extended Isolation Forest model
""",
    params=dict(
        x="""A vector containing the \code{character} names of the predictors in the model."""
    ),
    examples="""
library(h2o)
h2o.init()

# Import the prostate dataset
p <- h2o.importFile(path="https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")

# Set the predictors
predictors <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")

# Build an Extended Isolation forest model
model <- h2o.extendedIsolationForest(x = predictors,
                                     training_frame = p,
                                     model_id = "eif.hex",
                                     ntrees = 100,
                                     sample_size = 256,
                                     extension_level = 8)

# Calculate score
score <- h2o.predict(model, p)
anomaly_score <- score$anomaly_score
"""
)

