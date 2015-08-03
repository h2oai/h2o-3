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

    # pick the predictors and response column
    predictors = ["displacement","power","weight","acceleration","year"]
    if problem == 1   :
        response_col = "economy_20mpg"
        cars[response_col] = cars[response_col].asfactor()
    elif problem == 2 :
        response_col = "cylinders"
        cars[response_col] = cars[response_col].asfactor()
    else              :
        response_col = "economy"

    print "Response column: {0}".format(response_col)

    # build first model
    ntrees1 = random.sample(range(5,21),1)[0]
    max_depth1 = random.sample(range(2,6),1)[0]
    min_rows1 = random.sample(range(10,16),1)[0]
    print "ntrees model 1: {0}".format(ntrees1)
    print "max_depth model 1: {0}".format(max_depth1)
    print "min_rows model 1: {0}".format(min_rows1)
    model1 = h2o.random_forest(x=train[predictors],y=train[response_col],ntrees=ntrees1,max_depth=max_depth1,
                               min_rows=min_rows1, validation_x=valid[predictors],validation_y=valid[response_col],
                               seed=2345)

    # save the model, remove all keys, then load the model
    model_path = h2o.save_model(model1, name="delete_model", force=True)
    restored_model = h2o.load_model(model_path)

    # continue building the model with the same max_depth and min_rows
    ntrees2 = ntrees1 + random.sample(range(5,21),1)[0]
    max_depth2 = max_depth1
    min_rows2 = min_rows1
    print "ntrees model 2: {0}".format(ntrees2)
    print "max_depth model 2: {0}".format(max_depth2)
    print "min_rows model 2: {0}".format(min_rows2)
    model2 = h2o.random_forest(x=train[predictors],y=train[response_col],ntrees=ntrees2,max_depth=max_depth2,
                               min_rows=min_rows2,checkpoint=restored_model._id,validation_x=valid[predictors],
                               validation_y=valid[response_col],seed=2345)

    # continue building the model, but with different max_depth and min_rows (ensemble)
    ntrees3 = ntrees2
    max_depth3 = max_depth2 + random.sample(range(3,6),1)[0]
    min_rows3 = min_rows2 + random.sample(range(5,10),1)[0]
    print "ntrees model 3: {0}".format(ntrees3)
    print "max_depth model 3: {0}".format(max_depth3)
    print "min_rows model 3: {0}".format(min_rows3)
    model3 = h2o.random_forest(x=train[predictors],y=train[response_col],ntrees=ntrees3,max_depth=max_depth3,
                               min_rows=min_rows3,checkpoint=restored_model._id,validation_x=valid[predictors],
                               validation_y=valid[response_col],seed=2345)

    # build the equivalent of model 2 in one shot
    model4 = h2o.random_forest(x=train[predictors],y=train[response_col],ntrees=ntrees2,max_depth=max_depth2,
                               min_rows=min_rows2,validation_frame=valid,validation_x=valid[predictors],
                               validation_y=valid[response_col],seed=2345)

    if problem == 0:
        assert isinstance(model2,type(model4))
        print "model2.mse", model2.mse, "model4.mse", model4.mse
        print "model3.mse", model3.mse, "model4.mse", model4.mse
        assert model2.mse(valid=True)==model4.mse(valid=True)
        assert model3.mse(valid=True)!=model4.mse(valid=True)

    elif problem == 1:
        assert isinstance(model2,type(model4))
        print "model2.auc", model2.auc, "model4.auc", model4.auc
        print "model3.auc", model3.auc, "model4.auc", model4.auc
        assert model2.auc(valid=True)==model4.auc(valid=True)
        assert model3.auc(valid=True)!=model4.auc(valid=True)

        print "model2.logloss", model2.logloss, "model4.logloss", model4.logloss
        print "model3.logloss", model3.logloss, "model4.logloss", model4.logloss
        assert model2.logloss(valid=True)==model4.logloss(valid=True)
        assert model3.logloss(valid=True)!=model4.logloss(valid=True)

        print "model2.giniCoef", model2.giniCoef, "model4.giniCoef", model4.giniCoef
        print "model3.giniCoef", model3.giniCoef, "model4.giniCoef", model4.giniCoef
        assert model2.giniCoef(valid=True)==model4.giniCoef(valid=True)
        assert model3.giniCoef(valid=True)!=model4.giniCoef(valid=True)

    else:
        assert isinstance(model2,type(model4))
        assert model2.mse(valid=True)==model4.mse(valid=True)
        assert model3.mse(valid=True)!=model4.mse(valid=True)
        print "model2.mse", model2.mse, "model4.mse", model4.mse
        print "model3.mse", model3.mse, "model4.mse", model4.mse

        assert model2.r2(valid=True)==model4.r2(valid=True)
        assert model3.r2(valid=True)!=model4.r2(valid=True)
        print "model2.r2", model2.r2, "model4.r2", model4.r2
        print "model3.r2", model3.r2, "model4.r2", model4.r2

if __name__ == "__main__":
    h2o.run_test(sys.argv, cars_checkpoint)
