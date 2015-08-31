import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_1421(ip, port):
    

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    running_inside_h2o = tests.is_running_internal_to_h2o()

    if running_inside_h2o:
        hdfs_name_node = tests.get_h2o_internal_hdfs_name_node()
        hdfs_airlines_test_file  = "/datasets/airlines.test.csv"

        url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_airlines_test_file)
        air_test = h2o.import_file(url)

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_1421)
