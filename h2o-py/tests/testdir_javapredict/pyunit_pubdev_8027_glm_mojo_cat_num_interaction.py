import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import tempfile


def glm_mojo_cat_num_interaction_test():
    pd_df = pd.DataFrame(np.array([[1,0,1,0,1,0], [3, 1, 2, 1, 1, 2], [1, 2, 2, 3, 3, 2], [2, 1, 1, 1, 3, 1]
                                   ["a","a","a","b","a","b"], ['Foo','Foo','Foo','Foo','Foo','Bar'], ['A', 'A', 'B', 'B', 'B', 'A']]).T,
                         columns=['label','numerical_feat','numerical_feat2', 'numerical_feat3', 
                                  'categorical_feat', 'categorical_feat2', 'categorical_feat3'])
    h2o_df = h2o.H2OFrame(pd_df, na_strings=["UNKNOWN"])

    interaction_pairs = [("numerical_feat", "categorical_feat"),("numerical_feat2", "categorical_feat2"),
                         ("numerical_feat3", "categorical_feat3")]
    params = {'family':"binomial", 'alpha':0, 'lambda_search':False, 'interaction_pairs':interaction_pairs, 'standardize':False}
    xcols = ['numerical_feat','numerical_feat2','categorical_feat','categorical_feat2']
    TMPDIR = tempfile.mkdtemp()
    glmBinomialModel = pyunit_utils.build_save_model_generic(params, xcols, h2o_df, "label", "glm", TMPDIR) # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(glmBinomialModel._id)

    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(glmBinomialModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 0.1, tol=1e-10)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_mojo_cat_num_interaction_test)
else:
    glm_mojo_cat_num_interaction_test()
