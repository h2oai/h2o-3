extensions = dict(
    extra_params=[('verbose', 'FALSE')],
    module="""
#' Determines whether an XGBoost model can be built
#'
#' Ask the H2O server whether a XGBoost model can be built. (Depends on availability of native backend.)
#' Returns True if a XGBoost model can be built, or False otherwise.
#' @export
h2o.xgboost.available <- function() {
    if (!("XGBoost" %in% h2o.list_core_extensions())) {
        print("Cannot build a XGboost model - no backend found.")
        return(FALSE)
    } else {
        return(TRUE)
    }
}
""",
)

doc = dict(
    preamble="""
Build an eXtreme Gradient Boosting model

Builds a eXtreme Gradient Boosting model using the native XGBoost backend.
""",
    params=dict(
        verbose="""
\code{Logical}. Print scoring history to the console (Metrics per tree). Defaults to FALSE.
"""
    ),
    examples="""
library(h2o)
h2o.init()

# Import the titanic dataset
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
titanic <- h2o.importFile(f)

# Set predictors and response; set response as a factor
titanic['survived'] <- as.factor(titanic['survived'])
predictors <- setdiff(colnames(titanic), colnames(titanic)[2:3])
response <- "survived"

# Split the dataset into train and valid
splits <- h2o.splitFrame(data =  titanic, ratios = .8, seed = 1234)
train <- splits[[1]]
valid <- splits[[2]]

# Train the XGB model
titanic_xgb <- h2o.xgboost(x = predictors, y = response,
                           training_frame = train, validation_frame = valid,
                           booster = "dart", normalize_type = "tree",
                           seed = 1234)
"""
)
