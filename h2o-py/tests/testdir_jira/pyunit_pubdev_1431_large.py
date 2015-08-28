import sys, os
sys.path.insert(1, "../../")
import h2o, tests
import random

def pubdev_1431(ip, port):

    running_inside_h2o = tests.is_running_internal_to_h2o()

    if running_inside_h2o:
        hdfs_name_node = tests.get_h2o_internal_hdfs_name_node()
        airlines_billion_file_1 = "/datasets/airlinesbillion.csv"
        url = "hdfs://{0}{1}".format(hdfs_name_node, airlines_billion_file_1)
        airlines_billion_1 = h2o.import_file(url)

        airlines_billion_1[30] = airlines_billion_1[30].asfactor()
        gbm = h2o.gbm(x=airlines_billion_1[0:30], y=airlines_billion_1[30], ntrees=1, distribution="bernoulli", max_depth=1)

        predictions = gbm.predict(airlines_billion_1)

        csv = os.path.join(os.getcwd(),"delete.csv")
        h2o.download_csv(predictions,csv)

        airlines_billion_2 = h2o.import_file(csv)
        os.remove(csv)

        r1, c1 = airlines_billion_1.dim
        r2, c2 = airlines_billion_2.dim
        assert r1 == r2 and c1 == c2, "Expect rows to be equal. r1: {0} and r2: {1}. Expect cols to be equal c1: {0} " \
                                      "c2: {1}".format(r1,r2,c1,c2)
    else:
        print "Not running on H2O internal network.  No access to HDFS."

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_1431)
