from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
import tempfile
from tests import pyunit_utils
from h2o.estimators.anovaglm import H2OANOVAGLMEstimator as anovaglm

# This is the serialization and de-serialization test suggested by Michalk.  We built a anovaglm model.  Save it.
# Reload it and then compare the result from both models.
def test_anovaglm_serialization():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    y = 'CAPSULE'
    x = ['AGE','VOL','DCAPS']
    anovaglm_model = anovaglm(seed=12345, max_predictor_number=7)
    anovaglm_model.train(training_frame=train, x=x, y=y)
    result_frame_original = anovaglm_model.result()
    tmpdir = tempfile.mkdtemp()
    model_path = anovaglm_model.download_model(tmpdir)
    
    h2o.remove_all()
    loaded_anovaglm_model = h2o.load_model(model_path)
    result_frame_original = loaded_anovaglm_model.result()
    pyunit_utils.compare_frames_local(result_frame_original, result_frame_original, prob=1)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_anovaglm_serialization)
else:
    test_anovaglm_serialization()
