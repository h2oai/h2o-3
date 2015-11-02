import sys
sys.path.insert(1,"../..")
from tests import pyunit_utils
import h2o

def read_1gb_cloud():
    '''
    Test h2o cluster read file.  Should run faster than what is observed under
    https://0xdata.atlassian.net/browse/PUBDEV-2254
    Right now this test is not run through Jenkins. Need to setup a cloud
    testing infrastructure which is a longer term project.
    You can take a look at markc_multimachine on jenkins for the current setup
    which is based on ec2
    '''

    # file must be seen by all nodes
    df = h2o.import_file("http://s3.amazonaws.com/h2o-datasets/allstate/train_set.zip")
    response = "Cat1"
    predictors = ["Cat2","Cat3","Cat4","Cat5"]
    df['Cat1'] = df['Cat1'].asfactor()
    df['Cat1'].summary()
    rnd = df['Cat1'].runif(seed=1234)
    train = df[rnd <= 0.8]
    test = df[rnd > 0.8]

if __name__ == "__main__":
    pyunit_utils.standalone_test(read_1gb_cloud)
else:
    read_1gb_cloud()
