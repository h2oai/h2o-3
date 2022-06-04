import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def gbm_quantiles_global_with_only_categorical_colums():
  prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
  prostate_train = prostate_train.drop("AGE")

  for col_name in prostate_train.names:
    prostate_train[col_name] = prostate_train[col_name].ascharacter().asfactor()

  gbm_h2o = H2OGradientBoostingEstimator(histogram_type="quantiles_global")
  gbm_h2o.train(y="CAPSULE", training_frame=prostate_train)


if __name__ == "__main__":
  pyunit_utils.standalone_test(gbm_quantiles_global_with_only_categorical_colums)
else:
    gbm_quantiles_global_with_only_categorical_colums()
