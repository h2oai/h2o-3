extensions = dict(
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
"""
)

doc = dict(
    preamble="""
Fit a AnovaGLM estimator

Creates three generalized linear models using subsets of the predictors, specified by a response variable, a set
of exactly two predictors, and a description of the error distribution.
    """,
    examples="""
lirary(h2o)
h2o.init()

# Run ANOVA GLM of VOL ~ CAPSULE + RACE
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
prostate <- h2o.uploadFile(f)
prostate$CAPSULE <- as.factor(prostate$CAPSULE)
model <- h2o.anovaglm(y = "VOL", x = c("CAPSULE", "RACE"), training_frame = prostate, family = "gaussian")
"""
)
