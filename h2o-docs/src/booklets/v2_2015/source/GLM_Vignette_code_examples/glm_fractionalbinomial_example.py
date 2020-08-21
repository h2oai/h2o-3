import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/fraction_binommialOrig.csv")
x = ["log10conc"]
y = "y"

fractional_binomial = H2OGeneralizedLinearEstimator(family = "fractionalbinomial", alpha = [0], lambda_ = [0], standardize = False, compute_p_values = True)
fractional_binomial.train(x = x, y = y, training_frame = train)
