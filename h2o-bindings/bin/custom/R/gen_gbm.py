def update_param(name, param):
    if name == 'distribution':
        param['values'].remove('ordinal')
        return param
    return None  # param untouched


extensions = dict(
    extra_params=[('verbose', 'FALSE')],
    validate_params="""
# Required maps for different names params, including deprecated params
.gbm.map <- c("x" = "ignored_columns",
              "y" = "response_column")
""",
    set_params="""
if (!missing(max_hit_ratio_k)) {
  warning("Argument max_hit_ratio_k is deprecated and has no use.")
  parms$offset_column <- NULL
}
    """
)


doc = dict(
    preamble="""
Build gradient boosted classification or regression trees

Builds gradient boosted classification trees and gradient boosted regression trees on a parsed data set.
The default distribution function will guess the model type based on the response column type.
In order to run properly, the response column must be an numeric for "gaussian" or an
enum for "bernoulli" or "multinomial".
""",
    params=dict(
        max_hit_ratio_k="This argument is deprecated and has no use. Max. number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable).",
        verbose="""
\code{Logical}. Print scoring history to the console (Metrics per tree). Defaults to FALSE.
"""
    ),
    seealso="""
\code{\link{predict.H2OModel}} for prediction
""",
    examples="""
library(h2o)
h2o.init()

# Run regression GBM on australia data
australia_path <- system.file("extdata", "australia.csv", package = "h2o")
australia <- h2o.uploadFile(path = australia_path)
independent <- c("premax", "salmax", "minairtemp", "maxairtemp", "maxsst",
                 "maxsoilmoist", "Max_czcs")
dependent <- "runoffnew"
h2o.gbm(y = dependent, x = independent, training_frame = australia,
        ntrees = 3, max_depth = 3, min_rows = 2)
"""
)
