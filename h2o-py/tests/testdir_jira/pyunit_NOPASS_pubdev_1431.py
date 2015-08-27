import sys, os
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_1431(ip, port):

    running_inside_h2o = tests.is_running_internal_to_h2o()

    if running_inside_h2o:
        hdfs_name_node = tests.get_h2o_internal_hdfs_name_node()
        airlines_billion_file = "/datasets/airlinesbillion.csv"
        url = "hdfs://{0}{1}".format(hdfs_name_node, airlines_billion_file)
        airlines_billion = h2o.import_file(url)
        airlines_billion[30] = airlines_billion[30].asfactor()
        gbm = h2o.gbm(x=airlines_billion[0:30], y=airlines_billion[30], ntrees=1, distribution="bernoulli", max_depth=1)
        predictions = gbm.predict(airlines_billion)
        csv = os.path.join(os.getcwd(),"delete.csv")
        h2o.download_csv(predictions,csv)
        os.remove(csv)
    else:
        print "Not running on H2O internal network.  No access to HDFS."

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_1431)
