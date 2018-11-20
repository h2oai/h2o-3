from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator


def offset_init_train_deeplearning():
    # Connect to a pre-existing cluster
    insurance = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/insurance.csv"))
    insurance["offset"] = insurance["Holders"].log()
    insurance["Group"] = insurance["Group"].asfactor()
    insurance["Age"] = insurance["Age"].asfactor()
    insurance["District"] = insurance["District"].asfactor()

    # offset_column passed in the train method
    dl_train = H2ODeepLearningEstimator(distribution="tweedie",hidden=[1],epochs=1000,
                                        train_samples_per_iteration=-1, reproducible=True,activation="Tanh", single_node_mode=False,
                                        balance_classes=False, force_load_balance=False, seed=23123, tweedie_power=1.5,
                                        score_training_samples=0, score_validation_samples=0, stopping_rounds=0)
    dl_train.train(x=list(range(3)), y="Claims", training_frame=insurance, offset_column="offset")
    predictions_train = dl_train.predict(insurance).as_data_frame()

    # test offset_column passed in estimator init
    dl_init = H2ODeepLearningEstimator(distribution="tweedie", hidden=[1],epochs=1000,
                                       train_samples_per_iteration=-1, reproducible=True, activation="Tanh", single_node_mode=False,
                                       balance_classes=False, force_load_balance=False, seed=23123, tweedie_power=1.5,
                                       score_training_samples=0, score_validation_samples=0, stopping_rounds=0, offset_column="offset")
    dl_init.train(x=list(range(3)), y="Claims", training_frame=insurance)
    predictions_init = dl_init.predict(insurance).as_data_frame()

    # case the both offset column parameters are set and only the parameter in train will be used
    dl_init_train = H2ODeepLearningEstimator(distribution="tweedie",hidden=[1],epochs=1000,
                                             train_samples_per_iteration=-1, reproducible=True, activation="Tanh", single_node_mode=False,
                                             balance_classes=False, force_load_balance=False, seed=23123, tweedie_power=1.5,
                                             score_training_samples=0, score_validation_samples=0, stopping_rounds=0, offset_column="offset-test")
    dl_init_train.train(x=list(range(3)), y="Claims", training_frame=insurance, offset_column="offset")
    predictions_init_train = dl_init_train.predict(insurance).as_data_frame()

    assert predictions_train.equals(predictions_init), "Expected predictions of a model with offset_column in train method has to be same as predictions of a model with offset_column in constructor."
    assert predictions_train.equals(predictions_init_train), "Expected predictions of a model with offset_column in train method has to be same as predictions of a model with offset_column in both constructor and init."


if __name__ == "__main__":
    pyunit_utils.standalone_test(offset_init_train_deeplearning)
else:
    offset_init_train_deeplearning()
