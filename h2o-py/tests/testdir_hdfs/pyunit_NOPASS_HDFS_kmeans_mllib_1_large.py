#----------------------------------------------------------------------
# Purpose:  This test compares k-means centers between H2O and MLlib.
#----------------------------------------------------------------------

import sys
sys.path.insert(1, "../../")
import h2o, tests
import numpy as np

def kmeans_mllib(ip, port):
    

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    running_inside_h2o = h2o.is_running_internal_to_h2o()

    if running_inside_h2o:
        hdfs_name_node = h2o.get_h2o_internal_hdfs_name_node()
        hdfs_cross_file = "/datasets/runit/BigCross.data"

        print "Import BigCross.data from HDFS"
        url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_cross_file)
        cross_h2o = h2o.import_file(url)
        n = cross_h2o.nrow

        err_mllib = np.genfromtxt(h2o.locate("smalldata/mllib_bench/bigcross_wcsse.csv"), delimiter=",", skip_header=1)
        ncent = [int(err_mllib[r][0]) for r in range(len(err_mllib))]

        for k in ncent:
            print "Run k-means++ with k = {0} and max_iterations = 10".format(k)
            cross_km = h2o.kmeans(training_frame = cross_h2o, x = cross_h2o, k = k, init = "PlusPlus",
                                  max_iterations = 10, standardize = False)

            clust_mllib = np.genfromtxt(h2o.locate("smalldata/mllib_bench/bigcross_centers_" + str(k) + ".csv"),
                                        delimiter=",").tolist()
            clust_h2o = cross_km.centers()

            # Sort in ascending order by first dimension for comparison purposes
            clust_mllib.sort(key=lambda x: x[0])
            clust_h2o.sort(key=lambda x: x[0])

            print "\nMLlib Cluster Centers:\n"
            print clust_mllib
            print "\nH2O Cluster Centers:\n"
            print clust_h2o

            wcsse_mllib = err_mllib[err_mllib[0:4,0].tolist().index(k)][1]
            wcsse_h2o = cross_km.tot_withinss() / n
            print "\nMLlib Average Within-Cluster SSE: \n".format(wcsse_mllib)
            print "H2O Average Within-Cluster SSE: \n".format(wcsse_h2o)
            assert wcsse_h2o == wcsse_mllib, "Expected mllib and h2o to get the same wcsse. Mllib got {0}, and H2O " \
                                             "got {1}".format(wcsse_mllib, wcsse_h2o)

if __name__ == "__main__":
    tests.run_test(sys.argv, kmeans_mllib)
