rest_api_version = 99

extensions = dict(
    required_params=dict(training_frame=None, x=None, destination_key=None),
    validate_required_params="",
    set_required_params="""
parms$training_frame <- training_frame
if(!missing(x))
  parms$ignored_columns <- .verify_datacols(training_frame, x)$cols_ignore
if(!missing(destination_key)) {
  warning("'destination_key' is deprecated; please use 'model_id' instead.")
  if(missing(model_id)) {
    parms$model_id <- destination_key
  }
}
""",
)

doc = dict(
    preamble="""
Singular value decomposition of an H2O data frame using the power method
""",
    params=dict(
        x="""
A vector containing the \code{character} names of the predictors in the model.
""",
        destination_key="""
(Optional) The unique key assigned to the resulting model.
Automatically generated if none is provided.
""",
    ),
    returns="""
an object of class \linkS4class{H2ODimReductionModel}.
""",
    references="""
N. Halko, P.G. Martinsson, J.A. Tropp. {Finding structure with randomness: Probabilistic algorithms for constructing approximate matrix decompositions}[http://arxiv.org/abs/0909.4061]. SIAM Rev., Survey and Review section, Vol. 53, num. 2, pp. 217-288, June 2011.
""",
    examples="""
library(h2o)
h2o.init()
australia_path <- system.file("extdata", "australia.csv", package = "h2o")
australia <- h2o.uploadFile(path = australia_path)
h2o.svd(training_frame = australia, nv = 8)
"""
)
