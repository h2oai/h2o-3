# TODO: ugh:
import sys, pprint
sys.path.extend(['.','..','py'])
import h2o, h2o_util
import os

#################
# Config is below
#################

print "ARGV is:", sys.argv

ip = "127.0.0.1"
port = 54321

def parse_arguments(argv):
    global ip
    global port

    i = 1
    while (i < len(argv)):
        s = argv[i]
        if (s == "--usecloud"):
            i += 1
            ip_port = argv[i]
            arr = ip_port.split(':')
            ip = arr[0]
            port = int(arr[1])
        i += 1

parse_arguments(sys.argv)

print "ip:", ip
print "port", port

###########
# Utilities
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

def validate_builder(builder):
    assert 'parameters' in builder and isinstance(builder['parameters'], list)
    parameters = builder['parameters']
    assert len(parameters) > 0
    parameter = parameters[0]
    h2o_util.assertKeysExist(parameter, '', ['name', 'label', 'help', 'required', 'type', 'default_value', 'actual_value', 'level', 'values'])


def validate_model_builder_result(result, original_params, model_name):
    if 'validation_error_count' in result:
        print 'Parameters validation error for model: ', model_name
        print 'Input parameters: '
        pp.pprint(original_params)
        print 'Returned result: '
        pp.pprint(result)
    assert 'jobs' in result, "Failed to find jobs key for model: " + model_name
    assert 'key' in result, "Failed to find (jobs) key for model: " + model_name


def list_to_dict(l, key):
    result = {}
    for entry in l:
        k = entry[key]
        result[k] = entry
    return result

def validate_actual_parameters(input_parameters, actual_parameters, training_frame, validation_frame):
    actuals_dict = list_to_dict(actual_parameters, 'name')
    for k, v in input_parameters.iteritems():
        # TODO: skipping some stuff for now because they aren't serialized properly
        if k is 'response_column':
            continue

        # TODO: skipping training frame becuase model building is now changing the training frame.  Why?!
        if k is 'training_frame':
            continue

        expected = str(v)
        # Python says True; json says true
        assert k in actuals_dict, "Expected key " + k + " not found in actual parameters list."

        if actuals_dict[k]['type'] == 'boolean':
            expected = expected.lower()

        assert expected == actuals_dict[k]['actual_value'], "Parameter with name: " + k + " expected to have input value: " + str(expected) + ", instead has: " + str(actuals_dict[k]['actual_value'])
    # TODO: training_frame, validation_frame


def cleanup(a_node, models=None, frames=None):
    ###################
    # test delete_model
    if models is None:
        a_node.delete_models()
    else:
        for model in models:
            a_node.delete_model(model)

    ms = a_node.models()
    if models is None:
        assert 'models' in ms and 0 == len(ms['models']), "Called delete_models and the models list isn't empty: " + h2o_util.dump_json(ms)
    else:
        for model in models:
            for m in ms['models']:
                assert m['key'] != model, 'Found model that we tried to delete in the models list: ' + model

    ###################
    # test delete_frame
    if frames is not None:
        for frame in frames:
            a_node.delete_frame(frame)
            ms = a_node.frames(len=5)

            found = False;
            for m in ms['frames']:
                assert m['key'] != frame, 'Found frame that we tried to delete in the frames list: ' + frame
            

    # TODO
    ####################
    # test delete_models
    # jobs = a_node.build_model(algo='kmeans', destination_key='dummy', training_frame=prostate_key, parameters={'K': 2 }, timeoutSecs=240) # synchronous
    # a_node.delete_models()
    # models = a_node.models()

    # assert 'models' in models and 0 == len(models['models']), "Called delete_models and the models list isn't empty: " + h2o_util.dump_json(models)

    # TODO
    ####################
    # test delete_frames


################
# The test body:
################

a_node = h2o.H2O(ip, port)

#########
# Config:
algos = ['example', 'kmeans', 'deeplearning', 'glm']
clean_up_after = False

h2o.H2O.verbose = False
h2o.H2O.verboseprint("connected to: ", "127.0.0.1", 54321)

models = a_node.models()
if h2o.H2O.verbose:
    print 'Models: '
    pp.pprint(models)

frames = a_node.frames(len=5)
if h2o.H2O.verbose:
    print 'Frames: '
    pp.pprint(frames)


####################################
# test model_builders collection GET
print 'Testing /ModelBuilders. . .'
model_builders = a_node.model_builders(timeoutSecs=240)

if h2o.H2O.verbose:
    print 'ModelBuilders: '
    pp.pprint(model_builders)

for algo in algos:
    assert algo in model_builders['model_builders'], "Failed to find algo: " + algo
    builder = model_builders['model_builders'][algo]
    validate_builder(builder)
    

####################################
# test model_builders individual GET
print 'Testing /ModelBuilders/{algo}. . .'
for algo in algos:
    model_builder = a_node.model_builders(algo=algo, timeoutSecs=240)
    assert algo in model_builder['model_builders'], "Failed to find algo: " + algo
    builder = model_builders['model_builders'][algo]
    validate_builder(builder)

####################################
# test model_metrics collection GET
print 'Testing /ModelMetrics. . .'
model_metrics = a_node.model_metrics(timeoutSecs=240)

if h2o.H2O.verbose:
    print 'ModelMetrics: '
    pp.pprint(model_metrics)

####################################
# test model_metrics individual GET
# TODO

# Clean up frames
print 'Cleaning up old stuff. . .'
cleanup(a_node)

################################################
# Import prostate.csv
import_result = a_node.import_files(path=os.path.realpath("../../smalldata/logreg/prostate.csv"))
print "import_result: "
pp.pprint(import_result)
print "frames: "
pp.pprint(a_node.frames(key=import_result['keys'][0], len=5))
frames = a_node.frames(key=import_result['keys'][0], len=5)['frames']
assert frames[0]['isText'], "Raw imported Frame is not isText"
parse_result = a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files
prostate_key = parse_result['frames'][0]['key']['name']


################################################
# Test /Frames for prostate.csv
frames = a_node.frames(len=5)['frames']
frames_dict = h2o_util.list_to_dict(frames, 'key/name')

# TODO: remove:
if h2o.H2O.verbose:
    print "frames: "
    pp.pprint(frames)

if h2o.H2O.verbose:
    print "frames_dict: "
    pp.pprint(frames_dict)

# TODO: test len and offset (they aren't working yet)
assert prostate_key in frames_dict, "Failed to find " + prostate_key + " in Frames list."
assert not frames_dict[prostate_key]['isText'], "Parsed Frame is isText"


# Test /Frames/{key} for prostate.csv
frames = a_node.frames(key=prostate_key, len=5)['frames']
frames_dict = h2o_util.list_to_dict(frames, 'key/name')
assert prostate_key in frames_dict, "Failed to find prostate.hex in Frames list."
columns_dict = h2o_util.list_to_dict(frames[0]['columns'], 'label')
assert 'CAPSULE' in columns_dict, "Failed to find CAPSULE in Frames/prostate.hex."
assert 'AGE' in columns_dict, "Failed to find AGE in Frames/prostate.hex/columns."
assert 'bins' in columns_dict['AGE'], "Failed to find bins in Frames/prostate.hex/columns/AGE."
print 'bins: ', repr(columns_dict['AGE']['bins'])
assert None is columns_dict['AGE']['bins'], "Failed to clear bins field." # should be cleared except for /summary


# Test /Frames/{key}/columns for prostate.csv
frames = a_node.columns(key=prostate_key)['frames']
columns_dict = h2o_util.list_to_dict(frames[0]['columns'], 'label')
assert 'ID' in columns_dict, "Failed to find ID in Frames/prostate.hex/columns."
assert 'AGE' in columns_dict, "Failed to find AGE in Frames/prostate.hex/columns."
assert 'bins' in columns_dict['AGE'], "Failed to find bins in Frames/prostate.hex/columns/AGE."
print 'bins: ', repr(columns_dict['AGE']['bins'])
assert None is columns_dict['AGE']['bins'], "Failed to clear bins field." # should be cleared except for /summary

# Test /Frames/{key}/columns/{label} for prostate.csv
frames = a_node.column(key=prostate_key, column='AGE')['frames']
columns_dict = h2o_util.list_to_dict(frames[0]['columns'], 'label')
assert 'AGE' in columns_dict, "Failed to find AGE in Frames/prostate.hex/columns."
assert 'bins' in columns_dict['AGE'], "Failed to find bins in Frames/prostate.hex/columns/AGE."
print 'bins: ', repr(columns_dict['AGE']['bins'])
assert None is columns_dict['AGE']['bins'], "Failed to clear bins field." # should be cleared except for /summary

# Test /Frames/{key}/columns/{label}/summary for prostate.csv
frames = a_node.summary(key=prostate_key, column='AGE')['frames']
columns_dict = h2o_util.list_to_dict(frames[0]['columns'], 'label')
assert 'AGE' in columns_dict, "Failed to find AGE in Frames/prostate.hex/columns/AGE/summary."
col = columns_dict['AGE']
h2o_util.assertKeysExistAndNonNull(col, '', ['label', 'missing', 'zeros', 'pinfs', 'ninfs', 'mins', 'maxs', 'mean', 'sigma', 'type', 'data', 'precision', 'bins', 'base', 'stride', 'pctiles'])
h2o_util.assertKeysExist(col, '', ['domain', 'str_data'])
assert col['mins'][0] == 43, 'Failed to find 43 as the first min for AGE.'
assert col['maxs'][0] == 79, 'Failed to find 79 as the first max for AGE.'
assert col['mean'] == 66.03947368421052, 'Failed to find 66.03947368421052 as the mean for AGE.'
assert col['sigma'] == 6.527071269173308, 'Failed to find 6.527071269173308 as the sigma for AGE.'
assert col['type'] == 'int', 'Failed to find int as the type for AGE.'
assert col['data'][0] == 65, 'Failed to find 65 as the first data for AGE.'
assert col['precision'] == -1, 'Failed to find -1 as the precision for AGE.'
assert col['bins'][0] == 1, 'Failed to find 1 as the first bin for AGE.'
assert col['base'] == 43, 'Failed to find 43 as the base for AGE.'
assert col['stride'] == 1, 'Failed to find 1 as the stride for AGE.'
assert col['pctiles'][0] == 50.5, 'Failed to find 50.5 as the first pctile for AGE.'


################################################
# Import allyears2k_headers.zip
import_result = a_node.import_files(path=os.path.realpath("../../smalldata/airlines/allyears2k_headers.zip"))
parse_result = a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files
if h2o.H2O.verbose:
    pp.pprint(parse_result)
airlines_key = parse_result['frames'][0]['key']['name']

####################
# Build KMeans model
model_builders = a_node.model_builders(timeoutSecs=240)
if h2o.H2O.verbose:
    pp.pprint(model_builders)

kmeans_builder = a_node.model_builders(algo='kmeans', timeoutSecs=240)['model_builders']['kmeans']

kmeans_model_name = 'prostate_KMeans_1' # TODO: currently can't specify the target key

print 'About to build a KMeans model. . .'
kmeans_parameters = {'K': 2 }
result = a_node.build_model(algo='kmeans', destination_key=kmeans_model_name, training_frame=prostate_key, parameters=kmeans_parameters, timeoutSecs=240) # synchronous
print 'kmeans build result: '
pp.pprint(result)

validate_model_builder_result(result, kmeans_parameters, kmeans_model_name)
print 'Done building KMeans model.'


#######################################
# Test DeepLearning parameters validation
#
# Default parameters:
model_builder = a_node.model_builders(algo='deeplearning', timeoutSecs=240)['model_builders']['deeplearning']
dl_test_parameters_list = model_builder['parameters']
dl_test_parameters = {value['name'] : value['default_value'] for value in dl_test_parameters_list}

parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
assert 'validation_error_count' in parameters_validation, "Failed to find validation_error_count in good-parameters parameters validation result."
h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))
if 1 != parameters_validation['validation_error_count']:
    print "validation errors: "
    pp.pprint(parameters_validation)
assert 1 == parameters_validation['validation_error_count'], "1 != validation_error_count in good-parameters parameters validation result."
assert 'training_frame' == parameters_validation['validation_messages'][0]['field_name'], "First validation message is about missing training frame."

# Good parameters (note: testing with null training_frame):
dl_test_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]" }
parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
assert 'validation_error_count' in parameters_validation, "Failed to find validation_error_count in good-parameters parameters validation result."
h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))
if 1 != parameters_validation['validation_error_count']:
    print "validation errors: "
    pp.pprint(parameters_validation)
assert 1 == parameters_validation['validation_error_count'], "1 != validation_error_count in good-parameters parameters validation result."
assert 'training_frame' == parameters_validation['validation_messages'][0]['field_name'], "First validation message is about missing training frame."

# Bad parameters (hidden is null):
# (note: testing with null training_frame)
dl_test_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]", 'input_dropout_ratio': 27 }
parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
assert 'validation_error_count' in parameters_validation, "Failed to find validation_error_count in bad-parameters parameters validation result."
h2o.H2O.verboseprint("Good params validation messages: ", repr(parameters_validation))
assert 2 == parameters_validation['validation_error_count'], "2 != validation_error_count in bad-parameters parameters validation result."
assert 'training_frame' == parameters_validation['validation_messages'][0]['field_name'], "First validation message is about missing training frame."

found_expected_error = False
for validation_message in parameters_validation['validation_messages']:
    if validation_message['message_type'] == 'ERROR' and validation_message['field_name'] == 'input_dropout_ratio':
        found_expected_error = True
assert found_expected_error, "Failed to find error message about input_dropout_ratio in the validation messages."

#######################################
# Build DeepLearning model for Prostate
dl_prostate_model_name = 'prostate_DeepLearning_1'

print 'About to build a DeepLearning model. . .'
dl_prostate_1_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]" }
jobs = a_node.build_model(algo='deeplearning', destination_key=dl_prostate_model_name, training_frame=prostate_key, parameters=dl_prostate_1_parameters, timeoutSecs=240) # synchronous
validate_model_builder_result(result, dl_prostate_1_parameters, dl_prostate_model_name)
print 'Done building DeepLearning model.'

models = a_node.models()

if h2o.H2O.verbose:
    print 'After Model build: Models: '
    pp.pprint(models)


#######################################
# Try to build DeepLearning model for Prostate but with bad parameters; we should get a ModelParametersSchema with the error.
dl_prostate_model_name_bad = 'prostate_DeepLearning_bad'

print 'About to try to build a DeepLearning model with bad parameters. . .'
dl_prostate_bad_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]", 'input_dropout_ratio': 27  }
parameters_validation = a_node.build_model(algo='deeplearning', destination_key=dl_prostate_model_name_bad, training_frame=prostate_key, parameters=dl_prostate_bad_parameters, timeoutSecs=240) # synchronous
validate_model_builder_result(result, dl_prostate_bad_parameters, dl_prostate_model_name_bad)
print 'Done trying to build DeepLearning model with bad parameters.'

assert 'validation_error_count' in parameters_validation, "Failed to find validation_error_count in bad-parameters build result."
assert 0 < parameters_validation['validation_error_count'], "0 != validation_error_count in bad-parameters build validation result."
found_expected_error = False
for validation_message in parameters_validation['validation_messages']:
    if validation_message['message_type'] == 'ERROR' and validation_message['field_name'] == 'input_dropout_ratio':
        found_expected_error = True
assert found_expected_error, "Failed to find error message about input_dropout_ratio in the bad build validation messages."


#######################################
# Build DeepLearning model for Airlines
dl_airlines_model_name = 'airlines_DeepLearning_1'

print 'About to build a DeepLearning model. . .'
dl_airline_1_parameters = {'response_column': 'IsDepDelayed' }
jobs = a_node.build_model(algo='deeplearning', destination_key=dl_airlines_model_name, training_frame=airlines_key, parameters=dl_airline_1_parameters, timeoutSecs=240) # synchronous
validate_model_builder_result(result, dl_airline_1_parameters, dl_airlines_model_name)
print 'Done building DeepLearning model.'

models = a_node.models()

if h2o.H2O.verbose:
    print 'After Model build: Models: '
    pp.pprint(models)

############################
# Check kmeans_model_name
found_kmeans = False;
kmeans_model = None
print 'looking for model: ', kmeans_model_name
for model in models['models']:
    print 'Is it: ', model['key'], '?'
    if model['key'] == kmeans_model_name:
        found_kmeans = True
        kmeans_model = model

assert found_kmeans, 'Did not find ' + kmeans_model_name + ' in the models list.'
validate_actual_parameters(kmeans_parameters, kmeans_model['parameters'], prostate_key, None)

###################################
# Check dl_prostate_model_name
# found_dl = False;
# dl_model = None
# for model in models['models']:
#     if model['key'] == dl_prostate_model_name:
#         found_dl = True
#         dl_model = model
# 
# assert found_dl, 'Did not find ' + dl_prostate_model_name + ' in the models list.'
# validate_actual_parameters(dl_prostate_1_parameters, dl_model['parameters'], prostate_key, None)
# 
###################################
# Compute and check ModelMetrics for dl_prostate_model_name
# mm = a_node.compute_model_metrics(model=dl_prostate_model_name, frame=prostate_key)
# assert mm is not None, "Got a null result for scoring: " + dl_prostate_model_name + " on: " + prostate_key
# assert 'auc' in mm, "ModelMetrics for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " does not contain an AUC."
# assert 'cm' in mm, "ModelMetrics for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " does not contain a CM."
# h2o.H2O.verboseprint("ModelMetrics for scoring: ", dl_prostate_model_name, " on: ", prostate_key, ":  ", repr(mm))
# 
###################################
# Check for ModelMetrics for dl_prostate_model_name in full list
# mms = a_node.model_metrics() # fetch all
# assert 'model_metrics' in mms, 'Failed to find model_metrics in result of /3/ModelMetrics.'
# found_mm = False
# for mm in mms['model_metrics']:
#     model_key = mm['model']['key']
#     frame_key = mm['frame']['key']['name'] # TODO: should match
#     if model_key == dl_prostate_model_name and frame_key == prostate_key:
#         found_mm = True
# assert found_mm, "Failed to find ModelMetrics object for model: " + dl_prostate_model_name + " and frame: " + prostate_key
# 
###################################
# Predict and check ModelMetrics for dl_prostate_model_name
# p = a_node.predict(model=dl_prostate_model_name, frame=prostate_key)
# assert p is not None, "Got a null result for scoring: " + dl_prostate_model_name + " on: " + prostate_key
# assert 'model_metrics' in p, "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " does not contain a model_metrics object."
# mm = p['model_metrics'][0]
# h2o.H2O.verboseprint('mm: ', repr(mm))
# assert 'auc' in mm, "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " does not contain an AUC."
# assert 'cm' in mm, "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " does not contain a CM."
# assert 'predictions' in mm, "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " does not contain an predictions section."
# predictions = mm['predictions']
# h2o.H2O.verboseprint('p: ', repr(p))
# assert 'columns' in predictions, "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " does not contain an columns section."
# assert len(predictions['columns']) > 0, "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " does not contain any columns."
# assert 'label' in predictions['columns'][0], "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " column 0 has no label element."
# assert 'predict' == predictions['columns'][0]['label'], "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " column 0 is not 'predict'."
# assert 380 == predictions['rows'], "Predictions for scoring: " + dl_prostate_model_name + " on: " + prostate_key + " has an unexpected number of rows."
# 
# h2o.H2O.verboseprint("Predictions for scoring: ", dl_prostate_model_name, " on: ", prostate_key, ":  ", repr(p))
# 
###################################
# Check dl_airlines_model_name
# found_dl = False;
# dl_model = None
# for model in models['models']:
#     if model['key'] == dl_airlines_model_name:
#         found_dl = True
#         dl_model = model
# 
# assert found_dl, 'Did not find ' + dl_airlines_model_name + ' in the models list.'
# validate_actual_parameters(dl_airline_1_parameters, dl_model['parameters'], airlines_key, None)
# 
######################################################################
# Now look for kmeans_model_name using the one-model API and find_compatible_frames, and check it
# model = a_node.models(key=kmeans_model_name, find_compatible_frames=True)
# found_kmeans = False;
# h2o.H2O.verboseprint('k-means model with find_compatible_frames output: ')
# h2o.H2O.verboseprint('/Models/', kmeans_model_name, '?find_compatible_frames=true: ', repr(model))
# h2o_util.assertKeysExist(model['models'][0], '', ['compatible_frames'])
# assert prostate_key in model['models'][0]['compatible_frames'], "Failed to find " + prostate_key + " in compatible_frames list."
# 

######################################################################
# Now look for prostate_key using the one-frame API and find_compatible_models, and check it
result = a_node.frames(key=prostate_key, find_compatible_models=True, len=5)
frames = result['frames']
frames_dict = h2o_util.list_to_dict(frames, 'key/name')
assert prostate_key in frames_dict, "Failed to find prostate.hex in Frames list."

# compatible_models = result['compatible_models']
# models_dict = h2o_util.list_to_dict(compatible_models, 'key')
# assert dl_prostate_model_name in models_dict, "Failed to find " + dl_prostate_model_name + " in compatible models list."
# 
# assert dl_prostate_model_name in frames[0]['compatible_models']
# assert kmeans_model_name in frames[0]['compatible_models']
# h2o.H2O.verboseprint('/Frames/prosate.hex?find_compatible_models=true: ', repr(result))
# 
if clean_up_after:
    cleanup(models=[dl_airlines_model_name, dl_prostate_model_name, kmeans_model_name], frames=[prostate_key, airlines_key])


