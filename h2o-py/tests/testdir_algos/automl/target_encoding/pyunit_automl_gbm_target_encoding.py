import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils
from h2o.targetencoder import TargetEncoder
import random


def split_data(frame = None, seed = None):
  splits = frame.split_frame(ratios=[.8,.1], seed=seed)
  targetColumnName = "survived"
  splits[0][targetColumnName] = splits[0][targetColumnName].asfactor()
  splits[1][targetColumnName] = splits[1][targetColumnName].asfactor()
  splits[2][targetColumnName] = splits[2][targetColumnName].asfactor()
  return dict(train=splits[0], valid=splits[1], test=splits[2])

def titanic_without_te(frame = None, seeds = None):
    targetColumnName = "survived"

    sum_of_aucs = 0
    for current_seed in seeds:
      ds = split_data(frame, current_seed)

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
      # print(variable_importance)
      my_gbm_metrics = air_model.model_performance(ds['test'])
      auc = my_gbm_metrics.auc()
      sum_of_aucs += auc
      print("AUC without te: " + str(current_seed) + " = " + str(auc))
    return sum_of_aucs / len(seeds)


def titanic_with_te_kfoldstrategy(frame = None, seeds = None):
    sum_of_aucs = 0
    for current_seed in seeds:
      ds = split_data(frame, current_seed)

      targetColumnName = "survived"

      foldColumnName = "kfold_column"
      ds['train'][foldColumnName] = ds['train'].kfold_column(n_folds=5, seed=current_seed)

      teColumns = ["home.dest", "cabin", "embarked"]
      targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                    fold_column= foldColumnName, blending_avg= True, inflection_point = 3, smoothing = 1)
      targetEncoder.fit(frame=ds['train'])

      encodedTrain = targetEncoder.transform(frame=ds['train'], holdout_type="kfold", seed=1234, is_train_or_valid=True)
      encodedValid = targetEncoder.transform(frame=ds['valid'], holdout_type="none", noise=0.0, seed=1234, is_train_or_valid=True)
      encodedTest = targetEncoder.transform(frame=ds['test'], holdout_type="none", noise=0.0, seed=1234, is_train_or_valid=False)

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
      # print(variable_importance)

      my_gbm_metrics = air_model.model_performance(encodedTest)
      auc = my_gbm_metrics.auc()
      sum_of_aucs += auc
      print("AUC with kfold for seed: " + str(current_seed) + " = " + str(auc))
    return sum_of_aucs / len(seeds)


def titanic_with_te_loostrategy(frame = None, seeds = None):
  sum_of_aucs = 0
  for current_seed in seeds:
    ds = split_data(frame, current_seed)
    targetColumnName = "survived"

    foldColumnName = "kfold_column"
    ds['train'][foldColumnName] = ds['train'].kfold_column(n_folds=5, seed=current_seed)

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  fold_column= foldColumnName, blending_avg= True, inflection_point = 3, smoothing = 1)
    targetEncoder.fit(frame=ds['train'])

    encodedTrain = targetEncoder.transform(frame=ds['train'], holdout_type="loo", seed=1234, is_train_or_valid=True)
    encodedValid = targetEncoder.transform(frame=ds['valid'], holdout_type="none", noise=0.0, seed=1234, is_train_or_valid=True)
    encodedTest = targetEncoder.transform(frame=ds['test'], holdout_type="none", noise=0.0, seed=1234, is_train_or_valid=False)

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
    # print(variable_importance)

    my_gbm_metrics = air_model.model_performance(encodedTest)
    auc = my_gbm_metrics.auc()
    sum_of_aucs += auc
    print("AUC with loo for seed: " + str(current_seed) + " = " + str(auc))
  return sum_of_aucs / len(seeds)

def titanic_with_te_nonestrategy(frame = None, seeds = None):
  sum_of_aucs = 0
  for current_seed in seeds:
    ds = split_data(frame, current_seed)
    splits = ds['train'].split_frame(ratios=[.91], seed=current_seed)
    train = splits[0]
    holdout = splits[1]

    targetColumnName = "survived"

    teColumns = ["home.dest", "cabin", "embarked"]
    targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                  blending_avg= True, inflection_point = 3, smoothing = 1)
    targetEncoder.fit(frame=holdout)

    encodedTrain = targetEncoder.transform(frame=train, holdout_type="none", noise=0.0, seed=1234, is_train_or_valid=True)
    encodedValid = targetEncoder.transform(frame=ds['valid'], holdout_type="none", noise=0.0, seed=1234, is_train_or_valid=True)
    encodedTest = targetEncoder.transform(frame=ds['test'], holdout_type="none", noise=0.0, seed=1234, is_train_or_valid=False)

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
    # print(variable_importance)

    my_gbm_metrics = air_model.model_performance(encodedTest)
    auc = my_gbm_metrics.auc()
    sum_of_aucs += auc
    print("AUC with none(holdout) for seed: " + str(current_seed) + " = " + str(auc))
  return sum_of_aucs / len(seeds)


if __name__ == "__main__":
  h2o.connect()

  titanic = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"), header=1)

  runs = 1 # Set to a bigger value to get more objective resuts.

  seeds = random.sample(range(1, 10000), runs)

  without_te = titanic_without_te(titanic, seeds)
  kfold_strategy = titanic_with_te_kfoldstrategy(titanic, seeds)
  loo_strategy = titanic_with_te_loostrategy(titanic, seeds)
  none_strategy = titanic_with_te_nonestrategy(titanic, seeds)

  print("\n\nReport was generated based on average values from " + str(runs) + " runs that depends on the same set of seeds")
  print("AUC without te: " + str(without_te))
  print("AUC with kfold: " + str(kfold_strategy))
  print("AUC with loo: " + str(loo_strategy))
  print("AUC with none(holdout): " + str(none_strategy))