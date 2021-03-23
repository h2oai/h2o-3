from __future__ import print_function
import sys
import os
import time
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from tests import pyunit_utils
import fault_tolerance_utils as utils
import h2o
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.exceptions import H2OResponseError
import unittest
import threading


class GridAutoRecoveryTest(unittest.TestCase):

    def _training_thread(self, grid, train):
        self.training_error = None
        try:
            print("starting initial grid...")
            grid.train(x=list(range(4)), y=4, training_frame=train)
        except Exception as e:
            print("Error while training")
            print(e)
            self.training_error = e

    def _wait_for_model_to_build(self, grid_id, model_count=1):
        grid_in_progress = None
        times_waited = 0
        while (times_waited < 20) and (grid_in_progress is None or len(grid_in_progress.model_ids) < model_count):
            time.sleep(5)  # give it tome to train some models
            times_waited += 1
            try:
                grid_in_progress = h2o.get_grid(grid_id)
            except IndexError:
                print("no models trained yet")
            except H2OResponseError as e:
                print("grid not started yet " + e.args[0])
        print("done sleeping")
        return grid_in_progress.model_ids

    def _print_models(self, heading, models):
        print("%s\n-------------\n" % heading)
        for m in models:
            print(m)
    
    def _check_training_error(self):
        assert self.training_error is None

    def test_auto_recovery(self):
        name_node = pyunit_utils.hadoop_namenode()
        dataset = "/datasets/iris_wheader.csv"

        ntrees_opts = [100, 120, 130, 140]
        learn_rate_opts = [0.01, 0.02, 0.03, 0.04]
        grid_size = len(ntrees_opts) * len(learn_rate_opts)
        print("max models %s" % grid_size)
        grid_id = "grid_ft_auto_recover"
        hyper_parameters = {
            "learn_rate": learn_rate_opts,
            "ntrees": ntrees_opts
        }

        cluster_1_name = "grid-auto-1-py"
        try:
            cluster_1 = utils.start_cluster(cluster_1_name, enable_auto_recovery=True, clean_auto_recovery=True)
            print("initial cluster started at %s" % cluster_1)
            h2o.connect(url=cluster_1)
            train = h2o.import_file(path="hdfs://%s%s" % (name_node, dataset))
            grid = H2OGridSearch(
                H2OGradientBoostingEstimator,
                grid_id=grid_id,
                hyper_params=hyper_parameters
            )
            bg_train_thread = threading.Thread(
                target=self._training_thread,
                kwargs={"grid": grid, "train": train}
            )
            bg_train_thread.start()
            phase_1_models = self._wait_for_model_to_build(grid_id)
            self._print_models("Initial models", phase_1_models)
            assert len(phase_1_models) > 0
            self._check_training_error()
        finally:
            utils.stop_cluster(cluster_1_name)

        cluster_2_name = "grid-auto-2-py"
        try:
            cluster_2 = utils.start_cluster(cluster_2_name, enable_auto_recovery=True)
            print("cluster resumed at %s, should unblock background thread" % cluster_2)
            phase_2_models = self._wait_for_model_to_build(grid_id, len(phase_1_models) + 1)
            self._print_models("Recovery #1 models", phase_2_models)
            assert len(phase_2_models) > len(phase_1_models)
            self._check_training_error()
        finally:
            utils.stop_cluster(cluster_2_name)

        cluster_3_name = "grid-auto-3-py"
        try:
            cluster_3 = utils.start_cluster(cluster_3_name, enable_auto_recovery=True)
            print("cluster resumed at %s, waiting for training to finish" % cluster_3)
            bg_train_thread.join()
            print("models after final run:")
            for x in sorted(grid.model_ids):
                print(x)
            print("Finished grained grid has %d models" % len(grid.model_ids))
            self.assertEqual(len(grid.model_ids), grid_size, "The full grid was not trained.")
            self._check_training_error()
            h2o.connection().close()
        finally:
            utils.stop_cluster(cluster_3_name)


if __name__ == '__main__':
    unittest.main()
