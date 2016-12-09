from __future__ import print_function

import sys

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

# This test is just to take a large dataset, perform GLRM on it and figure
# out the performance time.  This test should not be run on Jenkins.  It
# simply takes too long

def glrm_subset():
  acs_orig = h2o.upload_file(path=pyunit_utils.locate("bigdata/laptop/airlines_all.05p.csv"))

  seeds = [2297378124, 3849570216, 6733652048, 8915337442, 8344418400, 9416580152, 2598632624, 4977008454, 8273228579,
           8185554539, 3219125000, 2998879373, 7707012513, 5786923379, 5029788935, 935945790, 7092607078, 9305834745,
           6173975590, 5397294255]
  run_time_ms = []
  iterations = []
  objective = []
  num_runs = 1         # number of times to repeat experiments

  for ind in range(num_runs):
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
                                                 max_iterations = 200,
                                                 seed=seeds[ind % len(seeds)])
    acs_model.train(x = acs_orig.names, training_frame= acs_orig, seed=seeds[ind % len(seeds)])
    run_time_ms.append(acs_model._model_json['output']['end_time'] - acs_model._model_json['output']['start_time'])
    iterations.append(acs_model._model_json['output']['iterations'])
    objective.append(acs_model._model_json['output']['objective'])
  
  print("Run time in ms: {0}".format(run_time_ms))
  print("number of iterations: {0}".format(iterations))
  print("objective function value: {0}".format(objective))
  sys.stdout.flush()

if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_subset)
else:
  glrm_subset()
