import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils
from h2o.targetencoder import TargetEncoder


def titanic_without_te():
    h2o.connect()
    titanic_train = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_train.csv"), header=1)
    titanic_valid = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_valid.csv"), header=1)
    titanic_test = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_test.csv"), header=1)

    targetColumnName = "survived"
    titanic_train[targetColumnName] = titanic_train[targetColumnName].asfactor()
    titanic_valid[targetColumnName] = titanic_valid[targetColumnName].asfactor()
    titanic_test[targetColumnName] = titanic_test[targetColumnName].asfactor()
    # print(titanic_train)
    myX = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin", "embarked", "home.dest"]
    air_model = H2OGradientBoostingEstimator(ntrees=1000,
                                             learn_rate=0.1,
                                             score_tree_interval=10,
                                             stopping_rounds=5,
                                             stopping_metric="AUC",
                                             stopping_tolerance=0.001,
                                             distribution="multinomial",
                                             # why AUC is different for quasibinomial and multinomial?
                                             seed=1234)
    air_model.train(x=myX, y=targetColumnName,
                    training_frame=titanic_train, validation_frame=titanic_valid)
    variable_importance = air_model._model_json['output']['variable_importances'].as_data_frame()
    print(variable_importance)
    my_gbm_metrics = air_model.model_performance(titanic_test)
    return my_gbm_metrics.auc()


def titanic_with_te_kfoldstrategy():
    h2o.connect()
    titanic_train = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_train.csv"), header=1)
    titanic_valid = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_valid.csv"), header=1)
    titanic_test = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_test.csv"), header=1)

    targetColumnName = "survived"
    titanic_train[targetColumnName] = titanic_train[targetColumnName].asfactor()
    titanic_valid[targetColumnName] = titanic_valid[targetColumnName].asfactor()
    titanic_test[targetColumnName] = titanic_test[targetColumnName].asfactor()

    foldColumnName = "kfold_column"
    titanic_train[foldColumnName] = titanic_train.kfold_column(n_folds=5, seed=1234)

    teColumns = ["home.dest", "cabin", "embarked"]
    # teColumns = ["home.dest",  "embarked"]
    targetEncoder = TargetEncoder(teColumns= teColumns, targetColumnName = targetColumnName,
                                  foldColumnName = foldColumnName, blending = True, inflection_point = 3, smoothing = 1)
    targetEncoder.fit(trainingFrame=titanic_train)

    encodedTrain = targetEncoder.transform(frame=titanic_train, strategy="kfold", seed=1234)
    encodedValid = targetEncoder.transform(frame=titanic_valid, strategy="none", noise=0.0, seed=1234)
    encodedTest = targetEncoder.transform(frame=titanic_test, strategy="none", noise=0.0, seed=1234, isTrainOrVaid=True)

    myX = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin_te", "embarked_te", "home.dest_te"]
    air_model = H2OGradientBoostingEstimator(ntrees=1000,
                                             learn_rate=0.1,
                                             score_tree_interval=10,
                                             stopping_rounds=5,
                                             stopping_metric="AUC",
                                             stopping_tolerance=0.001,
                                             distribution="multinomial",
                                             # why AUC is different for quasibinomial and multinomial?
                                             seed=1234)
    air_model.train(x=myX, y=targetColumnName,
                    training_frame=encodedTrain, validation_frame=encodedValid)
    variable_importance = air_model._model_json['output']['variable_importances'].as_data_frame()
    print(variable_importance)

    my_gbm_metrics = air_model.model_performance(encodedTest)
    return my_gbm_metrics.auc()


def titanic_with_te_loostrategy():
  h2o.connect()
  titanic_train = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_train.csv"), header=1)
  titanic_valid = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_valid.csv"), header=1)
  titanic_test = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic_test.csv"), header=1)

  targetColumnName = "survived"
  titanic_train[targetColumnName] = titanic_train[targetColumnName].asfactor()
  titanic_valid[targetColumnName] = titanic_valid[targetColumnName].asfactor()
  titanic_test[targetColumnName] = titanic_test[targetColumnName].asfactor()

  foldColumnName = "kfold_column"
  titanic_train[foldColumnName] = titanic_train.kfold_column(n_folds=5, seed=1234)

  teColumns = ["home.dest", "cabin", "embarked"]
  targetEncoder = TargetEncoder(teColumns= teColumns, targetColumnName = targetColumnName,
                                foldColumnName = foldColumnName, blending = True, inflection_point = 3, smoothing = 1)
  targetEncoder.fit(trainingFrame=titanic_train)

  encodedTrain = targetEncoder.transform(frame=titanic_train, strategy="loo", seed=1234)
  encodedValid = targetEncoder.transform(frame=titanic_valid, strategy="none", noise=0.0, seed=1234)
  encodedTest = targetEncoder.transform(frame=titanic_test, strategy="none", noise=0.0, seed=1234, isTrainOrVaid=True)

  myX = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin_te", "embarked_te", "home.dest_te"]
  air_model = H2OGradientBoostingEstimator(ntrees=1000,
                                           learn_rate=0.1,
                                           score_tree_interval=10,
                                           stopping_rounds=5,
                                           stopping_metric="AUC",
                                           stopping_tolerance=0.001,
                                           distribution="multinomial",
                                           # why AUC is different for quasibinomial and multinomial?
                                           seed=1234)
  air_model.train(x=myX, y=targetColumnName,
                  training_frame=encodedTrain, validation_frame=encodedValid)
  variable_importance = air_model._model_json['output']['variable_importances'].as_data_frame()
  print(variable_importance)

  my_gbm_metrics = air_model.model_performance(encodedTest)
  return my_gbm_metrics.auc()


if __name__ == "__main__":
  without_te = titanic_without_te()
  kfoldstrategy = titanic_with_te_kfoldstrategy()
  loostrategy = titanic_with_te_loostrategy()
  print("AUC without te: " + str(without_te))
  print("AUC with kfold: " + str(kfoldstrategy))
  print("AUC with loo: " + str(loostrategy))