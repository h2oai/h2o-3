import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.rulefit import H2ORuleFitEstimator


def titanic():
    df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv",
                       col_types={'pclass': "enum", 'survived': "enum"})
    x =  ["age", "sibsp", "parch", "fare", "sex", "pclass"]

    rf_h2o = H2ORuleFitEstimator(max_rule_length=10, max_num_rules=100, seed=1234, model_type="rules")
    rf_h2o.train(training_frame=df, x=x, y="survived")

    print(rf_h2o.rule_importance())



if __name__ == "__main__":
  pyunit_utils.standalone_test(titanic)
else:
    titanic()
