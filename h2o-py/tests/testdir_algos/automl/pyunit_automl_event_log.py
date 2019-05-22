from __future__ import print_function
import sys, os, datetime as dt
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML


def test_event_log():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = 'CAPSULE'
    train[y] = train[y].asfactor()

    aml = H2OAutoML(project_name="test_event_log",
                    max_models=2,
                    seed=1234)
    aml.train(y=y, training_frame=train)

    print(aml.event_log)
    assert aml.event_log.columns == ['timestamp', 'level', 'stage', 'message', 'name', 'value']
    assert aml.event_log.nrows > 10

    print(aml.training_info)
    assert int(aml.training_info['stop_epoch']) > int(aml.training_info['start_epoch'])
    stop_dt = dt.datetime.fromtimestamp(int(aml.training_info['stop_epoch']))
    now = dt.datetime.now()
    assert stop_dt.day == now.day
    assert stop_dt.hour == now.hour

    print(aml.leaderboard.nrows)
    assert int(aml.training_info['model_count']) == aml.leaderboard.nrows


def test_train_verbosity():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = 'CAPSULE'
    train[y] = train[y].asfactor()

    aml = H2OAutoML(project_name="test_train_verbosity",
                    keep_cross_validation_predictions=True,
                    max_models=2,
                    seed=1234)
    aml.train(y=y, training_frame=train)
    aml.train(y=y, training_frame=train, verbosity='info')
    aml.train(y=y, training_frame=train, verbosity='warn')


pyunit_utils.run_tests([
    test_event_log,
    test_train_verbosity
])
