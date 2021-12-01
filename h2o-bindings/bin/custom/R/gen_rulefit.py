extensions = dict(
    extensions = dict(
        validate_params="""
# Required maps for different names params, including deprecated params
.gbm.map <- c("x" = "ignored_columns",
              "y" = "response_column")
"""
    ),
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
""",
    module="""
#' Evaluates validity of the given rules on the given data. Returns a frame with a column per each input rule id, 
#' representing a flag whether given rule is applied to the observation or not.
#'
#' @param model A trained rulefit model.  
#' @param frame A frame on which rule validity is to be evaluated
#' @param rule_ids Rule ids to be evaluated against the frame
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' titanic <- h2o.importFile(
#'  "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
#' )
#' response = "survived"
#' predictors <- c("age", "sibsp", "parch", "fare", "sex", "pclass")
#' titanic[,response] <- as.factor(titanic[,response])
#' titanic[,"pclass"] <- as.factor(titanic[,"pclass"])
#' 
#' splits <- h2o.splitFrame(data = titanic, ratios = .8, seed = 1234)
#' train <- splits[[1]]
#' test <- splits[[2]]
#' 
#' rfit <- h2o.rulefit(y = response, x = predictors, training_frame = train, validation_frame = test, 
#' min_rule_length = 1, max_rule_length = 10, max_num_rules = 100, seed = 1, model_type="rules")
#' h2o.fit_rules(rfit, train, c("M1T0N7, M1T49N7, M1T16N7", "M1T36N7", "M2T19N19"))
#' }
#' @export
h2o.fit_rules <- function(model, frame, rule_ids) {
    o <- model
    if (is(o, "H2OModel")) {
        if (o@algorithm == "rulefit"){
            expr <- sprintf('(rulefit.fit.rules %s %s %s)', model@model_id, h2o.getId(frame), .collapse.char(rule_ids))
            rapidsFrame <- h2o.rapids(expr)
            return(h2o.getFrame(rapidsFrame$key$name))
        } else {
            warning(paste0("No calculation available for this model"))
            return(NULL)
        }
    } else {
        warning(paste0("No calculation available for ", class(o)))
        return(NULL)
    }
}
"""
)

doc = dict(
    preamble="""
Build a RuleFit Model
    
Builds a Distributed RuleFit model on a parsed dataset, for regression or 
classification.
    """,
    params=dict(
        model_type="Specifies type of base learners in the ensemble. Must be one of: \"rules_and_linear\", \"rules\", \"linear\". "
                   "Defaults to rules_and_linear.",
        min_rule_length="Minimum length of rules. Defaults to 3.",
        max_rule_length="Maximum length of rules. Defaults to 3.",
    ),
    signatures=dict(
        model_type="c(\"rules_and_linear\", \"rules\", \"linear\")"
    ),
    examples="""
library(h2o)
h2o.init()

# Import the titanic dataset:
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
coltypes <- list(by.col.name = c("pclass", "survived"), types=c("Enum", "Enum"))
df <- h2o.importFile(f, col.types = coltypes)

# Split the dataset into train and test
splits <- h2o.splitFrame(data = df, ratios = 0.8, seed = 1)
train <- splits[[1]]
test <- splits[[2]]

# Set the predictors and response; set the factors:
response <- "survived"
predictors <- c("age", "sibsp", "parch", "fare", "sex", "pclass")

# Build and train the model:
rfit <- h2o.rulefit(y = response,
                    x = predictors,
                    training_frame = train,
                    max_rule_length = 10,
                    max_num_rules = 100,
                    seed = 1)

# Retrieve the rule importance:
print(rfit@model$rule_importance)

# Predict on the test data:
h2o.predict(rfit, newdata = test)
"""
)
