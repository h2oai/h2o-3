extensions = dict(
    set_required_params="""
args <- .verify_dataxy(training_frame, x, y)
if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
parms$training_frame <- training_frame
    """
)


doc = dict(
    preamble="""
 Transformation of a categorical variable with a mean value of the target variable
""",
    examples="""
library(h2o)
h2o.init()
Import the titanic dataset
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
titanic <- h2o.importFile(f)

# Set response as a factor
response <- "survived"
titanic[response] <- as.factor(titanic[response])

# Split the dataset into train and test
splits <- h2o.splitFrame(data = titanic, ratios = .8, seed = 1234)
train <- splits[[1]]
test <- splits[[2]]

# Choose which columns to encode
encode_columns <- c("home.dest", "cabin", "embarked")

# Train a TE model
te_model <- h2o.targetencoder(x = encode_columns,
                              y = response, 
                              training_frame = train,
                              fold_column = "pclass", 
                              data_leakage_handling = "KFold")

# New target encoded train and test sets
train_te <- h2o.transform(te_model, train)
test_te <- h2o.transform(te_model, test)
"""
)
