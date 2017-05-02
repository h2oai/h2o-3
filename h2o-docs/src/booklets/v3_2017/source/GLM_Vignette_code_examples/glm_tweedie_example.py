import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/auto.csv")
tweedie_fit = H2OGeneralizedLinearEstimator(family = "tweedie")
tweedie_fit.train(y = "y", x = h2o_df.col_names[1:], training_frame = h2o_df)