import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from random import randint
import tempfile

def gam_gaussian_mojo():
    h2o.remove_all()
    NTESTROWS = 200    # number of test dataset rows
    PROBLEM="gaussian"
    params = set_params()   # set deeplearning model parameters
    df = random_dataset(PROBLEM, missing_fraction=0.5, seed=1)   # generate random dataset
    dfnames = df.names
    # add GAM specific parameters
    params["gam_columns"] = []
    params["scale"] = []
    count = 0
    num_gam_cols = 3    # maximum number of gam columns
    for cname in dfnames:
        if not(cname == 'response') and (str(df.type(cname)) == "real"):
            params["gam_columns"].append(cname)
            params["scale"].append(0.001)
            count = count+1
            if (count >= num_gam_cols):
                break
    
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    exclude_list = {"response"}
    exclude_list.add(params["gam_columns"][0])
    x = list(set(df.names) - exclude_list)

    TMPDIR = tempfile.mkdtemp()
    gamGaussianModel = pyunit_utils.build_save_model_generic(params, x, train, "response", "gam", TMPDIR) # build and save mojo model
    MOJONAME = pyunit_utils.getMojoName(gamGaussianModel._id)
    h2o.download_csv(test, os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(gamGaussianModel, TMPDIR, MOJONAME)  # load model and perform predict
    h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=1e-10)    # make sure operation sequence is preserved from Tomk        h2o.save_model(glmOrdinalModel, path=TMPDIR, force=True)  # save model for debugging

def set_params():
    missingValues = ['MeanImputation']
    missing_values = missingValues[randint(0, len(missingValues)-1)]

    params = {'missing_values_handling': missing_values, 'family':"gaussian"}
    print(params)
    return params

def random_dataset(response_type, verbose=True,  NTESTROWS=200, missing_fraction=0.0, seed=None):
    """Create and return a random dataset."""
    if verbose: print("\nCreating a dataset for a %s problem:" % response_type)
    fractions = {'real_fraction': 0.925363793458878, 'categorical_fraction': 0.9625390964218535,
                 'integer_fraction': 0.5693588274554572, 'time_fraction': 0.19987260017514685,
                 'string_fraction': 0.893090913162827, 'binary_fraction': 0.12909731789008272}
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] /= 3
    fractions["time_fraction"] /= 2

    sum_fractions = sum(fractions.values())
    for k in fractions:
        fractions[k] /= sum_fractions

    response_factors = 1
    df = h2o.create_frame(rows=25000 + NTESTROWS, cols=20,
                          missing_fraction=missing_fraction,
                          has_response=True, response_factors=response_factors, positive_response=True, factors=10,
                          seed=seed, **fractions)
    return df

if __name__ == "__main__":
    pyunit_utils.standalone_test(gam_gaussian_mojo)
else:
    gam_gaussian_mojo()
