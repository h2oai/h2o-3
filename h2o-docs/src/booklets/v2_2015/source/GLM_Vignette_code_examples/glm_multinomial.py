import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
multinomial_fit = H2OGeneralizedLinearEstimator(family = "multinomial")
multinomial_fit.train(y = 4, x = [0,1,2,3], training_frame = h2o_df)