

import h2o, tests


def deepLearningDemo():

  

  # Training data
  train_data = h2o.import_file(path=tests.locate("smalldata/gbm_test/ecology_model.csv"))
  train_data = train_data.drop('Site')
  train_data['Angaus'] = train_data['Angaus'].asfactor()
  print train_data.describe()
  train_data.head()

  # Testing data
  test_data = h2o.import_file(path=tests.locate("smalldata/gbm_test/ecology_eval.csv"))
  test_data['Angaus'] = test_data['Angaus'].asfactor()
  print test_data.describe()
  test_data.head()


  # Run GBM
  gbm = h2o.gbm(x           = train_data[1:],
                y           = train_data['Angaus'],
                validation_x= test_data [1:] ,
                validation_y= test_data ['Angaus'],
                ntrees=100,
                distribution="bernoulli")

  gbm.show()

  # Run DeepLearning

  dl = h2o.deeplearning(x           = train_data[1:],
                        y           = train_data['Angaus'],
                        validation_x= test_data [1:] ,
                        validation_y= test_data ['Angaus'],
                        loss   = 'CrossEntropy',
                        epochs = 1000,
                        hidden = [20, 20, 20])

  dl.show()


pyunit_test = deepLearningDemo
