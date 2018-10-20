# TODO: ugh:
import sys, pprint, os

sys.path.insert(1, '..')
sys.path.insert(1, '.')
sys.path.insert(1, os.path.join("..", "py"))

import h2o
import h2o_test_utils
from h2o_test_utils import ModelSpec
import os
import argparse
import time
import json
import requests

sys.path.insert(1, '../rest_tests')

#################
# Config
#################

clean_up_after = False

algos = ['coxph', 'kmeans', 'deeplearning', 'deepwater', 'drf', 'glm', 'gbm', 'pca', 'naivebayes', 'glrm', 'svd', 'aggregator', 'word2vec', 'stackedensemble', 'xgboost', 'isolationforest']
algo_additional_default_params = { 'grep' : { 'regex' : '.*' },
                                   'kmeans' : { 'k' : 2 }
                                 } # additional params to add to the default params

#################
# setup
#################

parser = argparse.ArgumentParser(
    description='Run basic H2O REST API tests.',
)

parser.add_argument('--verbose', '-v', help='verbose output', action='count')
parser.add_argument('--usecloud', help='ip:port to attach to', default='')
parser.add_argument('--host', help='hostname to attach to', default='localhost')
parser.add_argument('--port', help='port to attach to', type=int, default=54321)
args = parser.parse_args()

h2o_test_utils.setVerbosity(args.verbose)
h2o.H2O.verbose = h2o_test_utils.isVerboser()

if (len(args.usecloud) > 0):
    arr = args.usecloud.split(":")
    args.host = arr[0]
    args.port = int(arr[1])

host = args.host
port = args.port

h2o.H2O.verboseprint("host: " + str(host))
h2o.H2O.verboseprint("port" + str(port))

pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

################
# The test body:
################

a_node = h2o.H2O(host, port)
h2o.H2O.verboseprint("connected to: ", str(host), ':', str(port))


import test_metadata
test_metadata.test(a_node, pp)

import test_html
test_html.test(a_node, pp)

import test_cluster_sanity
test_cluster_sanity.test(a_node, pp, algos)

# Clean up old objects from the DKV, in case the cluster has been doing other things:
if h2o_test_utils.isVerbose(): print('Cleaning up old stuff. . .')
h2o_test_utils.cleanup(a_node)

import test_and_import_frames
datasets = test_and_import_frames.load_and_test(a_node, pp)

import test_models
test_models.build_and_test(a_node, pp, datasets, algos, algo_additional_default_params)

# Metadata used to get corrupted, so test again
test_metadata.test(a_node, pp)

import test_predict_and_model_metrics
test_predict_and_model_metrics.test(a_node, pp)

import test_final_sanity
test_final_sanity.test(a_node, pp)



# TODO: use built_models
if clean_up_after:
    h2o_test_utils.cleanup(models=[dl_airlines_model_name, 'deeplearning_prostate_binomial', 'kmeans_prostate'], frames=['prostate_binomial', 'airlines_binomial'])


