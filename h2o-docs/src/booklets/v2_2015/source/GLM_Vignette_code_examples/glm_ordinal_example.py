import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv")
h2o_df['C11'] = h2o_df['C11'].asfactor()

ordinal_fit = H2OGeneralizedLinearEstimator(family = "ordinal", alpha = 1.0, lambda_=0.000000001, obj_reg = 0.00001, max_iterations=1000, beta_epsilon=1e-8, objective_epsilon=1e-10)
ordinal_fit.train(x=list(range(0,10)), y="C11", training_frame=h2o_df)