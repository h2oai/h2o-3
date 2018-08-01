import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator



def varimp_test():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    # Run GBM
    my_gbm = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.1, distribution="multinomial")
    my_gbm.train(x=list(range(1,4)), y="class", training_frame=train)
    varimp = my_gbm.varimp()
    assert len(varimp) == 3, "expected varimp list to contain 3 entries, but it has " \
                             "{0}".format(len(varimp))
    assert len(varimp[0]) == 4, "expected varimp entry to contain 4 elements (variable, relative_importance, " \
                                "scaled_importance, percentage), but it has {0}".format(len(varimp[0]))
    assert all(isinstance(vi, h2o.model.model_base.VarImp) for vi in varimp)



if __name__ == "__main__":
    pyunit_utils.standalone_test(varimp_test)
else:
    varimp_test()
