import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils
from h2o.targetencoder import TargetEncoder

def import_dataset():
  titanic = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)
  splits = titanic.split_frame(ratios=[.8,.1], seed=567)
  targetColumnName = "survived"
  splits[0][targetColumnName] = splits[0][targetColumnName].asfactor()
  splits[1][targetColumnName] = splits[1][targetColumnName].asfactor()
  splits[2][targetColumnName] = splits[2][targetColumnName].asfactor()
  return dict(train=splits[0], valid=splits[1], test=splits[2])

def titanic_without_te():
    h2o.connect()

    ds = import_dataset()
    targetColumnName = "survived"

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
                    training_frame=ds['train'], validation_frame=ds['valid'])
    variable_importance = air_model._model_json['output']['variable_importances'].as_data_frame()
    print(variable_importance)
    my_gbm_metrics = air_model.model_performance(ds['test'])
    return my_gbm_metrics.auc()


def titanic_with_te_kfoldstrategy():
    h2o.connect()
    ds = import_dataset()

    targetColumnName = "survived"

    foldColumnName = "kfold_column"
    ds['train'][foldColumnName] = ds['train'].kfold_column(n_folds=5, seed=1234)

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(teColumns= teColumns, targetColumnName = targetColumnName,
                                  foldColumnName = foldColumnName, blending = True, inflection_point = 3, smoothing = 1)
    targetEncoder.fit(trainingFrame=ds['train'])

    encodedTrain = targetEncoder.transform(frame=ds['train'], strategy="kfold", seed=1234, isTrainOrVaid=True)
    encodedValid = targetEncoder.transform(frame=ds['valid'], strategy="none", noise=0.0, seed=1234, isTrainOrVaid=True)
    encodedTest = targetEncoder.transform(frame=ds['test'], strategy="none", noise=0.0, seed=1234, isTrainOrVaid=False)

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

  ds = import_dataset()

  targetColumnName = "survived"

  foldColumnName = "kfold_column"
  ds['train'][foldColumnName] = ds['train'].kfold_column(n_folds=5, seed=1234)

  teColumns = ["home.dest", "cabin", "embarked"]
  targetEncoder = TargetEncoder(teColumns= teColumns, targetColumnName = targetColumnName,
                                foldColumnName = foldColumnName, blending = True, inflection_point = 3, smoothing = 1)
  targetEncoder.fit(trainingFrame=ds['train'])

  encodedTrain = targetEncoder.transform(frame=ds['train'], strategy="loo", seed=1234, isTrainOrVaid=True)
  encodedValid = targetEncoder.transform(frame=ds['valid'], strategy="none", noise=0.0, seed=1234, isTrainOrVaid=True)
  encodedTest = targetEncoder.transform(frame=ds['test'], strategy="none", noise=0.0, seed=1234, isTrainOrVaid=False)

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

def titanic_with_te_nonestrategy():
  h2o.connect()

  ds = import_dataset()

  splits = ds['train'].split_frame(ratios=[.91], seed=567)
  train = splits[0]
  holdout = splits[1]

  targetColumnName = "survived"

  teColumns = ["home.dest", "cabin", "embarked"]
  targetEncoder = TargetEncoder(teColumns= teColumns, targetColumnName = targetColumnName,
                                blending = True, inflection_point = 3, smoothing = 1)
  targetEncoder.fit(trainingFrame=holdout)

  encodedTrain = targetEncoder.transform(frame=train, strategy="none", noise=0.0, seed=1234, isTrainOrVaid=True)
  encodedValid = targetEncoder.transform(frame=ds['valid'], strategy="none", noise=0.0, seed=1234, isTrainOrVaid=True)
  encodedTest = targetEncoder.transform(frame=ds['test'], strategy="none", noise=0.0, seed=1234, isTrainOrVaid=False)

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
  kfold_strategy = titanic_with_te_kfoldstrategy()
  loo_strategy = titanic_with_te_loostrategy()
  none_strategy = titanic_with_te_nonestrategy()
  print("AUC without te: " + str(without_te))
  print("AUC with kfold: " + str(kfold_strategy))
  print("AUC with loo: " + str(loo_strategy))
  print("AUC with none(holdout): " + str(none_strategy))