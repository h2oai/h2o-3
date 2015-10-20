import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pubdev_1421():
    

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()
        hdfs_airlines_test_file  = "/datasets/airlines.test.csv"

        url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_airlines_test_file)
        air_test = h2o.import_file(url)
    else:
        raise(EnvironmentError, "Not running on H2O internal network.  No access to HDFS.")



if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_1421)
else:
    pubdev_1421()
