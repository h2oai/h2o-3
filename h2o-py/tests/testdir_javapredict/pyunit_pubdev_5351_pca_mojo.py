from builtins import range
import sys, os
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator

def pca_mojo():
    h2o.remove_all()
    NTESTROWS = 200    # number of test dataset rows
    df = pyunit_utils.random_dataset("regression", ncol_upper=8000, ncol_lower=5000, missing_fraction=0.001, seed=1234)
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    x = df.names
    transform_types = ["NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE"] # pyunit test loop through transform
    for transformN in transform_types:  # compare H2O predict and mojo predict for all dataset transform types
        pcaModel = H2OPrincipalComponentAnalysisEstimator(k=3, transform=transformN, seed=1234, impute_missing=True,
                                                          use_all_factor_levels=False)
        pcaModel.train(x=x, training_frame=train)
        pyunit_utils.saveModelMojo(pcaModel) # save mojo model
        MOJONAME = pyunit_utils.getMojoName(pcaModel._id)
        TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
        h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
        pred_h2o, pred_mojo = pyunit_utils.mojo_predict(pcaModel, TMPDIR, MOJONAME) # save mojo predict
  
        for col in range(pred_h2o.ncols):
            if pred_h2o[col].isfactor():
                pred_h2o[col] = pred_h2o[col].asnumeric()

        print("Comparing mojo predict and h2o predict...")
        pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 1, tol=1e-10)

if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_mojo)
else:
    pca_mojo()
