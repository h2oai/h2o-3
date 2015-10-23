import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
#----------------------------------------------------------------------
# Purpose:  This tests convergence of k-means on a large dataset.
#----------------------------------------------------------------------






def hdfs_kmeans_converge():
    

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()
        hdfs_cross_file = "/datasets/runit/BigCross.data"

        print "Import BigCross.data from HDFS"
        url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_cross_file)
        cross_h2o = h2o.import_file(url)
        n = cross_h2o.nrow
        print "rows: {0}".format(n)
        ncent = 3
        miters = 10

        print "Run k-means with k = {0} and max_iterations = {1}".format(ncent,miters)
        cross1_km = h2o.kmeans(training_frame = cross_h2o, x=cross_h2o[0:57], k = ncent, max_iterations = miters)
        print cross1_km

        print "Run k-means with init = final cluster centers and max_iterations = 1"
        init_centers = h2o.H2OFrame(cross1_km.centers())
        cross2_km = h2o.kmeans(training_frame = cross_h2o, x=cross_h2o[0:57], k = ncent, user_points=init_centers,
                               max_iterations = 1)
        print cross2_km

        print "Check k-means converged or maximum iterations reached"
        c1 = h2o.H2OFrame(cross1_km.centers())
        c2 = h2o.H2OFrame(cross2_km.centers())
        avg_change = ((c1-c2)**2).sum() / ncent
        iters = cross1_km._model_json['output']['model_summary'].cell_values[0][3]
        assert avg_change < 1e-6 or iters > miters, "Expected k-means to converge or reach max iterations. avg_change = " \
                                                    "{0} and iterations = {1}".format(avg_change, iters)
    else:
        raise(EnvironmentError, "Not running on H2O internal network.  No access to HDFS.")



if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_kmeans_converge)
else:
    hdfs_kmeans_converge()
