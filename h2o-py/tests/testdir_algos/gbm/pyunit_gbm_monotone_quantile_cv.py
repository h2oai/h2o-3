import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils


# check quantile monotone constraints works with CV enabled - PUBDEV-8581
# tested locally also with gbm.monotonicity.checkEnabled=true
def gbm_monotone_quantile_test():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    train.describe()

    # Run GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="quantile", 
                                          monotone_constraints={"C2": 1}, 
                                          nfolds=5,
                                          quantile_alpha=0.8)
    my_gbm.train(x=["C2", "C3", "C4", "C5"], y="C1", training_frame=train)
    my_gbm.show()

    my_gbm_metrics = my_gbm.model_performance(train)
    my_gbm_metrics.show()

    print(my_gbm_metrics)


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_monotone_quantile_test)
else:
    gbm_monotone_quantile_test()
