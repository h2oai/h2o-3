
import time
import h2o_methods, h2o_print as h2p, h2o_sandbox, h2o_args
from h2o_test import verboseprint, dump_json
from h2o_xl import Key

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
# if a key= is passed, it does a frames on that key while polling (intermediate model results?)
def poll_job(self, job_key, timeoutSecs=10, retryDelaySecs=0.5, key=None, **kwargs):
    '''
    Poll a single job from the /Jobs endpoint until it is "status": "DONE" or "CANCELLED" or "FAILED" or we time out.
    '''
    params_dict = {}
    # merge kwargs into params_dict
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'poll_job', False)

    start_time = time.time()
    pollCount = 0
    while True:
        result = self.do_json_request('2/Jobs.json/' + job_key, timeout=timeoutSecs, params=params_dict)
        # print 'Job: ', dump_json(result)

        if key:
            frames_result = self.frames(key=key)
            print 'frames_result for key:', key, dump_json(result)

        jobs = result['jobs'][0]
        description = jobs['description']
        dest = jobs['dest']
        dest_name = dest['name']
        msec = jobs['msec']
        status = jobs['status']
        progress = jobs['progress']
        print description, \
            "dest_name:", dest_name, \
            "\tprogress:", "%-10s" % progress, \
            "\tstatus:", "%-12s" % status, \
            "\tmsec:", msec
        
        if status=='DONE' or status=='CANCELLED' or status=='FAILED':
            h2o_sandbox.check_sandbox_for_errors()
            return result

        # FIX! what are the other legal polling statuses that we should check for?

        if not h2o_args.no_timeout and (time.time() - start_time > timeoutSecs):
            h2o_sandbox.check_sandbox_for_errors()
            emsg = "Job:", job_key, "timed out in:", timeoutSecs
            raise Exception(emsg)
            print emsg
            return None

        # check every other poll, for now
        if (pollCount % 2) == 0:
            h2o_sandbox.check_sandbox_for_errors()

        time.sleep(retryDelaySecs)
        pollCount += 1


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
    h2o_sandbox.check_sandbox_for_errors()
    return a


# key is required
# FIX! for now h2o doesn't support regex here. just key or list of keys
# FIX! default turn off intermediateResults until NPE is fixed.
def parse(self, key, hex_key=None,
          timeoutSecs=300, retryDelaySecs=0.2, initialDelaySecs=None, pollTimeoutSecs=180,
          noise=None, benchmarkLogging=None, noPoll=False, intermediateResults=False, **kwargs):
    '''
    Parse an imported raw file or files into a Frame.
    '''
    # these should override what parse setup gets below
    params_dict = {
        'srcs': None,
        'hex': hex_key, 
        'pType': None, # This is a list?
        'sep': None,
        'ncols': None,
        'checkHeader': None, # how is this used
        'singleQuotes': None,
        'columnNames': None, # list?
        'delete_on_done': None,
        'blocking': None,
    }
        
    # if key is a list, create a comma separated string
    # list or tuple but not string
    if not isinstance(key, basestring):
        # it's a list of some kind (tuple ok?)
        # if len(key) > 1:
        #     print "I noticed you're giving me a list of > 1 keys %s to parse:" % len(key), key

        # len 1 is ok here. 0 not. what if None or [None] here
        if not key:
            raise Exception("key seems to be bad in parse. Should be list or string. %s" % key)
        srcs = "[" + ",".join(key) + "]"
    else:
        # what if None here
        srcs = "[" + key + "]"

    params_dict['srcs'] = srcs

    # merge kwargs into params_dict
    # =None overwrites params_dict
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'parse before setup merge', print_params=False)

    # Call ParseSetup?srcs=[keys] . . .

    # if benchmarkLogging:
    #     cloudPerfH2O.get_log_save(initOnly=True)

    # h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'parse_setup', print_params=True)
    params_setup = {'srcs': srcs}
    setup_result = self.do_json_request(jsonRequest="ParseSetup.json", cmd='post', timeout=timeoutSecs, postData=params_setup)
    h2o_sandbox.check_sandbox_for_errors()
    verboseprint("ParseSetup result:", dump_json(setup_result))

    # and then Parse?srcs=<keys list> and params from the ParseSetup result
    # Parse?srcs=[nfs://Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv]&hex=prostate.hex&pType=CSV&sep=44&ncols=9&checkHeader=0&singleQuotes=false&columnNames=[ID,%20CAPSULE,%20AGE,%20RACE,%20DPROS,%20DCAPS,%20PSA,%20VOL,%20GLEASON]

    if setup_result['srcs']:
        setupSrcs = "[" + ",".join([src['name'] for src in setup_result['srcs'] ]) + "]"
    else:
        setupSrcs = None
    
    # I suppose we need a way for parameters to parse() to override these
    if setup_result['columnNames']:
        ascii_column_names = "[" + ",".join(setup_result['columnNames']) + "]"
    else:
        ascii_column_names = None


    parse_params = {
        'srcs': setupSrcs,
        'hex': setup_result['hexName'],
        'pType': setup_result['pType'],
        'sep': setup_result['sep'],
        'ncols': setup_result['ncols'],
        'checkHeader': setup_result['checkHeader'],
        'singleQuotes': setup_result['singleQuotes'],
        'columnNames': ascii_column_names,
        # how come these aren't in setup_result?
        'delete_on_done': params_dict['delete_on_done'],
        'blocking': params_dict['blocking'],
    }
    # HACK: if there are too many column names..don't print! it is crazy output
    # just check the output of parse setup. Don't worry about columnNames passed as params here. 
    tooManyColNamesToPrint = setup_result['columnNames'] and len(setup_result['columnNames']) > 2000
    if tooManyColNamesToPrint:
        h2p.yellow_print("Not printing the parameters to Parse because the columnNames are too lengthy.") 
        h2p.yellow_print("See sandbox/commands.log")

    # merge params_dict into parse_params
    # don't want =None to overwrite parse_params
    h2o_methods.check_params_update_kwargs(parse_params, params_dict, 'parse after merge into parse setup', 
        print_params=not tooManyColNamesToPrint, ignoreNone=True)

    print "parse srcs is length:", len(parse_params['srcs'])
    print "parse columnNames is length:", len(parse_params['columnNames'])

    # none of the kwargs passed to here!
    parse_result = self.do_json_request( jsonRequest="Parse.json", cmd='post', postData=parse_params, timeout=timeoutSecs)
    verboseprint("Parse result:", dump_json(parse_result))

    job_key = parse_result['job']['key']['name']
    hex_key = parse_params['hex']

    # TODO: dislike having different shapes for noPoll and poll
    if noPoll:
        # ??
        h2o_sandbox.check_sandbox_for_errors()
        return this.jobs(job_key)

    # does Frame also, while polling
    if intermediateResults:
        key = hex_key
    else:
        key = None

    job_result = self.poll_job(job_key, timeoutSecs=timeoutSecs, key=key)

    if job_result:
        jobs = job_result['jobs'][0]
        description = jobs['description']
        dest = jobs['dest']
        msec = jobs['msec']
        status = jobs['status']
        progress = jobs['progress']
        dest_key = dest['name']

        # can condition this with a parameter if some FAILED are expected by tests.
        if status=='FAILED':
            print dump_json(job_result)
            raise Exception("Taking exception on parse job status: %s %s %s %s %s" % \
                (status, progress, msec, dest_key, description))

        return self.frames(dest_key)
    else:
        # ? we should always get a job_json result
        raise Exception("parse didn't get a job_result when it expected one")
        # return None



# TODO: remove .json
def frames(self, key=None, timeoutSecs=10, **kwargs):
    if not (key is None or isinstance(key, (basestring, Key))):
        raise Exception("frames: key should be string or Key type %s %s" % (type(key), key))

    params_dict = {
        'find_compatible_models': 0,
        'offset': 0, # is offset working yet?
        'len': 5,
    }
    '''
    Return a single Frame or all of the Frames in the h2o cluster.  The
    frames are contained in a list called "frames" at the top level of the
    result.  Currently the list is unordered.
    TODO:
    When find_compatible_models is implemented then the top level 
    dict will also contain a "models" list.
    '''
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'frames', False)
    
    # key can be type Key? (from h2o_xl) str(key) should return
    if key:
        if isinstance(key, Key):
            keyStr = key.frame
        else:
            keyStr = key
        result = self.do_json_request('3/Frames.json/' + keyStr, timeout=timeoutSecs, params=params_dict)
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
def summary(self, key, column="C1", timeoutSecs=10, **kwargs):
    '''
    Return the summary for a single column for a single Frame in the h2o cluster.  
    '''
    params_dict = { 
        'offset': 0,
        'len': 100
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'summary', True)
    
    result = self.do_json_request('3/Frames.json/%s/columns/%s/summary' % (key, column), timeout=timeoutSecs, params=params_dict)
    h2o_sandbox.check_sandbox_for_errors()
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

    if parameters = True, return the parameters?
    '''
    params_dict = {}
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'model_builders', False)

    request = '2/ModelBuilders.json' 
    if algo:
        request += "/" + algo

    result = self.do_json_request(request, timeout=timeoutSecs, params=params_dict)
    # verboseprint(request, "result:", dump_json(result))
    h2o_sandbox.check_sandbox_for_errors()
    return result


def validate_model_parameters(self, algo, training_frame, parameters, timeoutSecs=60, **kwargs):
    '''
    Check a dictionary of model builder parameters on the h2o cluster 
    using the given algorithm and model parameters.
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

        key_name = frames['frames'][0]['key']['name']
        assert key_name==training_frame, \
            "/Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, key_name, training_frame)

        parameters['training_frame'] = training_frame

    # TODO: add parameter existence checks
    # TODO: add parameter value validation

    # FIX! why ignoreH2oError here?
    result = self.do_json_request('/2/ModelBuilders.json/' + algo + "/parameters", cmd='post', 
        timeout=timeoutSecs, postData=parameters, ignoreH2oError=True, noExtraErrorCheck=True)

    verboseprint("model parameters validation: " + repr(result))
    return result


# should training_frame be required? or in parameters. same with destination_key
# because validation_frame is in parameters
def build_model(self, algo, training_frame, parameters, destination_key=None, 
    timeoutSecs=60, noPoll=False, **kwargs):
    '''
    Build a model on the h2o cluster using the given algorithm, training 
    Frame and model parameters.
    '''
    assert algo is not None, '"algo" parameter is null'
    assert training_frame is not None, '"training_frame" parameter is null'
    assert parameters is not None, '"parameters" parameter is null'

    # why always check that the algo is in here?
    model_builders = self.model_builders(timeoutSecs=timeoutSecs)
    assert model_builders is not None, "/ModelBuilders REST call failed"
    assert algo in model_builders['model_builders'], "%s %s" % (algo, [k for k in model_builders['model_builders']])
    builder = model_builders['model_builders'][algo]
    
    # TODO: test this assert, I don't think this is working. . .
    frames = self.frames(key=training_frame)
    assert frames is not None, "/Frames/{0} REST call failed".format(training_frame)

    key_name = frames['frames'][0]['key']['name'] 
    assert key_name==training_frame, \
        "/Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, key_name, training_frame)
    parameters['training_frame'] = training_frame

    if destination_key is not None:
        parameters['destination_key'] = destination_key

    print "build_model parameters", parameters
    start = time.time()
    result1 = self.do_json_request('/2/ModelBuilders.json/' + algo, cmd='post', 
        timeout=timeoutSecs, postData=parameters)

    verboseprint("build_model result", dump_json(result1))

    if noPoll:
        result = result1
    elif 'validation_error_count' in result1:
        h2p.yellow_print("parameter error in model_builders")
        # parameters validation failure
        # TODO: add schema_type and schema_version into all the schemas to make this clean to check
        result = result1
    else:
        job_result = result1['jobs'][0]
        job_key = job_result['key']['name']
        verboseprint("build_model job_key: " + repr(job_key))

        job_result = self.poll_job(job_key, timeoutSecs=timeoutSecs)
        verboseprint(job_result)

        elapsed = time.time() - start
        print "ModelBuilders end on ", training_frame, 'took', time.time() - start, 'seconds'
        print "%d pct. of timeout" % ((elapsed/timeoutSecs) * 100)

        if job_result:
            jobs = job_result['jobs'][0]
            description = jobs['description']
            dest = jobs['dest']
            msec = jobs['msec']
            status = jobs['status']
            progress = jobs['progress']

            # can condition this with a parameter if some FAILED are expected by tests.
            if status=='FAILED':
                print dump_json(job_result)
                raise Exception("Taking exception on build_model job status: %s %s %s %s" % \
                    (status, progress, msec, description))

            result = job_result
        else:
            # ? we should always get a job_json result
            raise Exception("build_model didn't get a job_result when it expected one")
            # return None

    verboseprint("result:", result)
    h2o_sandbox.check_sandbox_for_errors()
    return result


def compute_model_metrics(self, model, frame, timeoutSecs=60, **kwargs):
    '''
    Score a model on the h2o cluster on the given Frame and return only the model metrics. 
    '''
    assert model is not None, '"model" parameter is null'
    assert frame is not None, '"frame" parameter is null'

    models = self.models(key=model, timeoutSecs=timeoutSecs)
    assert models is not None, "/Models REST call failed"
    assert models['models'][0]['key']['name'] == model, "/Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['key']['name'], model)

    # TODO: test this assert, I don't think this is working. . .
    frames = self.frames(key=frame)
    assert frames is not None, "/Frames/{0} REST call failed".format(frame)
    assert frames['frames'][0]['key']['name'] == frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['key']['name'], frame)

    result = self.do_json_request('/3/ModelMetrics.json/models/' + model + '/frames/' + frame, cmd='post', timeout=timeoutSecs)

    mm = result['model_metrics'][0]
    verboseprint("model metrics: " + repr(mm))
    h2o_sandbox.check_sandbox_for_errors()
    return mm


def predict(self, model, frame, timeoutSecs=60, **kwargs):
    assert model is not None, '"model" parameter is null'
    assert frame is not None, '"frame" parameter is null'

    models = self.models(key=model, timeoutSecs=timeoutSecs)
    assert models is not None, "/Models REST call failed"
    assert models['models'][0]['key']['name'] == model, "/Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['key']['name'], model)

    # TODO: test this assert, I don't think this is working. . .
    frames = self.frames(key=frame)
    assert frames is not None, "/Frames/{0} REST call failed".format(frame)
    assert frames['frames'][0]['key']['name'] == frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['key']['name'], frame)

    result = self.do_json_request('/3/Predictions.json/models/' + model + '/frames/' + frame, cmd='post', timeout=timeoutSecs)

    h2o_sandbox.check_sandbox_for_errors()
    return result


def model_metrics(self, timeoutSecs=60, **kwargs):
    '''
    ModelMetrics list. 
    '''
    result = self.do_json_request('/3/ModelMetrics.json', cmd='get', timeout=timeoutSecs)
    h2o_sandbox.check_sandbox_for_errors()
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
        # result = self.do_json_request('3/Models.json', timeout=timeoutSecs, params=params_dict)
        # print "for ray:", dump_json(result)
        result = self.do_json_request('3/Models.json/' + key, timeout=timeoutSecs, params=params_dict)
    else:
        result = self.do_json_request('3/Models.json', timeout=timeoutSecs, params=params_dict)
    
    verboseprint("models result:", dump_json(result))
    h2o_sandbox.check_sandbox_for_errors()
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

    verboseprint("delete_model result:", dump_json(result))
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
