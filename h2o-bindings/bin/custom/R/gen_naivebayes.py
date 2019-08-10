from copy import copy


def update_param(name, param):
    if name == 'min_sdev':  # create deprecated alias for 'min_sdev'
        threshold = copy(param)
        threshold['name'] = 'threshold'
        return [threshold, param]
    if name == 'eps_sdev':  # create deprecated alias for 'eps_sdev'
        eps = copy(param)
        eps['name'] = 'eps'
        return [eps, param]
    return None  # param untouched


extensions = dict(
    validate_params="""
.naivebayes.map <- c("x" = "ignored_columns", "y" = "response_column", 
                     "threshold" = "min_sdev", "eps" = "eps_sdev")
""",
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'max_confusion_matrix_size',
                                 'threshold', 'eps'],
    set_params="""
if (!missing(threshold))
  warning("argument 'threshold' is deprecated; use 'min_sdev' instead.")
  parms$min_sdev <- threshold
if (!missing(eps))
  warning("argument 'eps' is deprecated; use 'eps_sdev' instead.")
  parms$eps_sdev <- eps
"""
)

doc = dict(
    preamble="""
Compute naive Bayes probabilities on an H2O dataset.

The naive Bayes classifier assumes independence between predictor variables conditional
on the response, and a Gaussian distribution of numeric predictors with mean and standard
deviation computed from the training dataset. When building a naive Bayes classifier,
every row in the training dataset that contains at least one NA will be skipped completely.
If the test dataset has missing values, then those predictors are omitted in the probability
calculation during prediction.
""",
    params=dict(
        threshold="""
This argument is deprecated, use `min_sdev` instead. The minimum standard deviation to use for observations without enough data.
Must be at least 1e-10.
""",
        min_sdev="""
The minimum standard deviation to use for observations without enough data.
Must be at least 1e-10.
""",
        eps="""
This argument is deprecated, use `eps_sdev` instead. A threshold cutoff to deal with numeric instability, must be positive.
""",
        eps_sdev="""
A threshold cutoff to deal with numeric instability, must be positive.
""",
        min_prob="""
Min. probability to use for observations with not enough data.
""",
        eps_prob="""
Cutoff below which probability is replaced with min_prob.
""",
    ),
    returns="""
an object of class \linkS4class{H2OBinomialModel} if the response has two categorical levels,
and \linkS4class{H2OMultinomialModel} otherwise.
""",
    examples="""
h2o.init()
votes_path <- system.file("extdata", "housevotes.csv", package = "h2o")
votes <- h2o.uploadFile(path = votes_path, header = TRUE)
h2o.naiveBayes(x = 2:17, y = 1, training_frame = votes, laplace = 3)
"""
)
