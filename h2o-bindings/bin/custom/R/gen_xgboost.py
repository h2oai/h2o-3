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

#' XGBoost feature interactions and importance, leaf statistics and split value histograms in a tabular form.
#'
#' Metrics:
#' Gain - Total gain of each feature or feature interaction.
#' FScore - Amount of possible splits taken on a feature or feature interaction.
#' wFScore - Amount of possible splits taken on a feature or feature interaction weighed by 
#' the probability of the splits to take place.
#' Average wFScore - wFScore divided by FScore.
#' Average Gain - Gain divided by FScore.
#' Expected Gain - Total gain of each feature or feature interaction weighed by the probability to gather the gain.
#' Average Tree Index
#' Average Tree Depth
#'
#' @param model A trained xgboost model.
#' @param max_interaction_depth Upper bound for extracted feature interactions depth. Defaults to 100.
#' @param max_tree_depth Upper bound for tree depth. Defaults to 100.
#' @param max_deepening Upper bound for interaction start deepening (zero deepening => interactions 
#' starting at root only). Defaults to -1.
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' boston <- h2o.importFile(
#'        "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv",
#'         destination_frame="boston"
#'         )
#' boston_xgb <- h2o.xgboost(training_frame = boston, y = "medv", seed = 1234)
#' feature_interactions <- h2o.xgboost.feature_interaction(boston_xgb)
#' }
#' @export
h2o.xgboost.feature_interaction <- function(model, max_interaction_depth = 100, max_tree_depth = 100, max_deepening = -1) {
    parms <- list()
    parms$model_id <- model@model_id
    parms$max_interaction_depth <- max_interaction_depth
    parms$max_tree_depth <- max_tree_depth
    parms$max_deepening <- max_deepening

    json <- .h2o.doSafePOST(urlSuffix = "FeatureInteraction", parms=parms)
    source <- .h2o.fromJSON(jsonlite::fromJSON(json,simplifyDataFrame=FALSE))

    return(source$feature_interaction)
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
