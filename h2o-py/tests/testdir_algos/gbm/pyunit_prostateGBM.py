import sys
sys.path.insert(1, "../../../")
import h2o

######################################################
#
# Sample Running GBM on prostate.csv

def prostateGBM(ip,port):
  # Connect to a pre-existing cluster
    # connect to localhost:54321

  df = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))
  df.describe()

  # Remove ID from training frame
  train = df.drop("ID")

  # For VOL & GLEASON, a zero really means "missing"
  vol = train['VOL']
  vol[vol == 0] = None
  gle = train['GLEASON']
  gle[gle == 0] = None

  # Convert CAPSULE to a logical factor
  train['CAPSULE'] = train['CAPSULE'].asfactor()

  # See that the data is ready
  train.describe()

  # Run GBM
  my_gbm = h2o.gbm(           y=train["CAPSULE"],
                   validation_y=train["CAPSULE"],
                              x=train[1:],
                   validation_x=train[1:],
                   ntrees=50,
                   learn_rate=0.1,
                   distribution="bernoulli")
  my_gbm.show()

  my_gbm_metrics = my_gbm.model_performance(train)
  my_gbm_metrics.show()

  my_gbm_metrics  #.show(criterion=my_gbm_metrics.theCriteria.PRECISION)

if __name__ == "__main__":
  h2o.run_test(sys.argv, prostateGBM)
