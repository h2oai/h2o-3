import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
gamma_inverse = H2OGeneralizedLinearEstimator(family = "gamma", link = "inverse")
gamma_inverse.train(y = "DPROS", x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL"], training_frame = h2o_df)

gamma_log = H2OGeneralizedLinearEstimator(family = "gamma", link = "log")
gamma_log.train(y="DPROS", x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL"], training_frame = h2o_df)
