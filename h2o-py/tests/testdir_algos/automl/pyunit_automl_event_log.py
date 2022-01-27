from __future__ import print_function
import sys, os, datetime as dt

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset


def test_event_log():
    ds = import_dataset()
    aml = H2OAutoML(project_name="test_event_log",
                    max_models=2,
                    seed=1234)
    aml.train(y=ds.target, training_frame=ds.train)

    print(aml.event_log)
    assert aml.event_log.columns == ['timestamp', 'level', 'stage', 'message', 'name', 'value']
    assert aml.event_log.nrows > 10

    print(aml.training_info)
    assert int(aml.training_info['stop_epoch']) > int(aml.training_info['start_epoch'])
    stop_dt = dt.datetime.fromtimestamp(int(aml.training_info['stop_epoch']))
    now = dt.datetime.now()
    # test that stop_epoch is time encoded as unix epoch
    assert abs(stop_dt - now) < dt.timedelta(minutes=1)
    assert abs(int(aml.training_info['duration_secs']) - (int(aml.training_info['stop_epoch']) - int(aml.training_info['start_epoch']))) <= 1


def test_train_verbosity():
    ds = import_dataset()
    make_aml = lambda verbosity=None: H2OAutoML(project_name="test_train_verbosity",
                                                keep_cross_validation_predictions=True,
                                                max_models=2,
                                                stopping_tolerance=0.01,  # triggers a warning event log message
                                                seed=1234,
                                                verbosity=verbosity)
    print("\n\nverbosity off")
    with pu.capture_print() as disabled:
        make_aml().train(y=ds.target, training_frame=ds.train)
    print("\n\nverbosity debug")
    with pu.capture_print() as debug:
        make_aml('debug').train(y=ds.target, training_frame=ds.train)
    print("\n\nverbosity info")
    with pu.capture_print() as info:
        make_aml('info').train(y=ds.target, training_frame=ds.train)
    print("\n\nverbosity warn")
    with pu.capture_print() as warn:
        make_aml('warn').train(y=ds.target, training_frame=ds.train)
    print("\n\nverbosity error")
    with pu.capture_print() as error:
        make_aml('error').train(y=ds.target, training_frame=ds.train)
    
    if disabled is not None:  # no check in Py2 
        print(len(disabled.out), len(error.out), len(warn.out), len(info.out), len(debug.out))
        assert len(disabled.out) <= len(error.out) <= len(warn.out) < len(info.out) < len(debug.out)


pu.run_tests([
    test_event_log,
    test_train_verbosity
])
