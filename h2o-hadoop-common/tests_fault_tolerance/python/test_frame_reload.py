from __future__ import print_function
import sys
import os
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from tests import pyunit_utils
import fault_tolerance_utils as utils
import h2o
import unittest


class FrameReloadTest(unittest.TestCase):

    def test_frame_reload(self):
        name_node = pyunit_utils.hadoop_namenode()
        work_dir = "hdfs://%s%s" % (name_node, utils.get_workdir())
        dataset = "/datasets/mnist/train.csv.gz"
        
        saver_cluster_name = "saver-py" 
        try:
            cluster_1 = utils.start_cluster(saver_cluster_name)
            h2o.connect(url=cluster_1)
            df_orig = h2o.import_file(path="hdfs://%s%s" % (name_node, dataset))
            df_key = df_orig.key
            df_pd_orig = df_orig.as_data_frame()
            df_orig.save(work_dir)
            h2o.connection().close()
        finally:
            utils.stop_cluster(saver_cluster_name)
        
        loader_cluster_name = "loader-py"
        try:
            cluster_2 = utils.start_cluster(loader_cluster_name)
            h2o.connect(url=cluster_2)
            df_loaded = h2o.load_frame(df_key, work_dir)
            df_pd_loaded = df_loaded.as_data_frame()
            h2o.connection().close()
        finally:
            utils.stop_cluster(loader_cluster_name)
        
        self.assertTrue(df_pd_orig.equals(df_pd_loaded))


if __name__ == '__main__':
    unittest.main()
