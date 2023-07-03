from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator

import random

def cars_checkpoint():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    s = cars.runif()
    train = cars[s > .2]
    valid = cars[s <= .2]

    print("\n*** Description (chunk distribution, etc) of training frame:")
    train.describe()
    print("\n*** Description (chunk distribution, etc) of validation frame:")
    valid.describe()

    # choose the type model-building exercise (multinomial classification or regression). 0:regression, 1:binomial,
    # 2:multinomial
    problem = random.sample(list(range(3)),1)[0]

    # pick the predictors and response column, along with the correct
    predictors = ["displacement","power","weight","acceleration","year"]
    if problem == 1   :
        response_col = "economy_20mpg"
        train[response_col] = train[response_col].asfactor()
        valid[response_col] = valid[response_col].asfactor()
    elif problem == 2 :
        response_col = "cylinders"
        train[response_col] = train[response_col].asfactor()
        valid[response_col] = valid[response_col].asfactor()
    else              :
        response_col = "economy"

    print("\n*** Response column: {0}".format(response_col))

    # build first model
    ntrees1 = 5
    max_depth1 = random.sample(list(range(2,6)),1)[0]
    min_rows1 = random.sample(list(range(10,16)),1)[0]
    print("\n*** Building model 1 with the following parameters:")
    print("*** ntrees model 1: {0}".format(ntrees1))
    print("*** max_depth model 1: {0}".format(max_depth1))
    print("*** min_rows model 1: {0}".format(min_rows1))
    model1 = H2ORandomForestEstimator(ntrees=ntrees1,
                                      max_depth=max_depth1,
                                      min_rows=min_rows1,
                                      score_each_iteration=True,
                                      seed=1234)
    model1.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid)
    # model1 = h2o.random_forest(x=train[predictors],
    #                  y=train[response_col],
    #                  ntrees=ntrees1,
    #                  max_depth=max_depth1,
    #                  min_rows=min_rows1,
    #                  score_each_iteration=True,
    #                  validation_x=valid[predictors],
    #                  validation_y=valid[response_col],
    #                  seed=1234)

    # save the model, then load the model
    model_path = h2o.save_model(model1, name="delete_model", force=True)
    restored_model = h2o.load_model(model_path)
    shutil.rmtree("delete_model")

    # continue building the model
    ntrees2 = ntrees1 + 5
    max_depth2 = max_depth1
    min_rows2 = min_rows1
    print("\n*** Continuing to build model 1 (now called model 2) with the following parameters:")
    print("*** ntrees model 2: {0}".format(ntrees2))
    print("*** max_depth model 2: {0}".format(max_depth2))
    print("*** min_rows model 2: {0}".format(min_rows2))
    model2 = H2ORandomForestEstimator(ntrees=ntrees2,
                                      max_depth=max_depth2,
                                      min_rows=min_rows2,
                                      score_each_iteration=True,
                                      checkpoint=restored_model._id,
                                      seed=1234)
    model2.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid)
    # model2 = h2o.random_forest(x=train[predictors],
    #                  y=train[response_col],
    #                  ntrees=ntrees2,
    #                  max_depth=max_depth2,
    #                  min_rows=min_rows2,
    #                  score_each_iteration=True,
    #                  validation_x=valid[predictors],
    #                  validation_y=valid[response_col],
    #                  checkpoint=restored_model._id,
    #                  seed=1234)

    # continue building the model, but with different number of trees
    ntrees3 = ntrees2 + 50
    max_depth3 = max_depth1
    min_rows3 = min_rows1
    print("\n*** Continuing to build model 1 (now called model 3) with the following parameters:")
    print("*** ntrees model 3: {0}".format(ntrees3))
    print("*** max_depth model 3: {0}".format(max_depth3))
    print("*** min_rows model 3: {0}".format(min_rows3))
    model3 = H2ORandomForestEstimator(ntrees=ntrees3,
                                      max_depth=max_depth3,
                                      min_rows=min_rows3,
                                      score_each_iteration=True,
                                      checkpoint=restored_model._id,
                                      seed=1234)
    model3.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid)
    # model3 = h2o.random_forest(x=train[predictors],
    #                  y=train[response_col],
    #                  ntrees=ntrees3,
    #                  max_depth=max_depth3,
    #                  min_rows=min_rows3,
    #                  score_each_iteration=True,
    #                  validation_x=valid[predictors],
    #                  validation_y=valid[response_col],
    #                  checkpoint=restored_model._id,
    #                  seed=1234)

    # build the equivalent of model 2 in one shot
    print("\n*** Building the equivalent of model 2 (called model 4) in one shot:")
    model4 = H2ORandomForestEstimator(ntrees=ntrees2,
                                      max_depth=max_depth2,
                                      min_rows=min_rows2,
                                      score_each_iteration=True,
                                      seed=1234)
    model4.train(x=predictors,y=response_col,training_frame=train,validation_frame=valid)
    # model4 = h2o.random_forest(x=train[predictors],
    #                  y=train[response_col],
    #                  ntrees=ntrees2,
    #                  max_depth=max_depth2,
    #                  min_rows=min_rows2,
    #                  score_each_iteration=True,
    #                  validation_x=valid[predictors],
    #                  validation_y=valid[response_col],
    #                  seed=1234)

    print("\n*** Model Summary for model 2:")
    print(model2.summary())
    print("\n*** Model Summary for model 3:")
    print(model3.summary())
    print("\n*** Model Summary for model 4:")
    print(model4.summary())

    print("\n*** Score History for model 2:")
    print(model2.scoring_history())
    print("\n*** Score History for model 3:")
    print(model3.scoring_history())
    print("\n*** Score History for model 4:")
    print(model4.scoring_history())

    # checks
    if problem == 0:
        assert isinstance(model2,type(model4))
        assert model2.mse(valid=True)==model4.mse(valid=True), "Expected Model 2 MSE: {0} to be the same as Model 4 MSE: {1}".format(model2.mse(valid=True), model4.mse(valid=True))
        #assert model3.mse(valid=True)!=model4.mse(valid=True), "Expected Model 3 MSE: {0} to be different from Model 4 MSE: {1}".format(model3.mse(valid=True), model4.mse(valid=True))

    elif problem == 1:
        assert isinstance(model2,type(model4))
        assert model2.auc(valid=True)==model4.auc(valid=True), "Expected Model 2 AUC: {0} to be the same as Model 4 AUC: {1}".format(model2.auc(valid=True), model4.auc(valid=True))
        #assert model3.auc(valid=True)!=model4.auc(valid=True), "Expected Model 3 AUC: {0} to be different from Model 4 AUC: {1}".format(model3.auc(valid=True), model4.auc(valid=True))

        assert model2.logloss(valid=True)==model4.logloss(valid=True), "Expected Model 2 Log Loss: {0} to be the same as Model 4 Log Loss: {1}".format(model2.logloss(valid=True), model4.logloss(valid=True))
        #assert model3.logloss(valid=True)!=model4.logloss(valid=True), "Expected Model 3 Log Loss: {0} to be different from Model 4 Log Loss: {1}".format(model2.logloss(valid=True), model4.logloss(valid=True))

        assert model2.gini(valid=True)==model4.gini(valid=True), "Expected Model 2 Gini Coef {0} to be the same as Model 4 Gini Coef: {1}".format(model2.gini(valid=True), model4.gini(valid=True))
        #assert model3.gini(valid=True)!=model4.gini(valid=True), "Expected Model 3 Gini Coef: {0} to be different from Model 4 Gini Coef: {1}".format(model2.gini(valid=True), model4.gini(valid=True))

    else:
        assert isinstance(model2,type(model4))
        assert model2.mse(valid=True)==model4.mse(valid=True), "Expected Model 2 MSE: {0} to be the same as Model 4 MSE: {1}".format(model2.mse(valid=True), model4.mse(valid=True))
        #assert model3.mse(valid=True)!=model4.mse(valid=True), "Expected Model 3 MSE: {0} to be different from Model 4 MSE: {1}".format(model3.mse(valid=True), model4.mse(valid=True))

        assert model2.r2(valid=True)==model4.r2(valid=True), "Expected Model 2 R2: {0} to be the same as Model 4 R2: {1}".format(model2.r2(valid=True), model4.r2(valid=True))
        #assert model3.r2(valid=True)!=model4.r2(valid=True), "Expected Model 3 R2: {0} to be different from Model 4 R2: {1}".format(model3.r2(valid=True), model4.r2(valid=True))



if __name__ == "__main__":
    pyunit_utils.standalone_test(cars_checkpoint)
else:
    cars_checkpoint()
