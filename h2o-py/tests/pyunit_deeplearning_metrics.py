import sys
sys.path.insert(1, "..")
import h2o


def deep_learning_metrics_test(ip, port):
  h2o.init(ip, port)               # connect to existing cluster
  df = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))

  del df['ID']                               # remove ID
  df['CAPSULE'] = df['CAPSULE'].asfactor()   # make CAPSULE categorical
  vol = df['VOL']
  vol[vol == 0] = None                       # 0 VOL means 'missing'

  r = vol.runif()                            # random train/test split
  train = df[r < 0.8]
  test  = df[r >= 0.8]

  # See that the data is ready
  train.describe()
  train.head()
  test.describe()
  test.head()

  # Run DeepLearning

  print "Train a Deeplearning model: "
  dl = h2o.deeplearning(x           = train[1:],
                        y           = train['CAPSULE'],
                        epochs = 100,
                        hidden = [10, 10, 10])
  print "Binomial Model Metrics: "
  print
  dl.show()
  # print dl._model_json
  dl.model_performance(test).show()


if __name__ == "__main__":
  h2o.run_test(sys.argv, deep_learning_metrics_test)
