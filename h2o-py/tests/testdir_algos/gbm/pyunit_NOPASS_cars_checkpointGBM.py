import h2o, tests
import random

def cars_checkpoint():

    cars = h2o.upload_file(tests.locate("smalldata/junit/cars_20mpg.csv"))
    s = cars.runif()
    train = cars[s > .2]
    valid = cars[s <= .2]

    print "\n*** Description (chunk distribution, etc) of training frame:"
    train.describe()
    print "\n*** Description (chunk distribution, etc) of validation frame:"
    valid.describe()

    # choose the type model-building exercise (multinomial classification or regression). 0:regression, 1:binomial,
    # 2:multinomial
    problem = random.sample(range(3),1)[0]

    # pick the predictors and response column, along with the correct distribution
    predictors = ["displacement","power","weight","acceleration","year"]
    if problem == 1   :
        response_col = "economy_20mpg"
        distribution = "bernoulli"
        train[response_col] = train[response_col].asfactor()
        valid[response_col] = valid[response_col].asfactor()
    elif problem == 2 :
        response_col = "cylinders"
        distribution = "multinomial"
        train[response_col] = train[response_col].asfactor()
        valid[response_col] = valid[response_col].asfactor()
    else              :
        response_col = "economy"
        distribution = "gaussian"

    print "\n*** Distribution: {0}".format(distribution)
    print "\n*** Response column: {0}".format(response_col)

    # build first model
    ntrees1 = 5
    max_depth1 = random.sample(range(2,6),1)[0]
    min_rows1 = random.sample(range(10,16),1)[0]
    print "\n*** Building model 1 with the following parameters:"
    print "*** ntrees model 1: {0}".format(ntrees1)
    print "*** max_depth model 1: {0}".format(max_depth1)
    print "*** min_rows model 1: {0}".format(min_rows1)
    model1 = h2o.gbm(x=train[predictors],
                     y=train[response_col],
                     ntrees=ntrees1,
                     max_depth=max_depth1,
                     min_rows=min_rows1,
                     score_each_iteration=True,
                     distribution=distribution,
                     validation_x=valid[predictors],
                     validation_y=valid[response_col])

    # save the model, then load the model
    model_path = h2o.save_model(model1, name="delete_model", force=True)
    restored_model = h2o.load_model(model_path)
    shutil.rmtree("delete_model")

    # continue building the model
    ntrees2 = ntrees1 + 5
    max_depth2 = max_depth1
    min_rows2 = min_rows1
    print "\n*** Continuing to build model 1 (now called model 2) with the following parameters:"
    print "*** ntrees model 2: {0}".format(ntrees2)
    print "*** max_depth model 2: {0}".format(max_depth2)
    print "*** min_rows model 2: {0}".format(min_rows2)
    model2 = h2o.gbm(x=train[predictors],
                     y=train[response_col],
                     ntrees=ntrees2,
                     max_depth=max_depth2,
                     min_rows=min_rows2,
                     distribution=distribution,
                     score_each_iteration=True,
                     validation_x=valid[predictors],
                     validation_y=valid[response_col],
                     checkpoint=restored_model._id)

    # continue building the model, but with different number of trees
    ntrees3 = ntrees2 + 50
    max_depth3 = max_depth1
    min_rows3 = min_rows1
    print "\n*** Continuing to build model 1 (now called model 3) with the following parameters:"
    print "*** ntrees model 3: {0}".format(ntrees3)
    print "*** max_depth model 3: {0}".format(max_depth3)
    print "*** min_rows model 3: {0}".format(min_rows3)
    model3 = h2o.gbm(x=train[predictors],
                     y=train[response_col],
                     ntrees=ntrees3,
                     max_depth=max_depth3,
                     min_rows=min_rows3,
                     distribution=distribution,
                     score_each_iteration=True,
                     validation_x=valid[predictors],
                     validation_y=valid[response_col],
                     checkpoint=restored_model._id)

    # build the equivalent of model 2 in one shot
    print "\n*** Building the equivalent of model 2 (called model 4) in one shot:"
    model4 = h2o.gbm(x=train[predictors],
                     y=train[response_col],
                     ntrees=ntrees2,
                     max_depth=max_depth2,
                     min_rows=min_rows2,
                     distribution=distribution,
                     score_each_iteration=True,
                     validation_x=valid[predictors],
                     validation_y=valid[response_col])

    print "\n*** Model Summary for model 2:"
    print model2.summary()
    print "\n*** Model Summary for model 3:"
    print model3.summary()
    print "\n*** Model Summary for model 4:"
    print model4.summary()

    print "\n*** Score History for model 2:"
    print model2.score_history()
    print "\n*** Score History for model 3:"
    print model3.score_history()
    print "\n*** Score History for model 4:"
    print model4.score_history()

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

        assert model2.giniCoef(valid=True)==model4.giniCoef(valid=True), "Expected Model 2 Gini Coef {0} to be the same as Model 4 Gini Coef: {1}".format(model2.giniCoef(valid=True), model4.giniCoef(valid=True))
        #assert model3.giniCoef(valid=True)!=model4.giniCoef(valid=True), "Expected Model 3 Gini Coef: {0} to be different from Model 4 Gini Coef: {1}".format(model2.giniCoef(valid=True), model4.giniCoef(valid=True))

    else:
        assert isinstance(model2,type(model4))
        assert model2.mse(valid=True)==model4.mse(valid=True), "Expected Model 2 MSE: {0} to be the same as Model 4 MSE: {1}".format(model2.mse(valid=True), model4.mse(valid=True))
        #assert model3.mse(valid=True)!=model4.mse(valid=True), "Expected Model 3 MSE: {0} to be different from Model 4 MSE: {1}".format(model3.mse(valid=True), model4.mse(valid=True))

        assert model2.r2(valid=True)==model4.r2(valid=True), "Expected Model 2 R2: {0} to be the same as Model 4 R2: {1}".format(model2.r2(valid=True), model4.r2(valid=True))
        #assert model3.r2(valid=True)!=model4.r2(valid=True), "Expected Model 3 R2: {0} to be different from Model 4 R2: {1}".format(model3.r2(valid=True), model4.r2(valid=True))


pyunit_test = cars_checkpoint
