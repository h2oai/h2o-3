import sys
sys.path.insert(1, "../../../")
import h2o
import random

def cars_checkpoint(ip,port):

    cars = h2o.upload_file(h2o.locate("smalldata/junit/cars_20mpg.csv"))
    s = cars.runif()
    train = cars[s > .2]
    valid = cars[s <= .2]

    # choose the type model-building exercise (multinomial classification or regression). 0:regression, 1:binomial,
    # 2:multinomial
    problem = random.sample(range(3),1)[0]

    # pick the predictors and response column, along with the correct distribution
    predictors = ["displacement","power","weight","acceleration","year"]
    if problem == 1   :
        response_col = "economy_20mpg"
        distribution = "bernoulli"
        cars[response_col] = cars[response_col].asfactor()
    elif problem == 2 :
        response_col = "cylinders"
        distribution = "multinomial"
        cars[response_col] = cars[response_col].asfactor()
    else              :
        response_col = "economy"
        distribution = "gaussian"

    print "Distribution: {0}".format(distribution)
    print "Response column: {0}".format(response_col)

    # build first model
    ntrees1 = random.sample(range(5,21),1)[0]
    max_depth1 = random.sample(range(2,6),1)[0]
    min_rows1 = random.sample(range(10,16),1)[0]
    print "ntrees model 1: {0}".format(ntrees1)
    print "max_depth model 1: {0}".format(max_depth1)
    print "min_rows model 1: {0}".format(min_rows1)
    model1 = h2o.gbm(x=train[predictors],y=train[response_col],ntrees=ntrees1,max_depth=max_depth1, min_rows=min_rows1,
                     distribution=distribution,validation_x=valid[predictors],validation_y=valid[response_col])

    # save the model, remove all keys, then load the model
    model_path = h2o.save_model(model1, name="delete_model", force=True)
    restored_model = h2o.load_model(model_path)

    # continue building the model
    ntrees2 = ntrees1 + random.sample(range(5,21),1)[0]
    max_depth2 = max_depth1
    min_rows2 = min_rows1
    print "ntrees model 2: {0}".format(ntrees2)
    print "max_depth model 2: {0}".format(max_depth2)
    print "min_rows model 2: {0}".format(min_rows2)
    model2 = h2o.gbm(x=train[predictors],y=train[response_col],ntrees=ntrees2,max_depth=max_depth2, min_rows=min_rows2,
                     distribution=distribution,checkpoint=restored_model._id,validation_x=valid[predictors],
                     validation_y=valid[response_col])

    # continue building the model, but with different number of trees
    ntrees3 = ntrees2 + 50
    max_depth3 = max_depth1
    min_rows3 = min_rows1
    print "ntrees model 3: {0}".format(ntrees3)
    print "max_depth model 3: {0}".format(max_depth3)
    print "min_rows model 3: {0}".format(min_rows3)
    model3 = h2o.gbm(x=train[predictors],y=train[response_col],ntrees=ntrees3,max_depth=max_depth3, min_rows=min_rows3,
                     distribution=distribution,checkpoint=restored_model._id,validation_x=valid[predictors],
                     validation_y=valid[response_col])

    # build the equivalent of model 2 in one shot
    model4 = h2o.gbm(x=train[predictors],y=train[response_col],ntrees=ntrees2,max_depth=max_depth2,
                     min_rows=min_rows2,validation_frame=valid,distribution=distribution,validation_x=valid[predictors],
                     validation_y=valid[response_col])

    if problem == 0:
        assert isinstance(model2,type(model4))
        assert model2.mse(valid=True)==model4.mse(valid=True), "Expected Model 2 MSE: {0} to be the same as Model 4 MSE: {1}".format(model2.mse(valid=True), model4.mse(valid=True))
        assert model3.mse(valid=True)!=model4.mse(valid=True), "Expected Model 3 MSE: {0} to be different from Model 4 MSE: {1}".format(model3.mse(valid=True), model4.mse(valid=True))

    elif problem == 1:
        assert isinstance(model2,type(model4))
        assert model2.auc(valid=True)==model4.auc(valid=True), "Expected Model 2 AUC: {0} to be the same as Model 4 AUC: {1}".format(model2.auc(valid=True), model4.auc(valid=True))
        assert model3.auc(valid=True)!=model4.auc(valid=True), "Expected Model 3 AUC: {0} to be different from Model 4 AUC: {1}".format(model3.auc(valid=True), model4.auc(valid=True))

        assert model2.logloss(valid=True)==model4.logloss(valid=True), "Expected Model 2 Log Loss: {0} to be the same as Model 4 Log Loss: {1}".format(model2.logloss(valid=True), model4.logloss(valid=True))
        assert model3.logloss(valid=True)!=model4.logloss(valid=True), "Expected Model 2 Log Loss: {0} to be different from as Model 4 Log Loss: {1}".format(model2.logloss(valid=True), model4.logloss(valid=True))

        assert model2.giniCoef(valid=True)==model4.giniCoef(valid=True), "Expected Model 2 Gini Coef {0} to be the same as Model 4 Gini Coef: {1}".format(model2.giniCoef(valid=True), model4.giniCoef(valid=True))
        assert model3.giniCoef(valid=True)!=model4.giniCoef(valid=True), "Expected Model 2 Gini Coef: {0} to be the same as Model 4 Gini Coef: {1}".format(model2.giniCoef(valid=True), model4.giniCoef(valid=True))

    else:
        assert isinstance(model2,type(model4))
        assert model2.mse(valid=True)==model4.mse(valid=True), "Expected Model 2 MSE: {0} to be the same as Model 4 MSE: {1}".format(model2.mse(valid=True), model4.mse(valid=True))
        assert model3.mse(valid=True)!=model4.mse(valid=True), "Expected Model 3 MSE: {0} to be different from Model 4 MSE: {1}".format(model3.mse(valid=True), model4.mse(valid=True))

        assert model2.r2(valid=True)==model4.r2(valid=True), "Expected Model 2 R2: {0} to be the same as Model 4 R2: {1}".format(model2.r2(valid=True), model4.r2(valid=True))
        assert model3.r2(valid=True)!=model4.r2(valid=True), "Expected Model 3 R2: {0} to be different from Model 4 R2: {1}".format(model3.r2(valid=True), model4.r2(valid=True))

if __name__ == "__main__":
    h2o.run_test(sys.argv, cars_checkpoint)
