import sys, os, time, json, datetime, errno, urlparse, stat, getpass, requests, pprint
import h2o

debug_rest = False
verbosity = 0  # 0, 1, 2, 3
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

def setVerbosity(level):
    global verbosity
    verbosity = level

def isVerbose():
    global verbosity
    return True if verbosity > 0 else False

def isVerboser():
    global verbosity
    return True if verbosity > 1 else False

def isVerbosest():
    global verbosity
    return True if verbosity > 2 else False

def sleep(secs):
    if getpass.getuser() == 'jenkins':
        period = max(secs, 120)
    else:
        period = secs
        # if jenkins, don't let it sleep more than 2 minutes
    # due to left over h2o.sleep(3600)
    time.sleep(period)

def dump_json(j):
    return json.dumps(j, sort_keys=True, indent=2)

def check_params_update_kwargs(params_dict, kw, function, print_params):
    # only update params_dict..don't add
    # throw away anything else as it should come from the model (propagating what RF used)
    for k in kw:
        if k in params_dict:
            params_dict[k] = kw[k]
        else:
            raise Exception("illegal parameter '%s' in %s" % (k, function))

    if print_params:
        print "%s parameters:" % function + repr(params_dict)
        sys.stdout.flush()

######################
# Assertion-type stuff
def make_sure_path_exists(path):
    try:
        os.makedirs(path)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

def followPath(d, path_elems):
    for path_elem in path_elems:
        if "" != path_elem:
            idx = -1
            if path_elem.endswith("]"):
                idx = int(path_elem[path_elem.find("[") + 1:path_elem.find("]")])
                path_elem = path_elem[:path_elem.find("[")]
            assert path_elem in d, "FAIL: Failed to find key: " + path_elem + " in dict: " + repr(d)

            if -1 == idx:
                d = d[path_elem]
            else:
                d = d[path_elem][idx]
        
    return d

def assertKeysExist(d, path, keys):
    path_elems = path.split("/")

    d = followPath(d, path_elems)
    for key in keys:
        assert key in d, "FAIL: Failed to find key: " + key + " in dict: " + repr(d)

def assertKeysExistAndNonNull(d, path, keys):
    path_elems = path.split("/")

    d = followPath(d, path_elems)
    for key in keys:
        assert key in d, "FAIL: Failed to find key: " + key + " in dict: " + repr(d)
        assert d[key] != None, "FAIL: Value unexpectedly null: " + key + " in dict: " + repr(d)

def assertKeysDontExist(d, path, keys):
    path_elems = path.split("/")

    d = followPath(d, path_elems)
    for key in keys:
        assert key not in d, "FAIL: Unexpectedly found key: " + key + " in dict: " + repr(d)


###############
# LOGGING STUFF
# used to rename the sandbox when running multiple tests in same dir (in different shells)
def get_sandbox_name():
    if os.environ.has_key("H2O_SANDBOX_NAME"):
        a = os.environ["H2O_SANDBOX_NAME"]
        print "H2O_SANDBOX_NAME", a
        return a
    else:
        return "sandbox"

LOG_DIR = get_sandbox_name()
make_sure_path_exists(LOG_DIR)

def log(cmd, comment=None):
    filename = LOG_DIR + '/commands.log'
    # everyone can read
    with open(filename, 'a') as f:
        f.write(str(datetime.datetime.now()) + ' -- ')
        # what got sent to h2o
        # f.write(cmd)
        # let's try saving the unencoded url instead..human readable
        if cmd:
            f.write(urlparse.unquote(cmd))
            if comment:
                f.write('    #')
                f.write(comment)
            f.write("\n")
        elif comment: # for comment-only
            f.write(comment + "\n")
            # jenkins runs as 0xcustomer, and the file wants to be archived by jenkins who isn't in his group
    permissions = stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP | stat.S_IROTH
    os.chmod(filename, permissions)

def log_rest(s):
    if not debug_rest:
        return
    rest_log_file = open(os.path.join(LOG_DIR, "rest.log"), "a")
    rest_log_file.write(s)
    rest_log_file.write("\n")
    rest_log_file.close()


######################
# REST API stuff

def list_to_dict(l, key):
    '''
    Given a List and a key to look for in each element return a Dict which maps the value of that key to the element.
    Also handles nesting for the key, so you can use this for things like a list of elements which contain H2O Keys and
    return a Dict indexed by the 'name" element within the key.
    list_to_dict([{'key': {'name': 'joe', 'baz': 17}}, {'key': {'name': 'bobby', 'baz': 42}}], 'key/name') =>
    {'joe': {'key': {'name': 'joe', 'baz': 17}}, 'bobby': {'key': {'name': 'bobby', 'baz': 42}}}
    '''
    result = {}
    for entry in l:
        # print 'In list_to_dict, entry: ', repr(entry)

        part = entry
        k = None
        for keypart in key.split('/'):
            part = part[keypart]
            k = keypart

            # print 'for keypart: ', keypart, ' part: ', repr(part)

        result[part] = entry
    # print 'result: ', repr(result)
    return result


def validate_builder(algo, builder):
    ''' Validate that a model builder seems to have a well-formed parameters list. '''
    assert 'parameters' in builder, "FAIL: Failed to find parameters list in builder: " + algo + " (" + repr(builder) + ")"
    assert isinstance(builder['parameters'], list), "FAIL: 'parameters' element is not a list in builder: " + algo + " (" + repr(builder) + ")"
    parameters = builder['parameters']
    assert len(parameters) > 0, "FAIL: parameters list is empty: " + algo + " (" + repr(builder) + ")"
    for parameter in parameters:
        assertKeysExist(parameter, '', ['name', 'label', 'help', 'required', 'type', 'default_value', 'actual_value', 'level', 'values'])

    assert 'can_build' in builder, "FAIL: Failed to find can_build list in builder: " + algo + " (" + repr(builder) + ")"
    assert isinstance(builder['can_build'], list), "FAIL: 'can_build' element is not a list in builder: " + algo + " (" + repr(builder) + ")"
    assert len(builder['can_build']) > 0, "FAIL: 'can_build' list is empty in builder: " + algo + " (" + repr(builder) + ")"


def validate_model_builder_result(result, original_params, model_name):
    '''
    Validate that a model build result has no parameter validation errors,
    and that it has a Job with a Key.  Note that model build will return a
    Job if successful, and a ModelBuilder with errors if it's not.
    '''
    global pp
    error = False
    if result is None:
        print 'FAIL: result for model %s is None, timeout during build? result: %s' % (model_name, result)
        error = True

    elif result['__http_response']['status_code'] != requests.codes.ok:
        error = True
        print "FAIL: expected 200 OK from a good validation request, got: " + str(result['__http_response']['status_code'])

    elif 'error_count' in result and result['error_count'] > 0:
        # error case
        print 'FAIL: Parameters validation error for model: ', model_name
        error = True

    if error:
        print 'Input parameters: '
        pp.pprint(original_params)
        print 'Returned result: '
        pp.pprint(result)
        assert result['error_count'] == 0, "FAIL: Non-zero error_count for model: " + model_name

    assert 'job' in result, "FAIL: Failed to find job key for model: " + model_name + ": " + pp.pprint(result)
    job = result['job']
    assert type(job) is dict, "FAIL: Job element for model is not a dict: " + model_name + ": " + pp.pprint(result)
    assert 'key' in job, "FAIL: Failed to find key in job for model: " + model_name + ": " + pp.pprint(result)


def validate_grid_builder_result(result, original_params, grid_params, grid_id):
    '''
    Validate that a grid build result has no parameter validation errors,
    and that it has a Job with a Key.  
    '''
    global pp
    error = False
    if result is None:
        print 'FAIL: result for grid %s is None, timeout during build? result: %s' % (grid_id, result)
        error = True

    elif result['__http_response']['status_code'] != requests.codes.ok:
        error = True
        print "FAIL: expected 200 OK from a good validation request, got: " + str(result['__http_response']['status_code'])

    if error:
        print 'Input parameters: '
        pp.pprint(original_params)
        print 'Grid parameters: '
        pp.pprint(grid_params)
        print 'Returned result: '
        pp.pprint(result)
        assert result['job']['error_count'] == 0, "FAIL: Non-zero error_count for model: " + grid_id


def validate_validation_messages(result, expected_error_fields):
    '''
    Check that we got the expected ERROR validation messages for a model build or validation check with bad parameters.
    '''
    assert 'error_count' in result, "FAIL: Failed to find error_count in bad-parameters model build result."
    assert 0 < result['error_count'], "FAIL: 0 != error_count in bad-parameters model build validation result."

    error_fields = []
    for validation_message in result['messages']:
        if validation_message['message_type'] == 'ERROR':
            error_fields.append(validation_message['field_name'])

    not_found = [item for item in expected_error_fields if item not in error_fields]
    assert len(not_found) == 0, 'FAIL: Failed to find all expected ERROR validation messages.  Missing: ' + repr(not_found) + ' from result: ' + repr(error_fields)
    assert len(not_found) == 0, 'FAIL: Failed to find all expected ERROR validation messages.  Missing: ' + repr(not_found) + ' from result: ' + repr(result['messages'])


def validate_model_exists(a_node, model_name):
    '''
    Validate that a given model key is found in the models list.
    '''
    models = a_node.models()['models']

    models_dict = list_to_dict(models, 'model_id/name')
    assert model_name in models_dict, "FAIL: Failed to find " + model_name + " in models list: " + repr(models_dict.keys())
    return a_node.models(key=model_name)['models'][0]


def validate_frame_exists(a_node, frame_name, frames=None):
    '''
    Validate that a given frame key is found in the frames list.
    '''
    if frames is None:
        result = a_node.frames()
        frames = result['frames']

    frames_dict = list_to_dict(frames, 'frame_id/name')
    assert frame_name in frames_dict, "FAIL: Failed to find " + frame_name + " in frames list: " + repr(frames_dict.keys())
    return frames_dict[frame_name]


def validate_job_exists(a_node, job_name, jobs=None):
    '''
    Validate that a given job key is found in the jobs list.
    '''
    if jobs is None:
        result = a_node.jobs()
        jobs = result['jobs']

    jobs_dict = list_to_dict(jobs, 'key/name')
    assert job_name in jobs_dict, "FAIL: Failed to find " + job_name + " in jobs list: " + repr(jobs_dict.keys())
    return jobs_dict[job_name]


def validate_actual_parameters(input_parameters, actual_parameters, training_frame, validation_frame):
    '''
    Validate that the returned parameters list for a model build contains all the values we passed in as input.
    '''
    actuals_dict = list_to_dict(actual_parameters, 'name')
    for k, expected in input_parameters.iteritems():
        # TODO: skipping some stuff for now because they aren't serialized properly
        if k is 'response_column':
            continue

        # TODO: skipping training frame becuase model building is now changing the training frame.  Why?!
        if k is 'training_frame':
            continue

        # Python says True; json says true
        assert k in actuals_dict, "FAIL: Expected key " + k + " not found in actual parameters list."

        actual = actuals_dict[k]['actual_value']
        actual_type = actuals_dict[k]['type']

        if actual_type == 'boolean':
            expected = bool(expected)
            actual = True if 'true' == actual else False # true -> True
        elif actual_type == 'int':
            expected = int(expected)
            actual = int(actual)
        elif actual_type == 'long':
            expected = long(expected)
            actual = long(actual)
        elif actual_type == 'string':
            # convert from Unicode
            expected = str(expected)
            actual = str(actual)
        elif actual_type == 'string[]':
            # convert from Unicode
            # expected = [str(expected_val) for expected_val in expected]
            actual = [str(actual_val) for actual_val in actual]
        elif actual_type == 'double':
            expected = float(expected)
            actual = float(actual)
        elif actual_type == 'float':
            expected = float(expected)
            actual = float(actual)
        elif actual_type.startswith('Key<'):
            # For keys we send just a String but receive an object
            expected = expected
            actual = actual['name']

        # TODO: don't do exact comparison of floating point!

        assert expected == actual, "FAIL: Parameter with name: " + k + " expected to have input value: " + str(expected) + ", instead has: " + str(actual) + " cast from: " + str(actuals_dict[k]['actual_value']) + " ( type of expected: " + str(type(expected)) + ", type of actual: " + str(type(actual)) + ")"
    # TODO: training_frame, validation_frame


def validate_grid_parameters(grid_parameters, actual_parameters):
    '''
    Validate that the returned parameters list for a model build contains values we passed in as grid parameters.
    '''
    actuals_dict = list_to_dict(actual_parameters, 'name')
    for k, grid_param_values in grid_parameters.iteritems():

        # Python says True; json says true
        assert k in actuals_dict, "FAIL: Expected key " + k + " not found in grid parameters list."

        actual = actuals_dict[k]['actual_value']
        actual_type = actuals_dict[k]['type']

        if actual_type == 'boolean':
            grid_param_values = [bool(x) for x in grid_param_values]
            actual = True if 'true' == actual else False # true -> True
        elif actual_type == 'int':
            grid_param_values = [int(x) for x in grid_param_values]
            actual = int(actual)
        elif actual_type == 'long':
            grid_param_values = [long(x) for x in grid_param_values]
            actual = long(actual)
        elif actual_type == 'string':
            # convert from Unicode
            grid_param_values = [str(x) for x in grid_param_values]
            actual = str(actual)
        elif actual_type == 'string[]':
            # convert from Unicode
            # grid_param_values = [str(grid_param_values_val) for grid_param_values_val in grid_param_values]
            actual = [str(actual_val) for actual_val in actual]
        elif actual_type == 'double':
            grid_param_values = [float(x) for x in grid_param_values]
            actual = float(actual)
        elif actual_type == 'float':
            grid_param_values = [float(x) for x in grid_param_values]
            actual = float(actual)
        elif actual_type.startswith('Key<'):
            # For keys we send just a String but receive an object
            grid_param_values = grid_param_values
            actual = actual['name']

        # TODO: don't do exact comparison of floating point!

        # print("actual_type: " + actual_type)
        # print("actual: " + repr(actual) + " (" + str(type(actual)) + ")")
        # print("grid_param_values: " + repr(grid_param_values))

        # TODO: 1-d arrays only for the moment; no grid over DL layers
        if actual_type.endswith(']'):
            actual = actual[0]
            

        # NOTE: check for IN
        assert actual in grid_param_values, "FAIL: Parameter with name: " + k + " expected to be a possible grid value: " + str(grid_param_values) + ", instead has: " + str(actual) + " cast from: " + str(actuals_dict[k]['actual_value']) + " ( type of expected: " + str(type(grid_param_values[0])) + ", type of actual: " + str(type(actual)) + ")"
    # TODO: training_frame, validation_frame


def validate_predictions(a_node, result, model_name, frame_key, expected_rows, predictions_frame=None):
    '''
    Validate a /Predictions result.
    '''
    assert result is not None, "FAIL: Got a null result for scoring: " + model_name + " on: " + frame_key
    assert 'model_metrics' in result, "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " does not contain a model_metrics object."
    mm = result['model_metrics'][0]
    h2o.H2O.verboseprint('mm: ', repr(mm))
    #assert 'auc' in mm, "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " does not contain an AUC."
    #assert 'cm' in mm, "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " does not contain a CM."
    assert 'predictions' in mm, "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " does not contain an predictions section."
    assert 'frame_id' in mm['predictions'], "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " does not contain a key."
    assert 'name' in mm['predictions']['frame_id'], "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " does not contain a key name."

    predictions_key = mm['predictions']['frame_id']['name']
    f = a_node.frames(key=predictions_key, find_compatible_models=True, row_count=5)
    frames = f['frames']
    frames_dict = list_to_dict(frames, 'frame_id/name')
    assert predictions_key in frames_dict, "FAIL: Failed to find predictions key" + predictions_key + " in Frames list."

    predictions = mm['predictions']
    h2o.H2O.verboseprint('prediction result: ', repr(result))
    assert 'columns' in predictions, "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " does not contain an columns section."
    assert len(predictions['columns']) > 0, "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " does not contain any columns."
    assert 'label' in predictions['columns'][0], "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " column 0 has no label element."
    assert 'predict' == predictions['columns'][0]['label'], "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " column 0 is not 'predict'."
    assert expected_rows == predictions['rows'], "FAIL: Predictions for scoring: " + model_name + " on: " + frame_key + " has an unexpected number of rows."

    assert 'predictions_frame' in result, "FAIL: failed to find 'predictions_frame' in predict result:" + h2o_test_utils.dump_json(result)
    assert 'name' in result['predictions_frame'], "FAIL: failed to find name in 'predictions_frame' in predict result:" + h2o_test_utils.dump_json(result)

    if predictions_frame is not None:
        assert predictions_frame == result['predictions_frame']['name'], "FAIL: bad value for 'predictions_frame' in predict result; expected: " + predictions_frame + ", got: " + result['predictions_frame']['name']

def cleanup(a_node, models=None, frames=None):
    '''
    DELETE the specified models and frames from H2O.
    '''
    ###################
    # test delete_model
    if models is None:
        a_node.delete_models()
    else:
        for model in models:
            a_node.delete_model(model)

    ms = a_node.models()
    if models is None:
        assert 'models' in ms and 0 == len(ms['models']), "FAIL: Called delete_models and the models list isn't empty: " + h2o_test_utils.dump_json(ms)
    else:
        for model in models:
            for m in ms['models']:
                assert m['model_id'] != model, 'FAIL: Found model that we tried to delete in the models list: ' + model

    ###################
    # test delete_frame
    if frames is not None:
        for frame in frames:
            a_node.delete_frame(frame)
            ms = a_node.frames(row_count=5)

            found = False;
            for m in ms['frames']:
                assert m['frame_id'] != frame, 'FAIL: Found frame that we tried to delete in the frames list: ' + frame


    # TODO
    ####################
    # test delete_models
    # jobs = a_node.build_model(algo='kmeans', model_id='dummy', training_frame='prostate_binomial', parameters={'k': 2 }, timeoutSecs=240) # synchronous
    # a_node.delete_models()
    # models = a_node.models()

    # assert 'models' in models and 0 == len(models['models']), "FAIL: Called delete_models and the models list isn't empty: " + h2o_test_utils.dump_json(models)

    # TODO
    ####################
    # test delete_frames

class ModelSpec(dict):
    '''
    Dictionary which specifies all that's needed to build and validate a model.
    '''
    def __init__(self, dest_key, algo, frame_key, params, model_category):
        self['algo'] = algo
        self['frame_key'] = frame_key
        self['params'] = params
        self['model_category'] = model_category

        if dest_key is None:
            self['dest_key'] = algo + "_" + frame_key
        else:
            self['dest_key'] = dest_key

    @staticmethod
    def for_dataset(dest_key, algo, dataset, params):
        '''
        Factory for creating a ModelSpec for a given Dataset (frame and additional metadata).
        '''
        dataset_params = {}
        assert 'model_category' in dataset, "FAIL: Failed to find model_category in dataset: " + repr(dataset)
        if 'response_column' in dataset: dataset_params['response_column'] = dataset['response_column']
        if 'ignored_columns' in dataset: dataset_params['ignored_columns'] = dataset['ignored_columns']

        return ModelSpec(dest_key, algo, dataset['dest_key'], dict(dataset_params.items() + params.items()), dataset['model_category'])


    def build_and_validate_model(self, a_node):
        before = time.time()
        if isVerbose(): print 'About to build: ' + self['dest_key'] + ', a ' + self['algo'] + ' model on frame: ' + self['frame_key'] + ' with params: ' + repr(self['params'])
        result = a_node.build_model(algo=self['algo'], model_id=self['dest_key'], training_frame=self['frame_key'], parameters=self['params'], timeoutSecs=240) # synchronous
        validate_model_builder_result(result, self['params'], self['dest_key'])

        model = validate_model_exists(a_node, self['dest_key'])
        validate_actual_parameters(self['params'], model['parameters'], self['frame_key'], None)

        # TODO: refactor into helper
        assert 'output' in model, 'FAIL: Failed to find output object in model: ' + self['dest_key']
        assert 'model_category' in model['output'], 'FAIL: Failed to find model_category in model: ' + self['dest_key']
        assert model['output']['model_category'] == self['model_category'], 'FAIL: Expected model_category: ' + self['model_category'] + ' but got: ' + model['output']['model_category'] + ' for model: ' + self['dest_key']

        if isVerbose(): print 'Done building: ' + self['dest_key'] + " (" + str(time.time() - before) + ")"
        return model


class GridSpec(dict):
    '''
    Dictionary which specifies all that's needed to build and validate a grid of models.
    '''
    def __init__(self, dest_key, algo, frame_key, params, grid_params, model_category):
        self['algo'] = algo
        self['frame_key'] = frame_key
        self['params'] = params
        self['grid_params'] = grid_params
        self['model_category'] = model_category

        if dest_key is None:
            self['dest_key'] = algo + "_" + frame_key
        else:
            self['dest_key'] = dest_key

    @staticmethod
    def for_dataset(dest_key, algo, dataset, params, grid_params):
        '''
        Factory for creating a GridSpec for a given Dataset (frame and additional metadata).
        '''
        dataset_params = {}
        assert 'model_category' in dataset, "FAIL: Failed to find model_category in dataset: " + repr(dataset)
        if 'response_column' in dataset: dataset_params['response_column'] = dataset['response_column']
        if 'ignored_columns' in dataset: dataset_params['ignored_columns'] = dataset['ignored_columns']

        return GridSpec(dest_key, algo, dataset['dest_key'], dict(dataset_params.items() + params.items()), grid_params, dataset['model_category'])


    def build_and_validate_grid(self, a_node):
        before = time.time()
        if isVerbose(): print 'About to build: ' + self['dest_key'] + ', a ' + self['algo'] + ' model grid on frame: ' + self['frame_key'] + ' with params: ' + repr(self['params']) + ' and grid_params: ' + repr(self['grid_params'])

        # returns a GridSearchSchema:
        result = a_node.build_model_grid(algo=self['algo'], grid_id=self['dest_key'], training_frame=self['frame_key'], parameters=self['params'], grid_parameters=self['grid_params'], timeoutSecs=240) # synchronous
        if isVerbose(): print 'result: ' + repr(result)
        grid = a_node.grid(key=self['dest_key'])
        if isVerbose(): print 'grid: ' + repr(grid)
        
        validate_grid_builder_result(grid, self['params'], self['grid_params'], self['dest_key'])

        # print("grid result: " + repr(grid))
        # print("grid __meta: " + repr(grid['__meta']))
        for model_key_dict in grid['model_ids']:
            model_key = model_key_dict['name']
            model = validate_model_exists(a_node, model_key)
            validate_actual_parameters(self['params'], model['parameters'], self['frame_key'], None)
            validate_grid_parameters(self['grid_params'], model['parameters'])

            assert 'output' in model, 'FAIL: Failed to find output object in model: ' + self['dest_key']
            assert 'model_category' in model['output'], 'FAIL: Failed to find model_category in model: ' + self['dest_key']
            assert model['output']['model_category'] == self['model_category'], 'FAIL: Expected model_category: ' + self['model_category'] + ' but got: ' + model['output']['model_category'] + ' for model: ' + self['dest_key']

        # Cartesian: check that we got the right number of models:
        combos = 1
        for k, vals in self['grid_params'].iteritems():
            combos *= len(vals)
        assert combos == len(grid['model_ids']), 'FAIL: Expected ' + combos + ' models; got: ' + len(grid['model_ids'])

        if isVerbose(): print 'Done building: ' + self['dest_key'] + " (" + str(time.time() - before) + ")"
        return grid


### TODO: we should be able to have multiple DatasetSpecs that come from a single parse, for efficiency
class DatasetSpec(dict):
    '''
    Dictionary which specifies the properties of a Frame (Dataset) for a specific use
    (e.g., prostate data with binomial classification on the CAPSULE column
    OR prostate data with regression on the AGE column).
    '''
    def __init__(self, dest_key, path, expected_rows, model_category, response_column, ignored_columns):
        self['path'] = os.path.realpath(path)
        self['expected_rows'] = expected_rows
        self['model_category'] = model_category
        self['response_column'] = response_column
        self['ignored_columns'] = ignored_columns

        if dest_key == None:
            # specify dest_key every time
            basename = os.path.basename(path)
            basename_split = basename.split(".")
            if len(basename_split) == 1:
                self['dest_key'] = basename_split[0] + ".hex" # name + ".hex"
            else:
                self['dest_key'] = basename_split[-2] + ".hex" # name without suffix + ".hex"
        else:
            self['dest_key'] = dest_key


    def import_and_validate_dataset(self, a_node):
        if isVerbose(): print "About to import and validate: " + self['path']
        import_result = a_node.import_files(path=self['path'])
        if isVerboser():
            print "import_result: "
            pp.pprint(import_result)
            print "frames: "
            pp.pprint(a_node.frames(key=import_result['destination_frames'][0], row_count=5))

        frames = a_node.frames(key=import_result['destination_frames'][0], row_count=5)['frames']
        assert frames[0]['is_text'], "FAIL: Raw imported Frame is not is_text: " + repr(frames[0])
        parse_result = a_node.parse(key=import_result['destination_frames'][0], dest_key=self['dest_key']) # TODO: handle multiple files
        key = parse_result['frames'][0]['frame_id']['name']
        assert key == self['dest_key'], 'FAIL: Imported frame key is wrong; expected: ' + self['dest_key'] + ', got: ' + key
        assert self['expected_rows'] == parse_result['frames'][0]['rows'], 'FAIL: Imported frame number of rows is wrong; expected: ' + str(self['expected_rows']) + ', got: ' + str(parse_result['frames'][0]['rows'])

        self['dataset'] = parse_result['frames'][0]  # save the imported dataset object

        if isVerbose(): print "Imported and validated key: " + self['dataset']['frame_id']['name']
        return self['dataset']


