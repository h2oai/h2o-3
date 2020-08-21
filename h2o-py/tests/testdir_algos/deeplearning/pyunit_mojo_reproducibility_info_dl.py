import sys, os

from h2o.two_dim_table import H2OTwoDimTable

sys.path.insert(1, "../../../")
import h2o, tests
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

def dl_mojo_reproducibility_info():

    # Training data
    train_data = h2o.import_file(path=tests.locate("smalldata/gbm_test/ecology_model.csv"))
    train_data = train_data.drop('Site')
    train_data['Angaus'] = train_data['Angaus'].asfactor()
    print(train_data.describe())
    train_data.head()

    # Testing data
    test_data = h2o.import_file(path=tests.locate("smalldata/gbm_test/ecology_eval.csv"))
    test_data['Angaus'] = test_data['Angaus'].asfactor()
    print(test_data.describe())
    test_data.head()

    # Run DeepLearning
    model = H2ODeepLearningEstimator(loss="CrossEntropy", epochs=1000, hidden=[20,20,20])
    model.train(x=list(range(1,train_data.ncol)),
             y="Angaus",
             training_frame=train_data,
             validation_frame=test_data)

    print("Downloading Java prediction model code from H2O")
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", model._id))
    os.makedirs(TMPDIR)
    mojo_path = model.download_mojo(path=TMPDIR)
    dlModel = h2o.upload_mojo(mojo_path=mojo_path)
    
    isinstance(dlModel._model_json['output']['reproducibility_information_table'][1]['h2o_cluster_uptime'][0], float)
    isinstance(dlModel._model_json['output']['reproducibility_information_table'][0]['java_version'][0], str)
    assert(dlModel._model_json['output']['reproducibility_information_table'][2]['input_frame'][0] == 'training_frame')
    assert(dlModel._model_json['output']['reproducibility_information_table'][2]['input_frame'][1] == 'validation_frame')


if __name__ == "__main__":
    pyunit_utils.standalone_test(dl_mojo_reproducibility_info)
else:
    dl_mojo_reproducibility_info()
