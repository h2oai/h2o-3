import sys
sys.path.insert(1, "../../../")
import h2o

def cupMediumGBM(ip,port):
  # Connect to h2o
  

  train = h2o.import_frame(path=h2o.locate("bigdata/laptop/usecases/cup98LRN_z.csv"))
  test = h2o.import_frame(path=h2o.locate("bigdata/laptop/usecases/cup98VAL_z.csv"))

  train["TARGET_B"] = train["TARGET_B"].asfactor()

  # Train H2O GBM Model:
  train_cols = train.names()
  for c in ['C1', "TARGET_D", "TARGET_B", "CONTROLN"]:
    train_cols.remove(c)
  model = h2o.gbm(x=train[train_cols], y=train["TARGET_B"], distribution = "bernoulli", ntrees = 5)

if __name__ == "__main__":
  h2o.run_test(sys.argv, cupMediumGBM)
