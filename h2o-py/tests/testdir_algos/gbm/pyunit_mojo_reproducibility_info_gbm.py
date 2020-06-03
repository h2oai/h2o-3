import sys, os


sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from random import randint
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def gbm_mojo_reproducibility_info():
    problems = ['binomial', 'multinomial', 'regression']
    PROBLEM = problems[randint(0, (len(problems) - 1))]
    TESTROWS = 2000
    df = pyunit_utils.random_dataset(PROBLEM, verbose=False, NTESTROWS=TESTROWS)
    train = df[TESTROWS:, :]
    x = list(set(df.names) - {"respose"})
    params = {'ntrees': 50, 'learn_rate': 0.1, 'max_depth': 4}
    gbmModel = pyunit_utils.build_save_model_GBM(params, x, train, "response")

    isinstance(gbmModel._model_json['output']['reproducibility_information_table'][1]['h2o_cluster_uptime'][0], float)
    isinstance(gbmModel._model_json['output']['reproducibility_information_table'][0]['java_version'][0], str)
    assert(gbmModel._model_json['output']['reproducibility_information_table'][2]['input_frame'][0] == 'training_frame')

    ecology = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    ecology['Angaus'] = ecology['Angaus'].asfactor()
    train, calib = ecology.split_frame(seed = 12354)
    predictors = ecology.columns[3:13]
    w = h2o.create_frame(binary_fraction=1, binary_ones_fraction=0.5, missing_fraction=0, rows=744, cols=1)
    w.set_names(["weight"])
    train = train.cbind(w)
    model = H2OGradientBoostingEstimator(ntrees=10, max_depth=5,  min_rows=10, learn_rate=0.1, distribution="multinomial",
                     weights_column="weight", calibrate_model=True, calibration_frame=calib)
    model.train(x=predictors,y="Angaus",training_frame=train)

    print("Downloading Java prediction model code from H2O")
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", model._id))
    os.makedirs(TMPDIR)
    mojo_path = model.download_mojo(path=TMPDIR)
    gbmModel = h2o.upload_mojo(mojo_path=mojo_path)

    isinstance(gbmModel._model_json['output']['reproducibility_information_table'][1]['h2o_cluster_uptime'][0], float)
    isinstance(gbmModel._model_json['output']['reproducibility_information_table'][0]['java_version'][0], str)
    assert(gbmModel._model_json['output']['reproducibility_information_table'][2]['input_frame'][0] == 'training_frame')
    assert(gbmModel._model_json['output']['reproducibility_information_table'][2]['input_frame'][2] == 'calibration_frame')


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_mojo_reproducibility_info)
else:
    gbm_mojo_reproducibility_info()
