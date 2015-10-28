import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def nfold_predict():
  fr = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  m = H2OGradientBoostingEstimator(nfolds=10,ntrees=10)
  m.train(x=range(2,fr.ncol), y=1, training_frame=fr)
  xval_models = m.get_xval_models()
  fr["weights"]=1
  preds = [model.predict(fr) for model in xval_models]
  (sum(preds)/10).show()



if __name__ == "__main__":
  pyunit_utils.standalone_test(nfold_predict)
else:
  nfold_predict()
