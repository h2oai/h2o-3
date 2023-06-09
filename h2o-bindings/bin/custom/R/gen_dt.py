extensions = dict(
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 
                                 'max_confusion_matrix_size', 'distribution', 'offset_column'],
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
""",
)


doc = dict(
    preamble="""
Build a Decision Tree model

Builds a Decision Tree model on an H2OFrame.
""",
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

# Train the DT model
h2o_dt <- h2o.decision_tree(x = predictors, y = response, training_frame = data, seed = 1234)
"""
)
