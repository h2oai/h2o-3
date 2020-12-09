import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.rulefit import H2ORuleFitEstimator


def titanic():
    df = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"),
                       col_types={'pclass': "enum", 'survived': "enum"})
    x =  ["age", "sibsp", "parch", "fare", "sex", "pclass"]

    # Split the dataset into train and test
    train, test = df.split_frame(ratios=[.8], seed=1234)

    rfit = H2ORuleFitEstimator(min_rule_length=1, max_rule_length=10, max_num_rules=100, seed=1234, model_type="rules")
    rfit.train(training_frame=train, x=x, y="survived")

    print(rfit.rule_importance())
    assert rfit._model_json["output"]["model_summary"] is not None, "model_summary should be present"
    assert len(rfit._model_json["output"]["model_summary"]._cell_values) > 0, "model_summary's content should be present"

    rfit.predict(test)



if __name__ == "__main__":
  pyunit_utils.standalone_test(titanic)
else:
    titanic()
