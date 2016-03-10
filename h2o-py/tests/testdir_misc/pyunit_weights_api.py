import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def weights_api():
    h2o_iris_data = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    r = h2o_iris_data.runif()
    iris_train = h2o_iris_data[r > 0.2]
    iris_valid = h2o_iris_data[r <= 0.2]

    # training_frame specified, weights column part of x
    gbm1 = H2OGradientBoostingEstimator(ntrees=5,distribution="multinomial")
    gbm1.train(x=["C1","C2","C3"],y=4, weights_column="C3", training_frame=iris_train)

    # training_frame specified, weights not part of x
    gbm1.train(x=["C1","C2","C3"],y=4, weights_column="C4", training_frame=iris_train)

    # training_frame not specified, weights part of x
    gbm1.train(x=["C1","C2","C3"],y=4, weights_column="C2", training_frame=iris_train)

    ########################################################################################################################

    # validation_frame specified, weights column part of validation_x
    gbm2 = H2OGradientBoostingEstimator(ntrees=5,distribution="multinomial")
    gbm2.train(x=["C1","C2","C3"],y=4, weights_column="C3", training_frame=iris_train, validation_frame=iris_valid)

    # validation_frame specified, weights not part of validation_x
    gbm2.train(x=["C1","C2","C3"],y=4, weights_column="C4", training_frame=iris_train, validation_frame=iris_valid)
    gbm2.train(x=["C1","C2","C3"],y=4, weights_column="C2", training_frame=iris_train, validation_frame=iris_valid)


if __name__ == "__main__":
    pyunit_utils.standalone_test(weights_api)
else:
    weights_api()
