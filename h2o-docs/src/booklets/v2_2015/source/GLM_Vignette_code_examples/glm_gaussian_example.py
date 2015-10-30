import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
gaussian_fit = H2OGeneralizedLinearEstimator(family = "gaussian")
gaussian_fit.train(y = "VOL", x = ["AGE", "RACE", "PSA", "GLEASON"], training_frame = h2o_df)