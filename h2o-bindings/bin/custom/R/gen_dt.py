extensions = dict(
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 
                                 'max_confusion_matrix_size', 'distribution', 'offset_column'],

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
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/
        airlines_train_preprocessed.csv"
data <- h2o.importFile(f)

# Set predictors and response; set response as a factor
data["IsDepDelayed"] <- as.factor(cars["IsDepDelayed"])
predictors <- c("fYear","fMonth","fDayOfMonth","fDayOfWeek",
                "UniqueCarrier","Origin","Dest","Distance")
response <- "IsDepDelayed"

# Train the DT model
airlines_dt <- h2o.decisiontree(x = predictors, y = response, training_frame = data, seed = 1234)
"""
)
