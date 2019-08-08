doc = dict(
    __class__="""
The naive Bayes classifier assumes independence between predictor variables
conditional on the response, and a Gaussian distribution of numeric predictors with
mean and standard deviation computed from the training dataset. When building a naive
Bayes classifier, every row in the training dataset that contains at least one NA will
be skipped completely. If the test dataset has missing values, then those predictors
are omitted in the probability calculation during prediction.
""",
)
