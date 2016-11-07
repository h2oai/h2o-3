from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

#----------------------------------------------------------------------
# Purpose:  This test is to run GLRM on airline data and measure
# how fast it can run with the various optimization methods that we
# are looking at.
#----------------------------------------------------------------------


def hdfs_orc_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        numElements2Compare = 10
        tol_time = 200
        tol_numeric = 1e-5

        hdfs_name_node = pyunit_utils.hadoop_namenode()
        hdfs_csv_file = "/datasets/air_csv_part"

        col_types = ['real', 'real', 'real', 'real', 'real', 'real', 'real', 'real', 'enum', 'real', 'enum', 'real',
                         'real', 'enum', 'real', 'real', 'enum', 'enum', 'real', 'enum', 'enum', 'real', 'real', 'real',
                         'enum', 'enum', 'enum', 'enum', 'enum', 'enum', 'enum']

            # import CSV file
        print("Import airlines 116M dataset in original csv format from HDFS")
        url_csv = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file)
        acs_orig = h2o.import_file(url_csv, na_strings=['\\N'], col_types=col_types)
        print("Data size number of rows: {0}, number of columns: {1}".format(acs_orig.nrow, acs_orig.ncol))

        seeds = [2297378124, 3849570216, 6733652048, 8915337442, 8344418400, 9416580152, 2598632624, 4977008454, 8273228579,
            8185554539, 3219125000, 2998879373, 7707012513, 5786923379, 5029788935, 935945790, 7092607078, 9305834745,
            6173975590, 5397294255]
        run_time_ms = []
        iterations = []
        objective = []
        num_runs = 1         # number of times to repeat experiments

        for ind in range(num_runs):
            acs_model = H2OGeneralizedLowRankEstimator(k = 10,
                                               transform = 'STANDARDIZE',
                                               loss = 'Quadratic',
                                               multi_loss="Categorical",
                                               model_id="clients_core_glrm",
                                               regularization_x="L2",
                                               regularization_y="L1",
                                               gamma_x=0.2,
                                               gamma_y=0.5,
                                               init="SVD",
                                               max_iterations = 200,
                                               seed=seeds[ind % len(seeds)])
            acs_model.train(x = acs_orig.names, training_frame= acs_orig, seed=seeds[ind % len(seeds)])
            run_time_ms.append(acs_model._model_json['output']['end_time'] - acs_model._model_json['output']['start_time'])
            iterations.append(acs_model._model_json['output']['iterations'])
            objective.append(acs_model._model_json['output']['objective'])

        print("Run time in ms: {0}".format(run_time_ms))
        print("number of iterations: {0}".format(iterations))
        print("objective function value: {0}".format(objective))
        sys.stdout.flush()
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_orc_parser)
else:
    hdfs_orc_parser()