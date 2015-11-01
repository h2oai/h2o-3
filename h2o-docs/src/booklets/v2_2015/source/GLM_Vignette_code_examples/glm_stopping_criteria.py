import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

#stops the model when we reach 10 active predictors
#Objective epsilon and gradient epsilon stopping criteria will be added in a future release.
model = H2OGeneralizedLinearEstimator(family = "binomial", lambda_search = True, max_active_predictors = 10)
model.train(y = "IsDepDelayed", x = ["Year", "Origin"], training_frame = h2o_df)
print(model)


