from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
import tempfile
import os
from tests import pyunit_utils
from h2o.estimators.anovaglm import H2OANOVAGLMEstimator as anovaglm

# This is the serialization and de-serialization test suggested by Michalk.  We built a anovaglm model.  Save it.
# Reload it and then compare the result from both models.
def test_anovaglm_serialization():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    y = 'CAPSULE'
    x = ['AGE','VOL','DCAPS']
    train[y] = train[y].asfactor()
    anovaglm_model = anovaglm(family='binomial', lambda_=0, missing_values_handling="skip")
    anovaglm_model.train(x=x, y=y, training_frame=train)

    tmpdir = tempfile.mkdtemp()
    model_path = anovaglm_model.download_model(tmpdir)
    result_frame_filename = os.path.join(tmpdir, "result_frame.csv")
    h2o.download_csv(anovaglm_model.result(), result_frame_filename)
    
    h2o.remove_all()
    result_frame_original = h2o.import_file(result_frame_filename)
    loaded_anovaglm_model = h2o.load_model(model_path)
    result_frame_loaded = loaded_anovaglm_model.result()
    for cind in list(range(0, result_frame_original.ncols)):
        for rind in list(range(0, result_frame_original.nrows)):
            if result_frame_original.type(cind) == 'real':
                assert abs(result_frame_original[rind, cind]-result_frame_loaded[rind, cind]) < 1e-6, \
                    "Expected: {0}. Actual: {1}".format(result_frame_original[rind, cind], result_frame_loaded[rind, cind])
            else:
                assert result_frame_original[rind, cind]==result_frame_loaded[rind, cind], \
                    "Expected: {0}. Actual: {1}".format(result_frame_original[rind, cind], result_frame_loaded[rind, cind])
            
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_anovaglm_serialization)
else:
    test_anovaglm_serialization()
