def update_param(name, param):
    if name == 'data_leakage_handling':
        param['values'] = ["leave_one_out", "k_fold", "none", "LeaveOneOut", "KFold", "None"]
        return param
    return None  # param untouched


extensions = dict(
    validate_params="""
if (!missing(columns_to_encode))
  columns_to_encode <- lapply(columns_to_encode, function(x) if(is.character(x) & length(x) == 1) list(x) else x)
""",
    set_required_params="""
args <- .verify_dataxy(training_frame, x, y)
if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
parms$training_frame <- training_frame
    """,
    ellipsis_param="""
varargs <- list(...)
for (arg in names(varargs)) {
   if (arg == 'k') {
      warning("argument 'k' is deprecated; please use 'inflection_point' instead.")
      if (missing(inflection_point)) inflection_point <- varargs$k else warning("ignoring 'k' as 'inflection_point' was also provided.")
   } else if (arg == 'f') {
      warning("argument 'f' is deprecated; please use 'smoothing' instead.")
      if (missing(smoothing)) smoothing <- varargs$f else warning("ignoring 'f' as 'smoothing' was also provided.")
   } else if (arg == 'noise_level') {
      warning("argument 'noise_level' is deprecated; please use 'noise' instead.")
      if (missing(noise)) noise <- varargs$noise_level else warning("ignoring 'noise_level' as 'noise' was also provided.")
   } else {
      stop(paste("unused argument", arg, "=", varargs[[arg]]))
   }
}
"""
)


doc = dict(
    preamble="""
 Transformation of a categorical variable with a mean value of the target variable
""",
    params=dict(
        _ellipsis_="Mainly used for backwards compatibility, to allow deprecated parameters."
    ),
    examples="""
library(h2o)
h2o.init()
#Import the titanic dataset
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
