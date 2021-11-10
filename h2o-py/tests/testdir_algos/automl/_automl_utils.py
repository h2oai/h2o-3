import h2o
from tests import pyunit_utils as pu, Namespace


def import_dataset(task_type='binary', split=True, larger=False, seed=0):
    if task_type == 'binary':
        df = h2o.import_file(path=pu.locate("smalldata/prostate/{}".format("prostate_complete.csv.zip" if larger else "prostate.csv")))
        target = "CAPSULE"
        df[target] = df[target].asfactor()
    elif task_type == 'multiclass':
        df = h2o.import_file(path=pu.locate("smalldata/iris/iris_wheader.csv"))
        target = "class"
        df[target] = df[target].asfactor()
    else:  # regression
        df = h2o.import_file(path=pu.locate("smalldata/extdata/australia.csv"))
        target = "runoffnew"
        
    splits = df.split_frame(ratios=[.8, .1],
                            destination_frames=[df.key+'_'+f for f in ['training', 'validation', 'test']],
                            seed=seed) if split else [df, None, None]

    return Namespace(train=splits[0], valid=splits[1], test=splits[2], target=target, target_idx=1)


def get_partitioned_model_names(leaderboard):
    model_names = Namespace()
    model_names.all = list(h2o.as_list(leaderboard['model_id'])['model_id'])
    model_names.se = [m for m in model_names.all if m.startswith('StackedEnsemble')]
    model_names.base = [m for m in model_names.all if m not in model_names.se]
    return model_names


