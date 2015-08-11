import sys, os
sys.path.insert(1, "../../../")
import h2o

def nfold_predict(ip,port):
  fr = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate_train.csv"))
  m  = h2o.gbm(x=fr[2:], y=fr[1], nfolds=10, ntrees=10)
  xval_models = m.get_xval_models()
  fr["weights"]=1
  preds = [model.predict(fr) for model in xval_models]
  (sum(preds)/10).show()

if __name__ == "__main__":
  h2o.run_test(sys.argv, nfold_predict)
