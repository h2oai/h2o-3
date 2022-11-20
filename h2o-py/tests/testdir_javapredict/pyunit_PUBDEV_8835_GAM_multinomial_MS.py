import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from random import randint
import tempfile

def gam_multinomial_mojo_MS():
    h2o.remove_all()
    NTESTROWS = 200    # number of test dataset rows
    PROBLEM="multinomial"
    params = set_params()   # set deeplearning model parameters
    df1 = pyunit_utils.random_dataset(PROBLEM, missing_fraction=0.001)   # generate random dataset
    df = pyunit_utils.random_dataset_real_only(nrow=df1.nrow, ncol = 3)
    df.set_names(["gam_col1", "gam_col2", "gam_col3"])
    df = df1.cbind(df)
    dfnames = df.names
    # add GAM specific parameters
    params["gam_columns"] = []
    params["scale"] = []
    params["family"] = "multinomial"
    params["bs"] = []
    count = 0
    num_gam_cols = 3    # maximum number of gam columns
    for cname in dfnames:
        if not(cname == 'response') and (str(df.type(cname)) == "real"):
            params["gam_columns"].append(cname)
            params["scale"].append(0.001)
            params["bs"].append(3)
            count = count+1
            if (count >= num_gam_cols):
                break
    
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    x = list(set(df.names) - {"response"})
    TMPDIR = tempfile.mkdtemp()
    gamMultinomialModel = pyunit_utils.build_save_model_generic(params, x, train, "response", "gam", TMPDIR) # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(gamMultinomialModel._id)
    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(gamMultinomialModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 0.1, tol=1e-10)    # make sure operation sequence is preserved from Tomk        h2o.save_model(glmOrdinalModel, path=TMPDIR, force=True)  # save model for debugging

def set_params():
    missingValues = ['MeanImputation']
    missing_values = missingValues[randint(0, len(missingValues)-1)]

    params = {'missing_values_handling': missing_values, 'family':"ordinal"}
    print(params)
    return params


if __name__ == "__main__":
    pyunit_utils.standalone_test(gam_multinomial_mojo_MS)
else:
    gam_multinomial_mojo_MS()
