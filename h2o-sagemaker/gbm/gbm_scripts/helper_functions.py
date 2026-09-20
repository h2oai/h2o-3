import socket
import time
import os
import json

prefix = '/opt/ml/'
config_prefix = 'input/config/'
input_prefix = 'input/data'
checkpoints_dir = 'opt/ml/checkpoints'


def _dns_lookup(host):
    counter = 0
    dns_resolved = False
    while dns_resolved is False:
        try:
            print(socket.gethostbyname(host))
            dns_resolved = True
        except Exception as e:
            time.sleep(10)
            counter += 1
            print("Waiting until DNS resolves: {}".format(counter))
            if counter > 10:
                raise Exception("Could not resolve DNS for Host: {}".format(host))


def _create_h2o_cluster(resource_params={}):

    with open("flatfile.txt", "w") as flatfile:
        for host in resource_params['hosts']:
            flatfile.write("{}:54321\n".format(host))
        for host in resource_params['hosts']:
            _dns_lookup(host)

    os.system("./startup_h2o_cluster.sh > h2o.log 2> h2o_error.log &")
    print("Starting up H2O-3")


def _get_parameters():
    param_path = os.path.join(prefix, config_prefix, 'hyperparameters.json')
    resource_path = os.path.join(prefix, config_prefix, 'resourceconfig.json')

    hyperparameters = {}
    if os.path.isfile(param_path):
        with open(param_path, 'r') as pf:
            hyperparameters = json.load(pf)

    print(param_path)
    print('All Parameters:')
    print(hyperparameters)

    resource_params = {}
    if os.path.isfile(resource_path):
        with open(resource_path, 'r') as rf:
            resource_params = json.load(rf)

    print(resource_path)
    print('All Resources:')
    print(resource_params)

    return hyperparameters, resource_params


def _parse_hyperparameters(hyperparameters_dict):
    training_params = hyperparameters_dict.pop("training")
    training_params = training_params.replace("'", '"')
    algo_params = {}

    # TODO replace with inspect module. getmembers
    algo_kwargs = ["balance_classes", "build_tree_one_node", "calibrate_model",
                   "calibration_frame", "categorical_encoding", "checkpoint",
                   "class_sampling_factors", "col_sample_rate",
                   "col_sample_rate_change_per_level",
                   "col_sample_rate_per_tree", "distribution",
                   "fold_assignment", "fold_column", "histogram_type",
                   "huber_alpha", "ignore_const_cols", "ignored_columns",
                   "keep_cross_validation_fold_assignment",
                   "keep_cross_validation_models",
                   "keep_cross_validation_predictions", "learn_rate",
                   "learn_rate_annealing", "max_abs_leafnode_pred",
                   "max_after_balance_size", "max_confusion_matrix_size",
                   "max_depth", "max_hit_ratio_k", "max_runtime_secs",
                   "min_rows", "min_split_improvement", "nbins", "nbins_cats",
                   "nbins_top_level", "nfolds", "ntrees", "offset_column",
                   "pred_noise_bandwidth", "quantile_alpha", "r2_stopping",
                   "response_column", "sample_rate", "sample_rate_per_class",
                   "score_each_iteration", "score_tree_interval", "seed",
                   "stopping_metric", "stopping_rounds", "stopping_tolerance",
                   "tweedie_power", "weights_column", "in_training_checkpoints_dir"]

    list_kwargs = ["class_sampling_factors", "ignored_columns",
                   "sample_rate_per_class"]

    int_kwargs = ["max_confusion_matrix_size", "max_depth", "max_hit_ratio_k",
                  "nbins", "nbins_cats", "nbins_top_level", "nfolds", "ntrees",
                  "seed", "stopping_rounds"]

    float_kwargs = ["col_sample_rate", "col_sample_rate_per_tree",
                    "huber_alpha", "learn_rate", "learn_rate_annealing",
                    "max_abs_leafnode_pred", "max_after_balance_size",
                    "max_runtime_secs", "min_rows", "min_split_improvement",
                    "pred_noise_bandwidth", "quantile_alpha", "r2_stopping",
                    "sample_rate", "tweedie_power"]

    bool_kwargs = ["balance_classes", "build_tree_one_node", "calibrate_model",
                   "ignore_const_cols"]

    for param in hyperparameters_dict.keys():
        if param in algo_kwargs:
            if param in list_kwargs:
                if hyperparameters_dict.get(param, "") != "":
                    algo_params[param] = hyperparameters_dict[param].split(",")
                else:
                    algo_params[param] = []
            elif param in int_kwargs:
                algo_params[param] = int(hyperparameters_dict[param])
            elif param in float_kwargs:
                algo_params[param] = float(hyperparameters_dict[param])
            elif param in bool_kwargs:
                if hyperparameters_dict[param] == "True" or hyperparameters_dict[param] == 'true':
                    algo_params[param] = True
                elif hyperparameters_dict[param] == "False" or hyperparameters_dict[param] == 'false':
                    algo_params[param] = False
            else:
                algo_params[param] = hyperparameters_dict[param]
        else:
            print("Ignoring Passed Parameter: {}. Not a kwarg for AutoML Algorithm".format(param))

    return training_params, algo_params
