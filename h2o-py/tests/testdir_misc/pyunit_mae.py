from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def iris_gbm_mae():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    train.describe()

    # Run GBM
    my_gbm = H2OGradientBoostingEstimator(ntrees=50,
                                          learn_rate=0.1,
                                          distribution="gaussian")
    my_gbm.train(x=list(range(1,4)),y="sepal_len",training_frame=train,validation_frame=train)
    my_gbm.show()

    my_gbm_metrics = my_gbm.model_performance(train)
    my_gbm_metrics.show()

    print(my_gbm_metrics)  #.show(criterion=my_gbm_metrics.theCriteria.PRECISION)

    #MAE calculated by hand From H2O predicted values
    pred = my_gbm.predict(train)
    diff_abs = abs(pred - train["sepal_len"])
    EXPmae = diff_abs.mean()
    print("EXPECTED MAE:")
    print(EXPmae)
    print("MAE CALCULATED BY H2O")
    print(my_gbm.mae())
    print("DIFFERENCE IN MANUAL AND H2O MAE CALCULATION:")
    print(my_gbm.mae() - EXPmae[0])
    assert my_gbm.mae() - EXPmae[0] < .000000001,"Incorrect MAE calculation"


if __name__ == "__main__":
    pyunit_utils.standalone_test(iris_gbm_mae)
else:
    iris_gbm_mae()