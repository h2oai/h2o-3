from __future__ import print_function

import sys
import random
import os
import numpy as np
import math
from builtins import range
import time
import json

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils

class Generate_kmeans_clusters:
    """
    PUBDEV-1843: Grid testing.  Subtask 2 for kmeans

    This class is used to generate kmeans clusters to test the kmeans algo with gridsearch.  It may run rather
    slowly.  Since this is used to generate training data for kmeans, it does not need to be run unless we
    need new datasets.
    """

    # parameters set by users, change with care
    cluster_centers = [[0, 0, 0], [0, 100, 0], [100, 100, 0], [100, 0, 0], [100, 0, 100], [0, 0, 100],
                       [0, 100, 100], [100, 100, 100]]      # list of cluster centers
    cluster_radius = [10, 10, 10, 10, 10, 10, 10, 10]           # cluster size in radius
    cluster_numbers = [125, 125, 125, 125, 125, 125, 125, 125]  # number of points within each cluster

    curr_time = str(round(time.time()))     # store current timestamp, used as part of filenames.
    seed = round(time.time())

    # filenames where we store our info.
    training1_filename = "kmeans_8_centers_3_coords.csv"

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    test_name = "pyunit_NOPASS_generate_kmeans_clusters.py"     # name of this test

    training_data_file = os.path.join(current_dir, training1_filename)

    def __init__(self):
        self.setup_data()

    def setup_data(self):
        """
        This function generates the kmeans cluster dataset and save it as a csv file.
        """
        dataset = pyunit_utils.generate_clusters(self.cluster_centers, self.cluster_numbers, self.cluster_radius)
        np.savetxt(self.training_data_file, dataset, delimiter=",")


def test_generate_kmeans_cluster():
    """
    Create and instantiate class that generates clusters.

    :return: None
    """
    test_PCA_grid = Generate_kmeans_clusters()

    sys.stdout.flush()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_generate_kmeans_cluster)
else:
    test_generate_kmeans_cluster()
