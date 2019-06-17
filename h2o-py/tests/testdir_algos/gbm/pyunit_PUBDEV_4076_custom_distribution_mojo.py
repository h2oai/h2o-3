from __future__ import print_function
import sys, os

sys.path.insert(1, "../../../")
import h2o
from tests.pyunit_utils import standalone_test
from tests.pyunit_utils import random_dataset, build_save_model_GBM, getMojoName, mojo_predict
from tests.pyunit_utils import compare_frames_local
from tests.pyunit_utils import CustomDistributionGaussian, CustomDistributionBernoulli

def custom_distribution_bernoulli():
    return h2o.upload_custom_distribution(CustomDistributionBernoulli, func_name="custom_bernoulli", func_file="custom_bernoulli.py")

# Test a mojo model is computed correctly with custom distribution
def custom_distribution_mojo_test():
    rows = 2000
    df = random_dataset('binomial', verbose=False, NTESTROWS=rows)
    df['response'] = df['response'].asnumeric()
    train = df[rows:, :]
    test = df[:rows, :]
    x = list(set(df.names) - {"response"})
    
    params = {
              'ntrees': 10, 
              'max_depth': 4, 
              'distribution': "custom",
              'custom_distribution_func': custom_distribution_bernoulli()
              }

    my_gbm = build_save_model_GBM(params, x, train, "response")
    mojo_name = getMojoName(my_gbm._id)
    tmp_dir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", mojo_name))

    h2o.download_csv(test[x], os.path.join(tmp_dir, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = mojo_predict(my_gbm, tmp_dir, mojo_name)  # load model and perform predict
    assert compare_frames_local(pred_h2o, pred_mojo, returnResult=True), "Predictions from model and MOJO model are not the same."


if __name__ == "__main__":
    standalone_test(custom_distribution_mojo_test)
else:
    custom_distribution_mojo_test()
