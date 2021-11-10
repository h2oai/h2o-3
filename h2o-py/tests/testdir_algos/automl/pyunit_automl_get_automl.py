from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML, get_leaderboard
from h2o.automl.autoh2o import get_automl
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset


def test_get_automl():
    ds = import_dataset()
    aml = H2OAutoML(project_name="test_get_automl",
                    max_models=2,
                    seed=1234)
    aml.train(y=ds.target, training_frame=ds.train)

    get_aml = get_automl(aml.project_name)

    assert aml.project_name == get_aml["project_name"]
    assert aml.leader.model_id == get_aml["leader"].model_id
    assert aml.leaderboard.get_frame_data() == get_aml["leaderboard"].get_frame_data()
    assert aml.event_log.get_frame_data() == get_aml["event_log"].get_frame_data()
    assert aml.training_info == get_aml['training_info']

    # PUBDEV-6599
    assert aml.project_name == get_aml.project_name
    assert aml.leader.model_id == get_aml.leader.model_id
    assert aml.leaderboard.frame_id == get_aml.leaderboard.frame_id
    assert aml.event_log.frame_id == get_aml.event_log.frame_id
    assert aml.training_info == get_aml.training_info

    # Test predictions
    predictions = aml.predict(ds.test)
    predictions_from_output = get_aml.predict(ds.test)
    assert (predictions == predictions_from_output).all()

    # Test get_leaderboard PUBDEV-7454
    assert (get_leaderboard(aml) == get_leaderboard(get_aml)).all()
    assert (get_leaderboard(aml, 'ALL') == get_leaderboard(get_aml, 'ALL')).all()


pu.run_tests([
    test_get_automl
])
