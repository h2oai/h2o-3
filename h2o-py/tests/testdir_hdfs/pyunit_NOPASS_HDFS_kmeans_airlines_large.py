#----------------------------------------------------------------------
# Purpose:  This test runs k-means on the full airlines dataset.
#----------------------------------------------------------------------

import sys
sys.path.insert(1, "../../")
import h2o

def hdfs_kmeans_airlines(ip, port):
    

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    running_inside_h2o = h2o.is_running_internal_to_h2o()

    if running_inside_h2o:
        hdfs_name_node = h2o.get_h2o_internal_hdfs_name_node()
        hdfs_file = "/datasets/airlines_all.csv"

        print "Import airlines_all.csv from HDFS"
        url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_file)
        airlines_h2o = h2o.import_file(url)
        n = airlines_h2o.nrow()
        print "rows: {0}".format(n)

        print "Run k-means++ with k = 7 and max_iterations = 10"
        myX = range(8) + range(11,16) + range(18,21) + range(24,29) + [9]
        airlines_km = h2o.kmeans(training_frame = airlines_h2o, x = airlines_h2o[myX], k = 7, init = "Furthest",
                                 max_iterations = 10, standardize = True)
        print airlines_km
    else:
        print "Not running on H2O internal network.  No access to HDFS."

if __name__ == "__main__":
    h2o.run_test(sys.argv, hdfs_kmeans_airlines)
