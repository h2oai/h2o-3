import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import h2o.exceptions
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import get_partitioned_model_names


def import_dataset(seed=0, mode='binary'):
    df = h2o.import_file(path=pu.locate("smalldata/titanic/titanic_expanded.csv"), header=1)
    target = dict(
        binary='survived',
        multiclass='pclass',
        regression='fare'
    )[mode]
    
    fr = df.split_frame(ratios=[.8], seed=seed)
    return pu.ns(train=fr[0], test=fr[1], target=target)


def check_mojo_pojo_availability(model):
    if model.algo in ['stackedensemble']:
        assert not model.have_mojo, "Model %s should not support MOJO" % model.model_id  # because base models don't
        assert not model.have_pojo, "Model %s should not support POJO" % model.model_id
    elif model.algo in ['deeplearning']:
        assert model.have_mojo, "Model %s should support MOJO" % model.model_id
        assert model.have_pojo, "Model %s should support POJO" % model.model_id
    else:
        assert model.algo in ['pipeline']
        assert not model.have_mojo, "Model %s should not support MOJO" % model.model_id
        assert not model.have_pojo, "Model %s should not support POJO" % model.model_id
        

def check_predict(model, test):
    predictions = model.predict(test)
    assert predictions is not None
    assert predictions.nrows == test.nrows
    # print(predictions)
    

def test_target_encoding_binary():
    ds = import_dataset(mode='binary')
    aml = H2OAutoML(project_name="automl_with_te_binary",
                    max_models=5,
                    preprocessing=['target_encoding'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, leaderboard_frame=ds.test)
    lb = aml.leaderboard
    print(lb)
    model_ids = list(h2o.as_list(lb['model_id'])['model_id'])
    assert any(m.startswith("Pipeline") for m in model_ids), "at least a Pipeline model should have been trained"
    # we can't really verify from client if TE was correctly applied... so just using a poor man's check:
    mem_keys = h2o.ls().key
    # print(mem_keys)
    assert any(k == "default_TE_1_model" for k in mem_keys), "a TE model should have been trained"
    for mid in get_partitioned_model_names(lb).all:
        model = h2o.get_model(mid)
        check_mojo_pojo_availability(model)
        check_predict(model, ds.test)
        

def test_target_encoding_multiclass():
    ds = import_dataset(mode='multiclass')
    aml = H2OAutoML(project_name="automl_with_te_multiclass",
                    max_models=5,
                    preprocessing=['target_encoding'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, leaderboard_frame=ds.test)
    lb = aml.leaderboard
    print(lb)
    model_ids = list(h2o.as_list(lb['model_id'])['model_id'])
    assert any(m.startswith("Pipeline") for m in model_ids), "at least a Pipeline model should have been trained"
    # we can't really verify from client if TE was correctly applied... so just using a poor man's check:
    mem_keys = h2o.ls().key
    # print(mem_keys)
    assert any(k == "default_TE_1_model" for k in mem_keys), "a TE model should have been trained"
    for mid in get_partitioned_model_names(lb).all:
        model = h2o.get_model(mid)
        check_mojo_pojo_availability(model)
        check_predict(model, ds.test)
    
    
def test_target_encoding_regression():
    ds = import_dataset(mode='regression')
    aml = H2OAutoML(project_name="automl_with_te_regression",
                    max_models=5,
                    preprocessing=['target_encoding'],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, leaderboard_frame=ds.test)
    lb = aml.leaderboard
    print(lb)
    model_ids = list(h2o.as_list(lb['model_id'])['model_id'])
    assert any(m.startswith("Pipeline") for m in model_ids), "at least a Pipeline model should have been trained"
    # we can't really verify from client if TE was correctly applied... so just using a poor man's check:
    mem_keys = h2o.ls().key
    # print(mem_keys)
    assert any(k == "default_TE_1_model" for k in mem_keys), "a TE model should have been trained"
    for mid in get_partitioned_model_names(lb).all:
        model = h2o.get_model(mid)
        check_mojo_pojo_availability(model)
        check_predict(model, ds.test)


pu.run_tests([
    test_target_encoding_binary,
    test_target_encoding_multiclass,
    test_target_encoding_regression
])
