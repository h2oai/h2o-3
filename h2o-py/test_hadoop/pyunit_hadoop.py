import sys
sys.path.insert(1,"../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import os


def test_hadoop():
    '''
    Test H2O read and write to hdfs
    '''
    hdfs_name_node = os.getenv("NAME_NODE")
    h2o_data = h2o.import_file("hdfs://" + hdfs_name_node + "/datasets/100k.csv")
    train,test = h2o_data.split_frame(ratios=[0.1])
    h2o_glm = H2OGeneralizedLinearEstimator(family="binomial", alpha=0.5, Lambda=0.01)
    h2o_glm.train(x=range(1, 10), y=0, training_frame=train) # dont need to train on all features

    hdfs_model_path = os.getenv("MODEL_PATH")
    h2o.save_model(h2o_glm, "hdfs://" + hdfs_model_path)

    new_model = h2o.load_model("hdfs://" + hdfs_model_path)



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_hadoop)
else:
    test_hadoop()

