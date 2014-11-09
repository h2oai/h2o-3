
import time
import h2o_methods
from h2o_test import verboseprint, dump_json

###################
# REST API ACCESSORS
# TODO: remove .json

def jobs(self, job_key=None, timeoutSecs=10, **kwargs):
    '''
    Fetch all the jobs or a single job from the /Jobs endpoint.
    '''
    params_dict = {
        'job_key': job_key
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'jobs', True)
    result = self.do_json_request('2/Jobs.json', timeout=timeoutSecs, params=params_dict)
    return result


# TODO: add delays, etc.
def poll_job(self, job_key, timeoutSecs=10, retryDelaySecs=0.5, **kwargs):
    '''
    Poll a single job from the /Jobs endpoint until it is "status": "DONE" or "CANCELLED" or "FAILED" or we time out.
    '''
    params_dict = {
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'poll_job', True)

    start_time = time.time()
    while True:
        verboseprint('Polling for job: ' + job_key + '. . .')
        result = self.do_json_request('2/Jobs.json/' + job_key, timeout=timeoutSecs, params=params_dict)
        
        if result['jobs'][0]['status'] == 'DONE' or result['jobs'][0]['status'] == 'CANCELLED' or result['jobs'][0]['status'] == 'FAILED':
            verboseprint('Job ' + result['jobs'][0]['status'] + ': ' + job_key + '.')
            return result

        if time.time() - start_time > timeoutSecs:
            verboseprint('Job: ' + job_key + ' timed out in: ' + timeoutSecs + '.')
            return None

        time.sleep(retryDelaySecs)


def import_files(self, path, timeoutSecs=180):
    ''' 
    Import a file or files into h2o.  The 'file' parameter accepts a directory or a single file.
    192.168.0.37:54323/ImportFiles.html?file=%2Fhome%2F0xdiag%2Fdatasets
    '''
    a = self.do_json_request('2/ImportFiles.json',
        timeout=timeoutSecs,
        params={"path": path}
    )
    verboseprint("\nimport_files result:", dump_json(a))
    return a


def parse(self, key, key2=None,
          timeoutSecs=300, retryDelaySecs=0.2, initialDelaySecs=None, pollTimeoutSecs=180,
          noise=None, benchmarkLogging=None, noPoll=False, **kwargs):
    '''
    Parse an imported raw file or files into a Frame.
    '''

    #
    # Call ParseSetup?srcs=[keys] . . .
    #

    if benchmarkLogging:
        cloudPerfH2O.get_log_save(initOnly=True)

    # TODO: multiple keys
    parse_setup_params = {
        'srcs': "[" + key + "]"
    }
    # h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'parse_setup', print_params=True)
    setup_result = self.do_json_request(jsonRequest="ParseSetup.json", timeout=timeoutSecs, params=parse_setup_params)
    verboseprint("ParseSetup result:", dump_json(setup_result))

    # 
    # and then Parse?srcs=<keys list> and params from the ParseSetup result
    # Parse?srcs=[nfs://Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv]&hex=prostate.hex&pType=CSV&sep=44&ncols=9&checkHeader=0&singleQuotes=false&columnNames=[ID,%20CAPSULE,%20AGE,%20RACE,%20DPROS,%20DCAPS,%20PSA,%20VOL,%20GLEASON]
    #

    first = True
    ascii_column_names = '['
    for s in setup_result['columnNames']:
        if not first: ascii_column_names += ', '
        ascii_column_names += str(s)
        first  = False
    ascii_column_names += ']'

    parse_params = {
        'srcs': "[" + setup_result['srcs'][0]['name'] + "]", # TODO: cons up the whole list
        'hex': setup_result['hexName'],
        'pType': setup_result['pType'],
        'sep': setup_result['sep'],
        'ncols': setup_result['ncols'],
        'checkHeader': setup_result['checkHeader'],
        'singleQuotes': setup_result['singleQuotes'],
        'columnNames': ascii_column_names,
    }
    h2o_methods.check_params_update_kwargs(parse_params, kwargs, 'parse', print_params=True)

    parse_result = self.do_json_request(jsonRequest="Parse.json", timeout=timeoutSecs, params=parse_params, **kwargs)
    verboseprint("Parse result:", dump_json(parse_result))

    job_key = parse_result['job']['name']

    # TODO: dislike having different shapes for noPoll and poll
    if noPoll:
        return this.jobs(job_key)

    job_json = self.poll_job(job_key, timeoutSecs=timeoutSecs)

    if job_json:
        dest_key = job_json['jobs'][0]['dest']['name']
        return self.frames(dest_key)

    return None


# TODO: remove .json
def frames(self, key=None, timeoutSecs=10, **kwargs):
    params_dict = {
        'find_compatible_models': 0,
        'offset': 0,
        'len': 100     # TODO: len and offset are not working yet
    }
    '''
    Return a single Frame or all of the Frames in the h2o cluster.  The
    frames are contained in a list called "frames" at the top level of the
    result.  Currently the list is unordered.
    TODO:
    When find_compatible_models is implemented then the top level 
    dict will also contain a "models" list.
    '''
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'frames', True)
    
    if key:
        result = self.do_json_request('3/Frames.json/' + key, timeout=timeoutSecs, params=params_dict)
    else:
        result = self.do_json_request('3/Frames.json', timeout=timeoutSecs, params=params_dict)
    return result


# TODO: remove .json
def columns(self, key, timeoutSecs=10, **kwargs):
    '''
    Return the columns for a single Frame in the h2o cluster.  
    '''
    params_dict = { 
        'offset': 0,
        'len': 100
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'columns', True)
    
    result = self.do_json_request('3/Frames.json/' + key + '/columns', timeout=timeoutSecs, params=params_dict)
    return result


# TODO: remove .json
'''
Return a single column for a single Frame in the h2o cluster.  
'''
def column(self, key, column, timeoutSecs=10, **kwargs):
    params_dict = { 
        'offset': 0,
        'len': 100
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'column', True)
    
    result = self.do_json_request('3/Frames.json/' + key + '/columns/' + column, timeout=timeoutSecs, params=params_dict)
    return result


# TODO: remove .json
def summary(self, key, column, timeoutSecs=10, **kwargs):
    '''
    Return the summary for a single column for a single Frame in the h2o cluster.  
    '''
    params_dict = { 
        'offset': 0,
        'len': 100
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'summary', True)
    
    result = self.do_json_request('3/Frames.json/' + key + '/columns/' + column + '/summary', timeout=timeoutSecs, params=params_dict)
    return result


def delete_frame(self, key, ignoreMissingKey=True, timeoutSecs=60, **kwargs):
    '''
    Delete a frame on the h2o cluster, given its key.
    '''
    assert key is not None, '"key" parameter is null'

    result = self.do_json_request('/3/Frames.json/' + key, cmd='delete', timeout=timeoutSecs)

    # TODO: look for what?
    if not ignoreMissingKey and 'f00b4r' in result:
        raise ValueError('Frame key not found: ' + key)
    return result


def delete_frames(self, timeoutSecs=60, **kwargs):
    '''
    Delete all frames on the h2o cluster.
    '''
    parameters = { }
    result = self.do_json_request('/3/Frames.json', cmd='delete', timeout=timeoutSecs)
    return result


# TODO: remove .json
def model_builders(self, algo=None, timeoutSecs=10, **kwargs):
    '''
    Return a model builder or all of the model builders known to the
    h2o cluster.  The model builders are contained in a dictionary
    called "model_builders" at the top level of the result.  The
    dictionary maps algorithm names to parameters lists.  Each of the
    parameters contains all the metdata required by a client to
    present a model building interface to the user.
    '''
    params_dict = {
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'model_builders', True)

    if algo:
        result = self.do_json_request('2/ModelBuilders.json/' + algo, timeout=timeoutSecs, params=params_dict)
    else:
        result = self.do_json_request('2/ModelBuilders.json', timeout=timeoutSecs, params=params_dict)
    return result


def validate_model_parameters(self, algo, training_frame, parameters, timeoutSecs=60, **kwargs):
    '''
    Check a dictionary of model builder parameters on the h2o cluster using the given algorithm and model parameters.
    '''
    assert algo is not None, '"algo" parameter is null'
    # Allow this now: assert training_frame is not None, '"training_frame" parameter is null'
    assert parameters is not None, '"parameters" parameter is null'

    model_builders = self.model_builders(timeoutSecs=timeoutSecs)
    assert model_builders is not None, "/ModelBuilders REST call failed"
    assert algo in model_builders['model_builders']
    builder = model_builders['model_builders'][algo]
    
    # TODO: test this assert, I don't think this is working. . .
    if training_frame is not None:
        frames = self.frames(key=training_frame)
        assert frames is not None, "/Frames/{0} REST call failed".format(training_frame)
        assert frames['frames'][0]['key']['name'] == training_frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, frames['frames'][0]['key']['name'], training_frame)
        parameters['training_frame'] = training_frame

    # TODO: add parameter existence checks
    # TODO: add parameter value validation
    result = self.do_json_request('/2/ModelBuilders.json/' + algo + "/parameters", cmd='post', timeout=timeoutSecs, postData=parameters, ignoreH2oError=True, noExtraErrorCheck=True)

    verboseprint("model parameters validation: " + repr(result))
    return result


def build_model(self, algo, training_frame, parameters, destination_key = None, timeoutSecs=60, asynchronous=False, **kwargs):
    '''
    Build a model on the h2o cluster using the given algorithm, training 
    Frame and model parameters.
    '''
    assert algo is not None, '"algo" parameter is null'
    assert training_frame is not None, '"training_frame" parameter is null'
    assert parameters is not None, '"parameters" parameter is null'

    model_builders = self.model_builders(timeoutSecs=timeoutSecs)
    assert model_builders is not None, "/ModelBuilders REST call failed"
    assert algo in model_builders['model_builders']
    builder = model_builders['model_builders'][algo]
    
    # TODO: test this assert, I don't think this is working. . .
    frames = self.frames(key=training_frame)
    assert frames is not None, "/Frames/{0} REST call failed".format(training_frame)
    assert frames['frames'][0]['key']['name'] == training_frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, frames['frames'][0]['key']['name'], training_frame)
    parameters['training_frame'] = training_frame

    if destination_key is not None:
        parameters['destination_key'] = destination_key
    result = self.do_json_request('/2/ModelBuilders.json/' + algo, cmd='post', timeout=timeoutSecs, postData=parameters)

    if asynchronous:
        return result
    elif 'validation_error_count' in result:
        # parameters validation failure
        # TODO: add schema_type and schema_version into all the schemas to make this clean to check
        return result
    else:
        job = result['jobs'][0]
        job_key = job['key']['name']
        verboseprint("model building job_key: " + repr(job_key))
        job_json = self.poll_job(job_key, timeoutSecs=timeoutSecs)
        return job_json


def compute_model_metrics(self, model, frame, timeoutSecs=60, **kwargs):
    '''
    Score a model on the h2o cluster on the given Frame and return only the model metrics. 
    '''
    assert model is not None, '"model" parameter is null'
    assert frame is not None, '"frame" parameter is null'

    models = self.models(key=model, timeoutSecs=timeoutSecs)
    assert models is not None, "/Models REST call failed"
    assert models['models'][0]['key'] == model, "/Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['key']['name'], model)

    # TODO: test this assert, I don't think this is working. . .
    frames = self.frames(key=frame)
    assert frames is not None, "/Frames/{0} REST call failed".format(frame)
    assert frames['frames'][0]['key']['name'] == frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['key']['name'], frame)

    result = self.do_json_request('/3/ModelMetrics.json/models/' + model + '/frames/' + frame, cmd='post', timeout=timeoutSecs)

    mm = result['model_metrics'][0]
    verboseprint("model metrics: " + repr(mm))
    return mm


def predict(self, model, frame, timeoutSecs=60, **kwargs):
    assert model is not None, '"model" parameter is null'
    assert frame is not None, '"frame" parameter is null'

    models = self.models(key=model, timeoutSecs=timeoutSecs)
    assert models is not None, "/Models REST call failed"
    assert models['models'][0]['key'] == model, "/Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['key']['name'], model)

    # TODO: test this assert, I don't think this is working. . .
    frames = self.frames(key=frame)
    assert frames is not None, "/Frames/{0} REST call failed".format(frame)
    assert frames['frames'][0]['key']['name'] == frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['key']['name'], frame)

    result = self.do_json_request('/3/Predictions.json/models/' + model + '/frames/' + frame, cmd='post', timeout=timeoutSecs)
    return result


def model_metrics(self, timeoutSecs=60, **kwargs):
    '''
    ModelMetrics list. 
    '''
    result = self.do_json_request('/3/ModelMetrics.json', cmd='get', timeout=timeoutSecs)
    return result


# TODO: remove .json
def models(self, key=None, timeoutSecs=10, **kwargs):
    '''
    Return all of the models in the h2o cluster, or a single model given its key.  
    The models are contained in a list called "models" at the top level of the
    result.  Currently the list is unordered.
    TODO:
    When find_compatible_frames is implemented then the top level 
    dict will also contain a "frames" list.
    '''
    params_dict = {
        'find_compatible_frames': False
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'models', True)

    if key:
        result = self.do_json_request('3/Models.json/' + key, timeout=timeoutSecs, params=params_dict)
    else:
        result = self.do_json_request('3/Models.json', timeout=timeoutSecs, params=params_dict)
    return result


def delete_model(self, key, ignoreMissingKey=True, timeoutSecs=60, **kwargs):
    '''
    Delete a model on the h2o cluster, given its key.
    '''
    assert key is not None, '"key" parameter is null'

    result = self.do_json_request('/3/Models.json/' + key, cmd='delete', timeout=timeoutSecs)

    # TODO: look for what?
    if not ignoreMissingKey and 'f00b4r' in result:
        raise ValueError('Model key not found: ' + key)
    return result


def delete_models(self, timeoutSecs=60, **kwargs):
    '''
    Delete all models on the h2o cluster.
    '''
    parameters = { }
    result = self.do_json_request('/3/Models.json', cmd='delete', timeout=timeoutSecs)
    return result


def endpoints(self, timeoutSecs=60, **kwargs):
    '''
    Fetch the list of REST API endpoints.
    '''
    parameters = { }
    result = self.do_json_request('/1/Metadata/endpoints.json', cmd='get', timeout=timeoutSecs)
    return result

def endpoint_by_number(self, num, timeoutSecs=60, **kwargs):
    '''
    Fetch the metadata for the given numbered REST API endpoint.
    '''
    parameters = { }
    result = self.do_json_request('/1/Metadata/endpoints.json/' + str(num), cmd='get', timeout=timeoutSecs)
    return result
