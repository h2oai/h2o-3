from __future__ import print_function
from builtins import str
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
import time

# I am trying to resolve a customer issue as captured in PUBDEV-3454.  Our customer
# said that he ran a GLRM model on a data set (which he has shared with us but would not want
# us to put it out to public) with many categorical leves (~13000 columns in Y matrix).  Model
# converges in ~1 hour for 76 iterations but job runs for another 6.5 hours while nothing happens.
#  Bar indicates 10% done.
#
# The following test is written to duplicate this scenario and captured the stalling.  Once we
# found the cause and fix it, this test will be used to test our results with a different dataset
# with similar characteristic.


def glrm_PUBDEV_3454():

  feature_names = ["ooid", "emps_cnt", "client_revenue", "esdb_state", "esdb_zip", "revenue_adp", "status", "revenue_region",
              "business_unit", "naics3"]  # column names
  feature_types = ["enum","int","int","enum","enum","real","enum","enum","enum","int"]
  features = ["emps_cnt", "client_revenue", "esdb_state", "esdb_zip", "revenue_adp", "status", "revenue_region",
  "business_unit", "naics3"]
  print("Importing user data...")

  seeds = int(round(time.time()))

#  seeds = 1475363366    # a good seed
#  seeds = 1475600507    # seed with high iteration number
  seeds = 12345

  # datahex = \
  #     h2o.upload_file(pyunit_utils.locate("/Users/wendycwong/Documents/PUBDEV_3454_GLRM/glrm_data_DTolstonogov.csv"),
  #                     col_names=feature_names, col_types=feature_types, na_strings=["NA"])

  datahex = \
       h2o.upload_file(pyunit_utils.locate("/Users/wendycwong/Documents/PUBDEV_3454_GLRM/glrm_data_DTolstonogov.csv"),
                       col_names=feature_names, na_strings=["NA"])

#  datahex.describe()

# k = 9, max_iterations = 1000
  kD=10
  max_iters = 1000
  glrm_h2o = H2OGeneralizedLowRankEstimator(k=kD, loss="Quadratic", transform="STANDARDIZE", multi_loss="Categorical",
                                            model_id="clients_core_glrm", regularization_x="L2",
                                            regularization_y="L1", gamma_x=0.2, gamma_y=0.5, max_iterations=max_iters,
                                            init="SVD", seed=seeds)
  startcsv = time.time()
#  glrm_h2o.train(x=features, training_frame=data2)
  glrm_h2o.train(x=features, training_frame=datahex)

  endcsv = time.time()
 # glrm_h2o.show()
  iterNum = glrm_h2o._model_json["output"]["iterations"]
  objectv = glrm_h2o._model_json["output"]["objective"]
  stepSize = glrm_h2o._model_json["output"]["step_size"]
  print("###########  number of iteration is {0}".format(iterNum))
  print("%%%%%%%%%%%  step size is {0}".format(stepSize))
  print("@@@@@@@@@@@@ seed used is {0}".format(seeds))
  print("&&&&&&&&&&&& objective function value is {0}".format(objectv))
  print("************** Time taken to train GLRM model is {0} seconds".format(endcsv-startcsv))
  sys.stdout.flush()


if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_PUBDEV_3454)
else:
    glrm_PUBDEV_3454()
