import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def spaces_in_column_names():
    train_data = h2o.upload_file(path=pyunit_utils.locate("smalldata/jira/spaces_in_column_names.csv"))
    train_data.show()
    train_data.describe()
    train_data["r e s p o n s e"] = train_data["r e s p o n s e"].asfactor()
    X = ["p r e d i c t o r 1","predictor2","p r e d i ctor3","pre d ictor4","predictor5"]
    gbm = H2OGradientBoostingEstimator(ntrees=1, distribution="bernoulli", min_rows=1)
    gbm.train(x=X,y="r e s p o n s e", training_frame=train_data)
    gbm.show()

if __name__ == "__main__":
    pyunit_utils.standalone_test(spaces_in_column_names)
else:
    spaces_in_column_names()
