import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/Motor_insurance_sweden.txt")
predictors = ["Payment", "Insured", "Kilometres", "Zone", "Bonus", "Make"]
response = "Claims"
negativebinomial_fit = H2OGeneralizedLinearEstimator(family="negativebinomial", link="identity", theta=0.5)
negativebinomial_fit.train(x=predictors, y=response, training_frame=h2o_df)
