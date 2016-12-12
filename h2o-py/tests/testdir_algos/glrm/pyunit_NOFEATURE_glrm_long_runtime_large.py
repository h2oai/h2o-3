from __future__ import print_function

import sys

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

# This test is just to take a large dataset, perform GLRM on it and figure
# out the performance time.  This test should not be run on Jenkins.  It
# simply takes too long

def glrm_long_run():
    run_time_ms = []
    iterations = []

    acs_orig = h2o.upload_file(path=pyunit_utils.locate("bigdata/laptop/milsongs/milsongs-cls-train.csv.gz"))

    # run GLRM with max_runtime_ms restriction.
    acs_model = H2OGeneralizedLowRankEstimator(k = 10,
                                               transform = 'STANDARDIZE',
                                               loss = 'Quadratic',
                                               multi_loss="Categorical",
                                               model_id="clients_core_glrm",
                                               regularization_x="L2",
                                               regularization_y="L1",
                                               gamma_x=0.2,
                                               gamma_y=0.5,
                                               init="SVD",
                                               seed=1234)
    acs_model.train(x = acs_orig.names, training_frame= acs_orig, max_runtime_secs=60)

    print("Run time in s with max_runtime_secs of 60 second: "
          "{0}".format((acs_model._model_json['output']['end_time']-
                        acs_model._model_json['output']['start_time'])/1000.0))
    print("number of iterations: {0}".format(acs_model._model_json['output']['iterations']))

    # let glrm run with restriction on iteration number.
    acs_model = H2OGeneralizedLowRankEstimator(k = 10,
                                               transform = 'STANDARDIZE',
                                               loss = 'Quadratic',
                                               multi_loss="Categorical",
                                               model_id="clients_core_glrm",
                                               regularization_x="L2",
                                               regularization_y="L1",
                                               gamma_x=0.2,
                                               gamma_y=0.5,
                                               init="SVD",
                                               seed=1234)
    acs_model.train(x = acs_orig.names, training_frame= acs_orig)
    run_time_ms.append(acs_model._model_json['output']['end_time'] - acs_model._model_json['output']['start_time'])
    iterations.append(acs_model._model_json['output']['iterations'])

    print("Run time in s with no max time restrication: "
          "{0}".format((acs_model._model_json['output']['end_time'] -
                        acs_model._model_json['output']['start_time'])/1000.0))
    print("number of iterations: {0}".format(acs_model._model_json['output']['iterations']))

    sys.stdout.flush()

if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_long_run)
else:
  glrm_long_run()
