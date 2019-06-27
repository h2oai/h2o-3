from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML
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

pyunit_utils.run_tests([test_get_automl])
