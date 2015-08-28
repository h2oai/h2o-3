import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_1829(ip,port):

    train =  h2o.import_file(path=h2o.locate("smalldata/jira/gbm_checkpoint_train.csv"))
    valid =  h2o.import_file(path=h2o.locate("smalldata/jira/gbm_checkpoint_valid.csv"))

    predictors = ["displacement","power","weight","acceleration","year"]
    response_col = "economy_20mpg"
    distribution = "bernoulli"
    train[response_col] = train[response_col].asfactor()
    valid[response_col] = valid[response_col].asfactor()

    ntrees1 = 5
    max_depth1 = 5
    min_rows1 = 10
    model1 = h2o.gbm(x=train[predictors],
                     y=train[response_col],
                     ntrees=ntrees1,
                     max_depth=max_depth1,
                     min_rows=min_rows1,
                     score_each_iteration=True,
                     distribution=distribution,
                     validation_x=valid[predictors],
                     validation_y=valid[response_col])

    ntrees2 = 10
    max_depth2 = 5
    min_rows2 = 10
    model2 = h2o.gbm(x=train[predictors],
                     y=train[response_col],
                     ntrees=ntrees2,
                     max_depth=max_depth2,
                     min_rows=min_rows2,
                     distribution=distribution,
                     score_each_iteration=True,
                     validation_x=valid[predictors],
                     validation_y=valid[response_col],
                     checkpoint=model1._id)

    model4 = h2o.gbm(x=train[predictors],
                     y=train[response_col],
                     ntrees=ntrees2,
                     max_depth=max_depth2,
                     min_rows=min_rows2,
                     distribution=distribution,
                     score_each_iteration=True,
                     validation_x=valid[predictors],
                     validation_y=valid[response_col])


    assert model2.auc(valid=True)==model4.auc(valid=True), "Expected Model 2 AUC: {0} to be the same as Model 4 AUC: {1}".format(model2.auc(valid=True), model4.auc(valid=True))
    assert model2.giniCoef(valid=True)==model4.giniCoef(valid=True), "Expected Model 2 Gini Coef {0} to be the same as Model 4 Gini Coef: {1}".format(model2.giniCoef(valid=True), model4.giniCoef(valid=True))
    assert model2.logloss(valid=True)==model4.logloss(valid=True), "Expected Model 2 Log Loss: {0} to be the same as Model 4 Log Loss: {1}".format(model2.logloss(valid=True), model4.logloss(valid=True))

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_1829)
