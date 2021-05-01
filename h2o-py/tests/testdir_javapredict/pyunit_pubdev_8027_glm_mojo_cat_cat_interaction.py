import sys, os
sys.path.insert(1, "../../../")
import h2o
import pandas as pd
import numpy as np
from tests import pyunit_utils
import tempfile


def glm_mojo_num_num_interaction_test():
    pd_df = pd.DataFrame(np.array([[1,0,1,0,1,0,1,0,1,0],
                                   ["b",'a','a','b','a','b','a','b','b','a'],
                                   ['A', "B", 'B', 'A', 'A', 'A', 'B', 'B', 'A', 'A'],
                                   ['foo', 'bar', "foo", 'bar', 'foo', 'foo', 'bar', 'foo', 'bar','bar']]).T,
                         columns=['label','categorical_feat','categorical_feat2', 'categorical_feat3'])
    h2o_df = h2o.H2OFrame(pd_df, na_strings=["NA"])

    interaction_pairs = [("categorical_feat", "categorical_feat2"),("categorical_feat", "categorical_feat3"),
                         ("categorical_feat2", "categorical_feat3")]
    params = {'family':"binomial", 'alpha':0, 'lambda_search':False, 'interaction_pairs':interaction_pairs, 'standardize':False}
    xcols = ['categorical_feat','categorical_feat2','categorical_feat3']
    TMPDIR = tempfile.mkdtemp()
    glmBinomialModel = pyunit_utils.build_save_model_generic(params, xcols, h2o_df, "label", "glm", TMPDIR) # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(glmBinomialModel._id)

    h2o.download_csv(h2o.H2OFrame(pd_df[xcols]), os.path.join(TMPDIR, 'in.csv'))
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(glmBinomialModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=1e-10)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_mojo_num_num_interaction_test)
else:
    glm_mojo_num_num_interaction_test()
