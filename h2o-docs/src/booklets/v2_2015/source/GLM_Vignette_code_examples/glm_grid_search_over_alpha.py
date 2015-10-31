import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()
alpha_opts = [0.0, 0.25, 0.5, 1.0]
hyper_parameters = {"alpha":alpha_opts}


grid = H2OGridSearch(H2OGeneralizedLinearEstimator(family="binomial"), hyper_params=hyper_parameters)
grid.train(y = "CAPSULE", x = ["AGE", "RACE", "PSA", "GLEASON"], training_frame = h2o_df)
for m in grid:
    print "Model ID: " + m.model_id + " auc: " , m.auc()
    print m.summary()
    print "\n\n"
