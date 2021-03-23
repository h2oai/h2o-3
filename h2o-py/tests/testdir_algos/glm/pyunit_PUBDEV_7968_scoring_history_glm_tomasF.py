from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# this test was given to me by Tomas Fryda and it was failing.  This test does not need to have an assert.  It just
# needs to run to completion without failing.
def test_glm_scoring_history_TomasF():
    df = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    df["CAPSULE"] = df["CAPSULE"].asfactor()

    glmModel = glm(generate_scoring_history=True)
    glmModel.train(y="CAPSULE", training_frame=df)
    glmModel.scoring_history()
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_scoring_history_TomasF)
else:
    test_glm_scoring_history_TomasF()
