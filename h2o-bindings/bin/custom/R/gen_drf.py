extensions = dict(
    extra_params=[('verbose', 'FALSE')],
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'max_confusion_matrix_size',
                                 'distribution', 'offset_column'],
    set_params="""
if (!missing(distribution)) {
  warning("Argument distribution is deprecated and has no use for Random Forest.")
  parms$distribution <- 'AUTO'
}
if (!missing(offset_column)) {
  warning("Argument offset_column is deprecated and has no use for Random Forest.")
  parms$offset_column <- NULL
}
"""
)


doc = dict(
    preamble="""
Build a Random Forest model

Builds a Random Forest model on an H2OFrame.
""",
    params=dict(
        distribution="Distribution. This argument is deprecated and has no use for Random Forest.",
        offset_column="Offset column. This argument is deprecated and has no use for Random Forest.",
        verbose="""
\code{Logical}. Print scoring history to the console (Metrics per tree). Defaults to FALSE.
"""
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

# Import the cars dataset
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
cars <- h2o.importFile(f)

# Set predictors and response; set response as a factor
cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
predictors <- c("displacement", "power", "weight", "acceleration", "year")
response <- "economy_20mpg"

# Train the DRF model
cars_drf <- h2o.randomForest(x = predictors, y = response,
                            training_frame = cars, nfolds = 5,
                            seed = 1234)
"""
)
