import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()


h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
rand_vec <- h2o.runif(h2o_df, seed = 1234)
train <- h2o_df[rand_vec <= 0.8,]
valid <- h2o_df[rand_vec > 0.8,]

binomial_fit = H2OGeneralizedLinearEstimator(family = "binomial")
binomial_fit.train(y = "CAPSULE", x = ["AGE", "RACE", "PSA", "GLEASON"], training_frame = h2o_df)
print(binomial.fit)

binomial.fit@model$coefficients

binomial.fit@model$coefficients_table

binomial.fit@model$standardized_coefficient_magnitudes
