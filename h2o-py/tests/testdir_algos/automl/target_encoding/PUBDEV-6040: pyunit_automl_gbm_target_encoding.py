import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.targetencoder import TargetEncoder
import random


def split_data(frame = None, seed = None):
  targetColumnName = "bad_loan"
  frame[targetColumnName] = frame[targetColumnName].asfactor()
  splits = frame.split_frame(ratios=[.8,.1], seed=seed)
  return dict(train=splits[0], valid=splits[1], test=splits[2])

def titanic_without_te(frame = None, seeds = None):
    targetColumnName = "bad_loan"

    sum_of_aucs = 0
    for current_seed in seeds:
        ds = split_data(frame, current_seed)

        myX = ["loan_amnt", "int_rate", "emp_length", "annual_inc", "dti",
               "delinq_2yrs", "revol_util", "total_acc", "longest_credit_length",
               "verification_status", "term", "purpose", "home_ownership",
               "addr_state"]
        model = H2OGradientBoostingEstimator(ntrees=1000,
                                                 learn_rate=0.1,
                                                 score_tree_interval=10,
                                                 stopping_rounds=5,
                                                 stopping_metric="AUC",
                                                 stopping_tolerance=0.001,
                                                 distribution="multinomial",
                                                 seed=1234)
        model.train(x=myX, y=targetColumnName,
                        training_frame=ds['train'], validation_frame=ds['valid'])
        variable_importance = model._model_json['output']['variable_importances'].as_data_frame()
        print(variable_importance)
        my_gbm_metrics = model.model_performance(ds['test'])
        auc = my_gbm_metrics.auc()
        sum_of_aucs += auc
        print("AUC without te: " + str(current_seed) + " = " + str(auc))
    return sum_of_aucs / len(seeds)

def badloan_with_te_kfoldstrategy(frame = None, seeds = None):
    sum_of_aucs = 0
    for current_seed in seeds:
      ds = split_data(frame, current_seed)

      targetColumnName = "bad_loan"

      foldColumnName = "kfold_column"
      ds['train'][foldColumnName] = ds['train'].kfold_column(n_folds=5, seed=current_seed)

      teColumns = ["addr_state"]
      targetEncoder = TargetEncoder(x= teColumns, y= targetColumnName,
                                    fold_column= foldColumnName, blended_avg= True, inflection_point = 3, smoothing = 1)
      targetEncoder.fit(frame=ds['train'])

      noiseOnlyForTrainingFrame = 0.2
      encodedTrain = targetEncoder.transform(frame=ds['train'], holdout_type="kfold", noise=noiseOnlyForTrainingFrame, seed=1234)
      encodedValid = targetEncoder.transform(frame=ds['valid'], holdout_type="none", noise=0.0)
      encodedTest = targetEncoder.transform(frame=ds['test'], holdout_type="none", noise=0.0)

      myX = ["loan_amnt", "int_rate", "emp_length", "annual_inc", "dti",
             "delinq_2yrs", "revol_util", "total_acc", "longest_credit_length",
             "verification_status", "term", "purpose", "home_ownership",
             "addr_state_te"] # <---- Note: it is `addr_state_te` here
      model = H2OGradientBoostingEstimator(ntrees=1000,
                                               learn_rate=0.1,
                                               score_tree_interval=10,
                                               stopping_rounds=5,
                                               stopping_metric="AUC",
                                               stopping_tolerance=0.001,
                                               distribution="multinomial",
                                               seed=1234)
      model.train(x=myX, y=targetColumnName,
                      training_frame=encodedTrain, validation_frame=encodedValid)
      variable_importance = model._model_json['output']['variable_importances'].as_data_frame()
      print(variable_importance)

      my_gbm_metrics = model.model_performance(encodedTest)
      auc = my_gbm_metrics.auc()
      sum_of_aucs += auc
      print("AUC with kfold for seed: " + str(current_seed) + " = " + str(auc))
    return sum_of_aucs / len(seeds)



if __name__ == "__main__":
  h2o.connect()

  badloan = h2o.import_file("https://raw.githubusercontent.com/h2oai/app-consumer-loan/master/data/loan.csv", header=1)

  runs = 2 # Set to a bigger value to get more objective result.

  seeds = random.sample(range(1, 10000), runs)

  without_te = titanic_without_te(badloan, seeds)
  kfold_strategy = badloan_with_te_kfoldstrategy(badloan, seeds)

  print("\n\nReport was generated based on average values from " + str(runs) + " runs that depends on the same set of seeds")
  print("Note that without proper search for optimal TE hyperparameters results are less likely to be satisfactory")
  print("AUC without te: " + str(without_te))
  print("AUC with kfold: " + str(kfold_strategy))
