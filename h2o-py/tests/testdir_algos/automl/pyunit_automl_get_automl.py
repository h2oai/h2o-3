from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML, get_leaderboard
from h2o.automl.autoh2o import get_automl


def test_get_automl():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = 'CAPSULE'
    train[y] = train[y].asfactor()

    aml = H2OAutoML(project_name="test_get_automl",
                    max_models=2,
                    seed=1234)
    aml.train(y=y, training_frame=train)

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
    predictions = aml.predict(train)
    predictions_from_output = get_aml.predict(train)
    assert (predictions == predictions_from_output).all()

    # Test get_leaderboard PUBDEV-7454
    assert (get_leaderboard(aml) == get_leaderboard(get_aml)).all()
    assert (get_leaderboard(aml, 'ALL') == get_leaderboard(get_aml, 'ALL')).all()

pyunit_utils.run_tests([test_get_automl])
