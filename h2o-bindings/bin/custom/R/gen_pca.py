extensions = dict(
    required_params=dict(training_frame=None, x=None),
    validate_required_params="",
    set_required_params="""
parms$training_frame <- training_frame
if(!missing(x))
  parms$ignored_columns <- .verify_datacols(training_frame, x)$cols_ignore
""",
)

doc = dict(
    preamble="""
Principal component analysis of an H2O data frame

Principal components analysis of an H2O data frame using the power method
to calculate the singular value decomposition of the Gram matrix.
""",
    params=dict(
        x="""A vector containing the \code{character} names of the predictors in the model."""
    ),
    returns="""
an object of class \linkS4class{H2ODimReductionModel}.
""",
    seealso="""
\code{\link{h2o.svd}}, \code{\link{h2o.glrm}}
""",
    references="""
N. Halko, P.G. Martinsson, J.A. Tropp. {Finding structure with randomness: Probabilistic algorithms for constructing approximate matrix decompositions}[http://arxiv.org/abs/0909.4061]. SIAM Rev., Survey and Review section, Vol. 53, num. 2, pp. 217-288, June 2011.
""",
    examples="""
library(h2o)
h2o.init()
australia_path <- system.file("extdata", "australia.csv", package = "h2o")
australia <- h2o.uploadFile(path = australia_path)
h2o.prcomp(training_frame = australia, k = 8, transform = "STANDARDIZE")
"""
)
