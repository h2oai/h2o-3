import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()
binomial_fit = H2OGeneralizedLinearEstimator(family = "binomial", nfolds=5, fold_assignment="Random")
binomial_fit.train(y = "CAPSULE", x = ["AGE", "RACE", "PSA", "GLEASON"], training_frame = h2o_df)
print "training auc:", binomial_fit.auc(train=True)
print "cross-validation auc:", binomial_fit.auc(xval=True)