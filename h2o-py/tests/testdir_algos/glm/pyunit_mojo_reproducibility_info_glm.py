import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils

def glm_mojo_reproducibility_info():
    params = {'family':"fractionalbinomial", 'alpha':[0], 'lambda_':[0],
              'standardize':False, "compute_p_values":True}
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/fraction_binommialOrig.csv"))
    x = ["log10conc"]
    y = "y"

    glmModel = pyunit_utils.build_save_model_GLM(params, x, train, y) # build and save mojo model

    isinstance(glmModel._model_json['output']['reproducibility_information_table'][1]['h2o_cluster_uptime'][0], float)
    isinstance(glmModel._model_json['output']['reproducibility_information_table'][0]['java_version'][0], str)
    assert(glmModel._model_json['output']['reproducibility_information_table'][2]['input_frame'][0] == 'training_frame')


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_mojo_reproducibility_info)
else:
    glm_mojo_reproducibility_info()
