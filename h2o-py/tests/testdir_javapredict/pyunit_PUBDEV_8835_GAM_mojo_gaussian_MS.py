import sys, os
sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from random import randint
import tempfile


def gam_gaussian_mojo_MS():
    h2o.remove_all()
    NTESTROWS = 200    # number of test dataset rows
    PROBLEM="gaussian"
    params = set_params()
    df = pyunit_utils.random_dataset(PROBLEM, seed=2, missing_fraction=0.5)
    dfnames = df.names

    # add GAM specific parameters
    params["gam_columns"] = []
    params["scale"] = []
    params["bs"] = []
    count = 0
    num_gam_cols = 3  # maximum number of gam columns
    for cname in dfnames:
        if not(cname == 'response') and (str(df.type(cname)) == "real"):
            params["gam_columns"].append(cname)
            params["scale"].append(0.001)
            params["bs"].append(3)
            count = count+1
            if count >= num_gam_cols:
                break

    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    exclude_list = {"response", params["gam_columns"][0]}
    x = list(set(df.names) - exclude_list)

    TMPDIR = tempfile.mkdtemp()
    gamGaussianModel = pyunit_utils.build_save_model_generic(params, x, train, "response", "gam", TMPDIR)  # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(gamGaussianModel._id)
    h2o.download_csv(test, os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(gamGaussianModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=1e-10)


def set_params():
    missingValues = ['MeanImputation']
    missing_values = missingValues[randint(0, len(missingValues)-1)]

    params = {'missing_values_handling': missing_values, 'family':"gaussian"}
    print(params)
    return params


if __name__ == "__main__":
    pyunit_utils.standalone_test(gam_gaussian_mojo_MS)
else:
    gam_gaussian_mojo_MS()
