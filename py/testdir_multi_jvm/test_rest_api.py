# TODO: ugh:
import sys, pprint, os

sys.path.insert(1, '..')
sys.path.insert(1, '.')
sys.path.insert(1, os.path.join("..", "py"))

import h2o
import h2o_test_utils
from h2o_test_utils import DatasetSpec
from h2o_test_utils import ModelSpec
import os
import argparse
import time
import json
import requests

#################
# Config is below
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

#########
# Config:
algos = ['kmeans', 'deeplearning', 'drf', 'glm', 'gbm', 'pca', 'naivebayes', 'glrm', 'svd']
algo_additional_default_params = { 'grep' : { 'regex' : '.*' },
                                   'kmeans' : { 'k' : 2 }
                                 } # additional params to add to the default params
clean_up_after = False

h2o.H2O.verboseprint("connected to: ", str(host), ':', str(port))

models = a_node.models()
if h2o_test_utils.isVerboser():
    print 'Models: '
    pp.pprint(models)

models = a_node.models(api_version=92)
if h2o_test_utils.isVerboser():
    print 'ModelsV92: '
    pp.pprint(models)

frames = a_node.frames(row_count=5)
if h2o_test_utils.isVerboser():
    print 'Frames: '
    pp.pprint(frames)

####################################
# test schemas collection GET
if h2o_test_utils.isVerbose(): print 'Testing /Metadata/schemas. . .'
schemas = a_node.schemas(timeoutSecs=240)
assert 'schemas' in schemas, "FAIL: failed to find 'schemas' field in output of /Metadata/schemas: " + repr(schemas)
assert type(schemas['schemas']) is list, "'schemas' field in output of /Metadata/schemas is not a list: " + repr(schemas)
assert len(schemas['schemas']) > 0, "'schemas' field in output of /Metadata/schemas is empty: " + repr(schemas)

if h2o_test_utils.isVerboser():
    print 'Schemas: '
    pp.pprint(schemas)


####################################
# test schemas individual GET
if h2o_test_utils.isVerbose(): print 'Testing /Metadata/schemas/FrameV3. . .'
schemas = a_node.schema(schemaname='FrameV3', timeoutSecs=240)
assert 'schemas' in schemas, "FAIL: failed to find 'schemas' field in output of /Metadata/schemas/FrameV3: " + repr(schemas)
assert type(schemas['schemas']) is list, "'schemas' field in output of /Metadata/schemas/FrameV3 is not a list: " + repr(schemas)
assert len(schemas['schemas']) == 1, "'schemas' field in output of /Metadata/schemas/FrameV3 has an unexpected length: " + repr(schemas)

if h2o_test_utils.isVerboser():
    print 'Schemas: '
    pp.pprint(schemas)


####################################
# test HTML pages GET
url_prefix = 'http://' + host + ':' + str(port)

urls = {
    '': 'Analytics',
    '/': 'Analytics',
    '/index.html': 'Analytics',
    '/flow/index.html': 'modal',
    '/LATEST/Cloud.html': 'Ready',
}

for (suffix, expected_word) in urls.iteritems():
    url = url_prefix + suffix
    h2o.H2O.verboseprint('Testing ' + url + '. . .')
    r = requests.get(url)
    assert r.text.find(expected_word), "FAIL: didn't find '" + expected_word + "' in: " + url


####################################
# test model_builders collection GET
if h2o_test_utils.isVerbose(): print 'Testing /ModelBuilders. . .'
model_builders = a_node.model_builders(timeoutSecs=240)

if h2o_test_utils.isVerboser():
    print 'ModelBuilders: '
    pp.pprint(model_builders)

for algo in algos:
    assert algo in model_builders['model_builders'], "FAIL: Failed to find algo: " + algo
    builder = model_builders['model_builders'][algo]
    h2o_test_utils.validate_builder(algo, builder)


####################################
# test model_builders individual GET
if h2o_test_utils.isVerbose(): print 'Testing /ModelBuilders/{algo}. . .'
for algo in algos:
    model_builder = a_node.model_builders(algo=algo, timeoutSecs=240)
    assert algo in model_builder['model_builders'], "FAIL: Failed to find algo: " + algo
    builder = model_builders['model_builders'][algo]
    h2o_test_utils.validate_builder(algo, builder)

####################################
# test model_metrics collection GET
if h2o_test_utils.isVerbose(): print 'Testing /ModelMetrics. . .'
model_metrics = a_node.model_metrics(timeoutSecs=240)

if h2o_test_utils.isVerboser():
    print 'ModelMetrics: '
    pp.pprint(model_metrics)

####################################
# test model_metrics individual GET
# TODO

# Clean up frames
if h2o_test_utils.isVerbose(): print 'Cleaning up old stuff. . .'
h2o_test_utils.cleanup(a_node)


#########################
# test Metadata/endpoints
if h2o_test_utils.isVerbose(): print 'Testing /Metadata/endpoints. . .'
endpoints = a_node.endpoints()
assert 'routes' in endpoints, "FAIL: failed to find routes in the endpoints result."
assert type(endpoints['routes']) is list, "FAIL: routes in the endpoints result is not a list."
assert len(endpoints['routes']) > 0, "FAIL: routes list in the endpoints result is empty."
assert type(endpoints['routes'][0]) is dict, "FAIL: routes[0] in the endpoints result is not a dict."
assert 'input_schema' in endpoints['routes'][0], "FAIL: routes[0] in the endpoints result does not have an 'input_schema' field."


#########################
# test Metadata/schemas
if h2o_test_utils.isVerbose(): print 'Testing /Metadata/schemas. . .'
schemas = a_node.schemas()
assert 'schemas' in schemas, "FAIL: failed to find schemas in the schemas result."
assert type(schemas['schemas']) is list, "FAIL: schemas in the schemas result is not a list."
assert len(schemas['schemas']) > 0, "FAIL: schemas list in the schemas result is empty."
assert type(schemas['schemas'][0]) is dict, "FAIL: schemas[0] in the schemas result is not a dict."
assert 'fields' in schemas['schemas'][0], "FAIL: schemas[0] in the schemas result does not have an 'fields' field."


####################################################################################################
# Import and check datasets
####################################################################################################

##################
# Test CreateFrame
if h2o_test_utils.isVerbose(): print 'Testing CreateFrame. . .'
created_job = a_node.create_frame(dest='created') # call with defaults

a_node.poll_job(job_key=created_job['key']['name']) # wait until done and get CreateFrameV3 instance (aka the Job)

frames = a_node.frames(key='created')['frames']
assert len(frames) == 1, "FAIL: expected to find 1 frame called 'created', found: " + str(len(frames))
assert frames[0]['frame_id']['name'] == 'created', "FAIL: expected to find 1 frame called 'created', found: " + repr(frames)

created = frames[0]
assert 'rows' in created, "FAIL: failed to find 'rows' field in CreateFrame result."
assert created['rows'] == 10000, "FAIL: expected value of 'rows' field in CreateFrame result to be: " + str(10000) + ", found: " + str(created['rows'])
assert 'columns' in created, "FAIL: failed to find 'columns' field in CreateFrame result."
assert len(created['columns']) == 10, "FAIL: expected value of 'columns' field in CreateFrame result to be: " + str(10) + ", found: " + str(len(created['columns']))

#########################################################
# Import all the datasets we'll need for the teste below:
#########################################################

# dest_key, path, expected_rows, model_category, response_column, ignored_columns
datasets_to_import = [
    DatasetSpec('prostate_clustering', '../../smalldata/logreg/prostate.csv', 380, 'Clustering', None, ['ID']),
    DatasetSpec('prostate_binomial', '../../smalldata/logreg/prostate.csv', 380, 'Binomial', 'CAPSULE', ['ID']),
    DatasetSpec('prostate_regression', '../../smalldata/logreg/prostate.csv', 380, 'Regression', 'AGE', ['ID']),

    DatasetSpec('airlines_binomial', '../../smalldata/airlines/allyears2k_headers.zip', 43978, 'Binomial', 'IsDepDelayed', ['IsArrDelayed', 'ArrDelay', 'DepDelay']), # TODO: more ignored?

    DatasetSpec('iris_multinomial', '../../smalldata/iris/iris_wheader.csv', 150, 'Multinomial', 'class', []),
]

datasets = {} # the dataset spec
for dataset_spec in datasets_to_import:
    dataset = dataset_spec.import_and_validate_dataset(a_node) # it's also stored in dataset_spec['dataset']
    datasets[dataset_spec['dest_key']] = dataset_spec


################################################
# Test /Frames for prostate.csv
frames = a_node.frames(row_count=5)['frames']
frames_dict = h2o_test_utils.list_to_dict(frames, 'frame_id/name')

# TODO: remove:
if h2o_test_utils.isVerboser():
    print "frames: "
    pp.pprint(frames)

if h2o_test_utils.isVerboser():
    print "frames_dict: "
    pp.pprint(frames_dict)

assert 'prostate_binomial' in frames_dict, "FAIL: Failed to find " + 'prostate_binomial' + " in Frames list."
assert not frames_dict['prostate_binomial']['is_text'], "FAIL: Parsed Frame is is_text"


# Test /Frames/{key} for prostate.csv
frames = a_node.frames(key='prostate_binomial', row_count=5)['frames']
frames_dict = h2o_test_utils.list_to_dict(frames, 'frame_id/name')
assert 'prostate_binomial' in frames_dict, "FAIL: Failed to find prostate.hex in Frames list."
columns_dict = h2o_test_utils.list_to_dict(frames[0]['columns'], 'label')
assert 'CAPSULE' in columns_dict, "FAIL: Failed to find CAPSULE in Frames/prostate.hex."
assert 'AGE' in columns_dict, "FAIL: Failed to find AGE in Frames/prostate.hex/columns."
assert 'histogram_bins' in columns_dict['AGE'], "FAIL: Failed to find bins in Frames/prostate.hex/columns/AGE."
h2o.H2O.verboseprint('bins: ' + repr(columns_dict['AGE']['histogram_bins']))
assert None is columns_dict['AGE']['histogram_bins'], "FAIL: Failed to clear bins field." # should be cleared except for /summary


# Test /Frames/{key}/columns for prostate.csv
frames = a_node.columns(key='prostate_binomial')['frames']
columns_dict = h2o_test_utils.list_to_dict(frames[0]['columns'], 'label')
assert 'ID' in columns_dict, "FAIL: Failed to find ID in Frames/prostate.hex/columns."
assert 'AGE' in columns_dict, "FAIL: Failed to find AGE in Frames/prostate.hex/columns."
assert 'histogram_bins' in columns_dict['AGE'], "FAIL: Failed to find bins in Frames/prostate.hex/columns/AGE."
h2o.H2O.verboseprint('bins: ' + repr(columns_dict['AGE']['histogram_bins']))
assert None is columns_dict['AGE']['histogram_bins'], "FAIL: Failed to clear bins field." # should be cleared except for /summary

# Test /Frames/{key}/columns/{label} for prostate.csv
frames = a_node.column(key='prostate_binomial', column='AGE')['frames']
columns_dict = h2o_test_utils.list_to_dict(frames[0]['columns'], 'label')
assert 'AGE' in columns_dict, "FAIL: Failed to find AGE in Frames/prostate.hex/columns."
assert 'histogram_bins' in columns_dict['AGE'], "FAIL: Failed to find bins in Frames/prostate.hex/columns/AGE."
h2o.H2O.verboseprint('bins: ' + repr(columns_dict['AGE']['histogram_bins']))
assert None is columns_dict['AGE']['histogram_bins'], "FAIL: Failed to clear bins field." # should be cleared except for /summary

# Test /Frames/{key}/columns/{label}/summary for prostate.csv
frames = a_node.summary(key='prostate_binomial', column='AGE')['frames']
columns_dict = h2o_test_utils.list_to_dict(frames[0]['columns'], 'label')
assert 'AGE' in columns_dict, "FAIL: Failed to find AGE in Frames/prostate.hex/columns/AGE/summary."
col = columns_dict['AGE']
h2o_test_utils.assertKeysExistAndNonNull(col, '', ['label', 'missing_count', 'zero_count', 'positive_infinity_count', 'negative_infinity_count', 'mins', 'maxs', 'mean', 'sigma', 'type', 'data', 'precision', 'histogram_bins', 'histogram_base', 'histogram_stride', 'percentiles'])
h2o_test_utils.assertKeysExist(col, '', ['domain', 'string_data'])
assert col['mins'][0] == 43, 'FAIL: Failed to find 43 as the first min for AGE.'
assert col['maxs'][0] == 79, 'FAIL: Failed to find 79 as the first max for AGE.'
assert abs(col['mean'] - 66.03947368421052) < 1e-8, 'FAIL: Failed to find 66.03947368421052 as the mean for AGE.'
assert abs(col['sigma'] - 6.527071269173308) < 1e-8, 'FAIL: Failed to find 6.527071269173308 as the sigma for AGE.'
assert col['type'] == 'int', 'FAIL: Failed to find int as the type for AGE.'
assert col['data'][0] == 65, 'FAIL: Failed to find 65 as the first data for AGE.'
assert col['precision'] == -1, 'FAIL: Failed to find -1 as the precision for AGE.'
assert col['histogram_bins'][0] == 1, 'FAIL: Failed to find 1 as the first bin for AGE.'
assert col['histogram_base'] == 43, 'FAIL: Failed to find 43 as the histogram_base for AGE.'
assert col['histogram_stride'] == 1, 'FAIL: Failed to find 1 as the histogram_stride for AGE.'
assert col['percentiles'][0] == 44.516, 'FAIL: Failed to find 43.516 as the 0.1% percentile for AGE. '+str(col['percentiles'][0])
assert col['percentiles'][1] == 50.79, 'FAIL: Failed to find 50.79 as the 1.0% percentile for AGE. '+str(col['percentiles'][1])
assert col['percentiles'][9] == 78, 'FAIL: Failed to find 78 as the 99.0% percentile for AGE. '+str(col['percentiles'][9])
assert col['percentiles'][10] == 79, 'FAIL: Failed to find 79 as the 99.9% percentile for AGE. '+str(col['percentiles'][10])
# NB: col['percentiles'] corresponds to probs=[0.001, 0.01, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.99, 0.999]

# Test /SplitFrame for prostate.csv
if h2o_test_utils.isVerbose(): print 'Testing SplitFrame with named destination_frames. . .'
splits = a_node.split_frame(dataset='prostate_binomial', ratios=[0.8], destination_frames=['bigger', 'smaller'])
frames = a_node.frames()['frames']
h2o_test_utils.validate_frame_exists(a_node, 'bigger', frames)
h2o_test_utils.validate_frame_exists(a_node, 'smaller', frames)
bigger = a_node.frames(key='bigger')['frames'][0]
smaller = a_node.frames(key='smaller')['frames'][0]
assert bigger['rows'] == 304, 'FAIL: 80/20 SplitFrame yielded the wrong number of rows.  Expected: 304; got: ' + bigger['rows']
assert smaller['rows'] == 76, 'FAIL: 80/20 SplitFrame yielded the wrong number of rows.  Expected: 76; got: ' + smaller['rows']
# TODO: h2o_test_utils.validate_job_exists(a_node, splits['frame_id']['name'])

if h2o_test_utils.isVerbose(): print 'Testing SplitFrame with generated destination_frames. . .'
splits = a_node.split_frame(dataset='prostate_binomial', ratios=[0.5])
frames = a_node.frames()['frames']
h2o_test_utils.validate_frame_exists(a_node, splits['destination_frames'][0]['name'], frames)
h2o_test_utils.validate_frame_exists(a_node, splits['destination_frames'][1]['name'], frames)

first = a_node.frames(key=splits['destination_frames'][0]['name'])['frames'][0]
second = a_node.frames(key=splits['destination_frames'][1]['name'])['frames'][0]
assert first['rows'] == 190, 'FAIL: 50/50 SplitFrame yielded the wrong number of rows.  Expected: 190; got: ' + first['rows']
assert second['rows'] == 190, 'FAIL: 50/50 SplitFrame yielded the wrong number of rows.  Expected: 190; got: ' + second['rows']
# TODO: h2o_test_utils.validate_job_exists(a_node, splits['frame_id']['name'])

####################################################################################################
# Build and do basic validation checks on models
####################################################################################################
models_to_build = [
    ModelSpec.for_dataset('kmeans_prostate', 'kmeans', datasets['prostate_clustering'], { 'k': 2 } ),

    ModelSpec.for_dataset('glm_prostate_regression', 'glm', datasets['prostate_regression'], {'family': 'gaussian'} ),

    ModelSpec.for_dataset('glm_prostate_binomial', 'glm', datasets['prostate_binomial'], {'family': 'binomial'} ),
    # TODO: Crashes: ModelSpec('glm_airlines_binomial', 'glm', 'airlines_binomial', {'response_column': 'IsDepDelayed', 'do_classification': True, 'family': 'binomial'}, 'Binomial'),
    # Multinomial doesn't make sense for glm: ModelSpec('glm_iris_multinomial', 'glm', iris_multinomial, {'response_column': 'class', 'do_classification': True, 'family': 'gaussian'}, 'Regression'),

    ModelSpec.for_dataset('deeplearning_prostate_regression', 'deeplearning', datasets['prostate_regression'], { 'epochs': 1, 'loss': 'Quadratic' } ),
# TODO: add toEnum of the response column and put back:    ModelSpec.for_dataset('deeplearning_prostate_binomial', 'deeplearning', datasets['prostate_binomial'], { 'epochs': 1, 'hidden': [20, 20], 'loss': 'CrossEntropy' } ),
    ModelSpec.for_dataset('deeplearning_airlines_binomial', 'deeplearning', datasets['airlines_binomial'], { 'epochs': 1, 'hidden': [10, 10], 'loss': 'CrossEntropy' } ),
    ModelSpec.for_dataset('deeplearning_iris_multinomial', 'deeplearning', datasets['iris_multinomial'], { 'epochs': 1, 'loss': 'CrossEntropy' } ),

    ModelSpec.for_dataset('gbm_prostate_regression', 'gbm', datasets['prostate_regression'], { 'ntrees': 5, 'distribution': 'gaussian' } ),
# TODO: add toEnum of the response column and put back:        ModelSpec.for_dataset('gbm_prostate_binomial', 'gbm', datasets['prostate_binomial'], { 'ntrees': 5, 'distribution': 'multinomial' } ),
    ModelSpec.for_dataset('gbm_airlines_binomial', 'gbm', datasets['airlines_binomial'], { 'ntrees': 5, 'distribution': 'multinomial' } ),
    ModelSpec.for_dataset('gbm_iris_multinomial', 'gbm', datasets['iris_multinomial'], { 'ntrees': 5, 'distribution': 'multinomial' } ),
]

built_models = {}
for model_spec in models_to_build:
    model = model_spec.build_and_validate_model(a_node)
    built_models[model_spec['dest_key']] = model


#######################################
# Test default parameters validation for each model builder
#
if h2o_test_utils.isVerbose(): print 'Testing ModelBuilder default parameters. . .'
model_builders = a_node.model_builders(timeoutSecs=240)['model_builders']

# Do we know about all of them?
server_algos = model_builders.keys()
assert len(set(server_algos) - set(algos)) == 0, "FAIL: Our set of algos doesn't match what the server knows about.  Ours: " + repr(algos) + "; server's: " + repr(server_algos)

for algo, model_builder in model_builders.iteritems():
    parameters_list = model_builder['parameters']
    test_parameters = { value['name'] : value['default_value'] for value in parameters_list } # collect default parameters
    if algo in algo_additional_default_params:
        test_parameters.update(algo_additional_default_params[algo])

    parameters_validation = a_node.validate_model_parameters(algo=algo, training_frame=None, parameters=test_parameters, timeoutSecs=240) # synchronous
    assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in good-parameters parameters validation result."
    h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))

    expected_count = 0
    if expected_count != parameters_validation['error_count']:
        print "validation errors: "
        pp.pprint(parameters_validation)
    assert expected_count == parameters_validation['error_count'], "FAIL: " + str(expected_count) + " != error_count in good-parameters parameters validation result."


#######################################
# Test DeepLearning parameters validation
#
# Default parameters:
model_builder = a_node.model_builders(algo='deeplearning', timeoutSecs=240)['model_builders']['deeplearning']
dl_test_parameters_list = model_builder['parameters']
dl_test_parameters = {value['name'] : value['default_value'] for value in dl_test_parameters_list}

parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in good-parameters parameters validation result."
h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))
if 0 != parameters_validation['error_count']:
    print "validation errors: "
    pp.pprint(parameters_validation)
assert 0 == parameters_validation['error_count'], "FAIL: 0 != error_count in good-parameters parameters validation result."

# Good parameters (note: testing with null training_frame):
dl_test_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]" }
parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in good-parameters parameters validation result."
h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))
if 0 != parameters_validation['error_count']:
    print "validation errors: "
    pp.pprint(parameters_validation)
assert 0 == parameters_validation['error_count'], "FAIL: 0 != error_count in good-parameters parameters validation result."

# Bad parameters (hidden is null):
# (note: testing with null training_frame)
dl_test_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]", 'input_dropout_ratio': 27 }
parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in bad-parameters parameters validation result (input_dropout_ratio)."
h2o.H2O.verboseprint("Good params validation messages: ", repr(parameters_validation))
assert 0 != parameters_validation['error_count'], "FAIL: 0 == error_count in bad-parameters parameters validation result: " + repr(parameters_validation)

found_expected_error = False
for validation_message in parameters_validation['messages']:
    if validation_message['message_type'] == 'ERROR' and validation_message['field_name'] == 'input_dropout_ratio':
        found_expected_error = True
assert found_expected_error, "FAIL: Failed to find error message about input_dropout_ratio in the validation messages."

# Bad parameters (no response_column):
dl_test_parameters = {'hidden': "[10, 20, 10]" }
parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame='prostate_binomial', parameters=dl_test_parameters, timeoutSecs=240) # synchronous
assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in bad-parameters parameters validation result (response_column)."
h2o.H2O.verboseprint("Good params validation messages: ", repr(parameters_validation))
assert 0 != parameters_validation['error_count'], "FAIL: 0 == error_count in bad-parameters parameters validation result: " + repr(parameters_validation)


#######################################
# Try to build DeepLearning model for Prostate but with bad parameters; we should get a ModelParametersSchema with the error.
if h2o_test_utils.isVerbose(): print 'About to try to build a DeepLearning model with bad parameters. . .'
dl_prostate_bad_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]", 'input_dropout_ratio': 27  }
parameters_validation = a_node.build_model(algo='deeplearning', model_id='deeplearning_prostate_binomial_bad', training_frame='prostate_binomial', parameters=dl_prostate_bad_parameters, timeoutSecs=240) # synchronous
h2o_test_utils.validate_validation_messages(parameters_validation, ['input_dropout_ratio'])
assert parameters_validation['__http_response']['status_code'] == requests.codes.precondition_failed, "FAIL: expected 412 Precondition Failed from a bad build request, got: " + str(parameters_validation['__http_response']['status_code'])
if h2o_test_utils.isVerbose(): print 'Done trying to build DeepLearning model with bad parameters.'

#####################################
# Early test of predict()
# TODO: remove after we remove the early exit
p = a_node.predict(model='deeplearning_airlines_binomial', frame='airlines_binomial', predictions_frame='deeplearning_airlines_binomial_predictions')
h2o_test_utils.validate_predictions(a_node, p, 'deeplearning_airlines_binomial', 'airlines_binomial', 43978, predictions_frame='deeplearning_airlines_binomial_predictions')
h2o_test_utils.validate_frame_exists(a_node, 'deeplearning_airlines_binomial_predictions')
h2o.H2O.verboseprint("Predictions for scoring: ", 'deeplearning_airlines_binomial', " on: ", 'airlines_binomial', ":  ", repr(p))

# print h2o_test_utils.dump_json(p)

print("WARNING: Terminating test before the end because we don't have as.factor yet. . .")   # TODO: Remove after deeplearning_prostate_binomial is updated
sys.exit(0)

###################################
# Compute and check ModelMetrics for 'deeplearning_prostate_binomial'
mm = a_node.compute_model_metrics(model='deeplearning_prostate_binomial', frame='prostate_binomial')
assert mm is not None, "FAIL: Got a null result for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial'
assert 'model_category' in mm, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " does not contain a model_category."
assert 'Binomial' == mm['model_category'], "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " model_category is not Binomial, it is: " + str(mm['model_category'])
assert 'AUC' in mm, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " does not contain an AUC element: " + h2o_test_utils.dump_json(mm)
assert type(mm['AUC']) is float, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " AUC element is not a float: " + h2o_test_utils.dump_json(mm)

assert 'confusion_matrices' in mm, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " does not contain a confusion_matrices element: " + h2o_test_utils.dump_json(mm)
assert type(mm['confusion_matrices']) is list, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " confusion_matrices element is not a list: " + h2o_test_utils.dump_json(mm)

# print h2o_test_utils.dump_json(mm)
h2o.H2O.verboseprint("ModelMetrics for scoring: ", 'deeplearning_prostate_binomial', " on: ", 'prostate_binomial', ":  ", repr(mm))

###################################
# Check for ModelMetrics for 'deeplearning_prostate_binomial' in full list
mms = a_node.model_metrics() # fetch all
assert 'model_metrics' in mms, 'FAIL: Failed to find model_metrics in result of /3/ModelMetrics.'
found_mm = False
for mm in mms['model_metrics']:
    assert 'model' in mm, "FAIL: mm does not contain a model element: " + repr(mm)
    assert 'name' in mm['model'], "FAIL: mm[model] isn't a key with a name: " + repr(mm)
    assert 'type' in mm['model'], "FAIL: mm[model] does not contain a type: " + repr(mm)
    assert 'Key<Model>' == mm['model']['type'], "FAIL: mm[model] type is not Key<Model>: " + repr(mm['model']['type'])

    assert 'frame' in mm, "FAIL: mm does not contain a frame element: " + repr(mm)
    assert 'name' in mm['frame'], "FAIL: mm[frame] does not contain a name: " + repr(mm)
    assert 'type' in mm['frame'], "FAIL: mm[frame] does not contain a type: " + repr(mm)
    assert 'Key<Frame>' == mm['frame']['type'], "FAIL: mm[frame] type is not Key<Frame>: " + repr(mm)

    model_key = mm['model']['name']
    frame_key = mm['frame']['name'] # TODO: should match
    if model_key == 'deeplearning_prostate_binomial' and frame_key == 'prostate_binomial':
        found_mm = True
assert found_mm, "FAIL: Failed to find ModelMetrics object for model: " + 'deeplearning_prostate_binomial' + " and frame: " + 'prostate_binomial'

# test delete_model_metrics
mms = a_node.model_metrics('deeplearning_prostate_binomial', 'prostate_binomial')
assert len(mms['model_metrics']) == 1, "FAIL: expected 1 ModelMetrics, found: " + str(len(mms['model_metrics']))
a_node.delete_model_metrics('deeplearning_prostate_binomial', 'prostate_binomial')
mms = a_node.model_metrics('deeplearning_prostate_binomial', 'prostate_binomial')
assert len(mms['model_metrics']) == 0, "FAIL: expected 0 ModelMetrics, found: " + str(len(mms['model_metrics']))


###################################
# Predict and check ModelMetrics for 'deeplearning_prostate_binomial'
p = a_node.predict(model='deeplearning_prostate_binomial', frame='prostate_binomial', predictions_frame='deeplearning_prostate_binomial_predictions')
h2o_test_utils.validate_predictions(a_node, p, 'deeplearning_prostate_binomial', 'prostate_binomial', 380, predictions_frame='deeplearning_prostate_binomial_predictions')
h2o_test_utils.validate_frame_exists(a_node, 'deeplearning_prostate_binomial_predictions')
h2o.H2O.verboseprint("Predictions for scoring: ", 'deeplearning_prostate_binomial', " on: ", 'prostate_binomial', ":  ", repr(p))

###################################
# Predict and check ModelMetrics for 'deeplearning_prostate_regression'
p = a_node.predict(model='deeplearning_prostate_regression', frame='prostate_binomial')
h2o_test_utils.validate_predictions(a_node, p, 'deeplearning_prostate_regression', 'prostate_binomial', 380)
h2o.H2O.verboseprint("Predictions for scoring: ", 'deeplearning_prostate_regression', " on: ", 'prostate_binomial', ":  ", repr(p))

###################################
# Predict and check ModelMetrics for 'gbm_prostate_binomial'
p = a_node.predict(model='gbm_prostate_binomial', frame='prostate_binomial')
h2o_test_utils.validate_predictions(a_node, p, 'gbm_prostate_binomial', 'prostate_binomial', 380)
h2o.H2O.verboseprint("Predictions for scoring: ", 'gbm_prostate_binomial', " on: ", 'prostate_binomial', ":  ", repr(p))

###################################
# Predict and check ModelMetrics for 'gbm_prostate_regression'
p = a_node.predict(model='gbm_prostate_regression', frame='prostate_binomial')
h2o_test_utils.validate_predictions(a_node, p, 'gbm_prostate_regression', 'prostate_binomial', 380)
h2o.H2O.verboseprint("Predictions for scoring: ", 'gbm_prostate_regression', " on: ", 'prostate_binomial', ":  ", repr(p))

###################################
# Predict and check ModelMetrics (empty now except for predictions frame) for 'kmeans_prostate'
p = a_node.predict(model='kmeans_prostate', frame='prostate_binomial')
h2o_test_utils.validate_predictions(a_node, p, 'kmeans_prostate', 'prostate_binomial', 380)
h2o.H2O.verboseprint("Predictions for scoring: ", 'kmeans_prostate', " on: ", 'prostate_binomial', ":  ", repr(p))

###################################
# Predict with reversed keys (should get an H2OErrorV1):
# TODO: this works, but I'm not handling 500s yet in the automated test:
# p = a_node.predict(frame='kmeans_prostate', model='prostate_binomial')
# print repr(p)

######################################################################
# Now look for kmeans_prostate_model_name using the one-model API and find_compatible_frames, and check it
model = a_node.models(key='kmeans_prostate', find_compatible_frames=True)
found_kmeans = False;
h2o.H2O.verboseprint('k-means model with find_compatible_frames output: ')
h2o.H2O.verboseprint('/Models/', 'kmeans_prostate', '?find_compatible_frames=true: ', repr(model))
h2o_test_utils.assertKeysExist(model['models'][0], '', ['compatible_frames'])
assert 'prostate_binomial' in model['models'][0]['compatible_frames'], "FAIL: Failed to find " + 'prostate_binomial' + " in compatible_frames list."


######################################################################
# Now look for 'prostate_binomial' using the one-frame API and find_compatible_models, and check it
result = a_node.frames(key='prostate_binomial', find_compatible_models=True, row_count=5)
frames = result['frames']
frames_dict = h2o_test_utils.list_to_dict(frames, 'frame_id/name')
assert 'prostate_binomial' in frames_dict, "FAIL: Failed to find prostate.hex in Frames list."

compatible_models = result['compatible_models']
models_dict = h2o_test_utils.list_to_dict(compatible_models, 'model_id/name')
assert 'deeplearning_prostate_binomial' in models_dict, "FAIL: Failed to find " + 'deeplearning_prostate_binomial' + " in compatible models list: " + repr(result)

assert 'deeplearning_prostate_binomial' in frames[0]['compatible_models'], "FAIL: failed to find deeplearning_prostate_binomial in compatible_models for prostate."
assert 'kmeans_prostate' in frames[0]['compatible_models'], "FAIL: failed to find kmeans_prostate in compatible_models for prostate."
h2o.H2O.verboseprint('/Frames/prosate.hex?find_compatible_models=true: ', repr(result))

####################################
# test schemas collection GET again
if h2o_test_utils.isVerbose(): print 'Testing /Metadata/schemas again. . .'
schemas = a_node.schemas(timeoutSecs=240)
assert 'schemas' in schemas, "FAIL: failed to find 'schemas' field in output of /Metadata/schemas: " + repr(schemas)
assert type(schemas['schemas']) is list, "'schemas' field in output of /Metadata/schemas is not a list: " + repr(schemas)
assert len(schemas['schemas']) > 0, "'schemas' field in output of /Metadata/schemas is empty: " + repr(schemas)

if h2o_test_utils.isVerboser():
    print 'Schemas: '
    pp.pprint(schemas)


####################################
# test schemas individual GET again
if h2o_test_utils.isVerbose(): print 'Testing /Metadata/schemas/FrameV3 again. . .'
schemas = a_node.schema(schemaname='FrameV3', timeoutSecs=240)
assert 'schemas' in schemas, "FAIL: failed to find 'schemas' field in output of /Metadata/schemas/FrameV3: " + repr(schemas)
assert type(schemas['schemas']) is list, "'schemas' field in output of /Metadata/schemas/FrameV3 is not a list: " + repr(schemas)
assert len(schemas['schemas']) == 1, "'schemas' field in output of /Metadata/schemas/FrameV3 has an unexpected length: " + repr(schemas)

if h2o_test_utils.isVerboser():
    print 'Schemas: '
    pp.pprint(schemas)


# TODO: use built_models
if clean_up_after:
    h2o_test_utils.cleanup(models=[dl_airlines_model_name, 'deeplearning_prostate_binomial', 'kmeans_prostate'], frames=['prostate_binomial', 'airlines_binomial'])


