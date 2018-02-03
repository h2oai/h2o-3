import h2o

from tests import pyunit_utils


def pubdev_5167():
    training_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/airlines_all.05p.csv"))

    if 'IsDepDelayed' in training_data.names:
        training_data['IsDepDelayed'] = training_data['IsDepDelayed'].asfactor()
    else:
        raise AttributeError("label {0} not found".format('IsDepDelayed'))

    estimator = h2o.estimators.deeplearning.H2ODeepLearningEstimator(hidden=[50, 50, 50, 50, 50],
                                                                     activation='rectifier',
                                                                     adaptive_rate=True,
                                                                     balance_classes=True,
                                                                     epochs=50,
                                                                     shuffle_training_data=True,
                                                                     score_each_iteration=True,
                                                                     stopping_metric='auc',
                                                                     stopping_rounds=5,
                                                                     stopping_tolerance=.01,
                                                                     use_all_factor_levels=False,
                                                                     variable_importances=False,
                                                                     export_weights_and_biases=True,
                                                                     seed=200)
    estimator.train(x=training_data.names[:-1], y=training_data.names[-1], training_frame=training_data)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5167)
else:
    pubdev_5167()
