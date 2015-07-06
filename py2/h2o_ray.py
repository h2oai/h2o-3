
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
        # 'job_key': job_key
    }
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'jobs', True)
    result = self.do_json_request('3/Jobs.json', timeout=timeoutSecs, params=params_dict)
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
        result = self.do_json_request('3/Jobs.json/' + job_key, timeout=timeoutSecs, params=params_dict)
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

        # what about 'CREATED'
        # FIX! what are the other legal polling statuses that we should check for?

        if not h2o_args.no_timeout and (time.time() - start_time > timeoutSecs):
            h2o_sandbox.check_sandbox_for_errors()
            emsg = "Job:", job_key, "timed out in:", timeoutSecs

            # for debug
            a = h2o.nodes[0].get_cloud()
            print "cloud.json:", dump_json(a)
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
    a = self.do_json_request('3/ImportFiles.json',
        timeout=timeoutSecs,
        params={"path": path}
    )
    verboseprint("\nimport_files result:", dump_json(a))
    h2o_sandbox.check_sandbox_for_errors()
    return a


# key is required
# FIX! for now h2o doesn't support regex here. just key or list of keys
# FIX! default turn off intermediateResults until NPE is fixed.
def parse(self, key, hex_key=None, columnTypeDict=None,
          timeoutSecs=300, retryDelaySecs=0.2, initialDelaySecs=None, pollTimeoutSecs=180,
          noise=None, benchmarkLogging=None, noPoll=False, intermediateResults=False, **kwargs):
    '''
    Parse an imported raw file or files into a Frame.
    '''
    # these should override what parse setup gets below
    params_dict = {
        'source_frames': None,
        'destination_frame': hex_key, 
        'parse_type': None, # file type 
        'separator': None,
        'single_quotes': None,
        'check_header': None, # forces first line to be seen as column names 
        'number_columns': None,
        'column_names': None, # a list
        'column_types': None, # a list. or can use columnTypeDict param (see below)
        'na_strings' : None, # a list
        'chunk_size': None,
        # are these two no longer supported?
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
        # have to put double quotes around the individual list items (single not legal)
        source_frames = "[" + ",".join(map((lambda x: '"' + x + '"'), key)) + "]"

    else:
        # what if None here
        source_frames = '["' + key + '"]' # quotes required on key

    params_dict['source_frames'] = source_frames

    # merge kwargs into params_dict
    # =None overwrites params_dict

    # columnTypeDict not used here
    h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'parse before setup merge', print_params=False)
    # Call ParseSetup?source_frames=[keys] . . .

    # if benchmarkLogging:
    #     cloudPerfH2O.get_log_save(initOnly=True)

    # h2o_methods.check_params_update_kwargs(params_dict, kwargs, 'parse_setup', print_params=True)
    params_setup = {'source_frames': source_frames}
    setup_result = self.do_json_request(jsonRequest="3/ParseSetup.json", cmd='post', timeout=timeoutSecs, postData=params_setup)
    h2o_sandbox.check_sandbox_for_errors()
    verboseprint("ParseSetup result:", dump_json(setup_result))

    # this should match what we gave as input?
    if setup_result['source_frames']:
        # should these be quoted?
        source_framesStr = "[" + ",".join([('"%s"' % src['name']) for src in setup_result['source_frames'] ]) + "]"
    else:
        source_framesStr = None

    # I suppose we need a way for parameters to parse() to override these
    # should it be an array or a dict?
    if setup_result['column_names']:
        # single quotes not legal..need double quotes
        columnNamesStr = "[" + ",".join(map((lambda x: '"' + x + '"'), setup_result['column_names'])) + "]"
    else:
        columnNamesStr = None

    columnTypes = setup_result['column_types']
    assert columnTypes is not None, "%s %s" % ("column_types:", columnTypes)

    if setup_result['na_strings']:
        # single quotes not legal..need double quotes
        naStrings = "[" + ",".join(map((lambda x: '"' + x + '"' if x != None else '""'), setup_result['na_strings'])) + "]"
    else:
        naStrings = None

    # dict parameter to update columnTypeDict?
    # but we don't pass columnNames like this?
    ct = setup_result['column_types']
    columnNames = setup_result['column_names']
    if columnTypeDict: 
        for k,v in columnTypeDict.iteritems():
            if isinstance(k, int):
                # if a column index
                if k>=0 and k<len(ct):
                    ct[k] = v
                else:
                    raise Exception("bad col index %s in columnTypeDict param %s" % (k, columnTypeDict))
            # if a column name
            elif isinstance(k, basestring):
                # find the index
                if k not in columnNames:
                    raise Exception("bad col name %s in columnTypeDict param %s. columnNames: %s" % (k, columnTypeDict, columnNames))
                ci = columnNames.index(k)
                ct[ci] = v
            else:
                raise Exception("%s %s should be int or string" % (k, type(k)))

    columnTypesStr = "[" + ",".join(map((lambda x: '"' + x + '"'), ct)) + "]"


    parse_params = {
        'source_frames': source_framesStr,
        'destination_frame': setup_result['destination_frame'],
        'parse_type': setup_result['parse_type'],
        'separator': setup_result['separator'],
        'single_quotes': setup_result['single_quotes'],
        'check_header': setup_result['check_header'],
        'number_columns': setup_result['number_columns'],
        'column_names': columnNamesStr,
        'column_types': columnTypesStr,
        'na_strings': naStrings, 
        'chunk_size': setup_result['chunk_size'],
        # No longer supported? how come these aren't in setup_result?
        'delete_on_done': params_dict['delete_on_done'],
        'blocking': params_dict['blocking'],
    }
    # HACK: if there are too many column names..don't print! it is crazy output
    # just check the output of parse setup. Don't worry about columnNames passed as params here. 
    tooManyColNamesToPrint = setup_result['column_names'] and len(setup_result['column_names']) > 2000
    if tooManyColNamesToPrint:
        h2p.yellow_print("Not printing the parameters to Parse because the columnNames are too lengthy.") 
        h2p.yellow_print("See sandbox/commands.log")

    # merge params_dict into parse_params
    # don't want =None to overwrite parse_params
    h2o_methods.check_params_update_kwargs(parse_params, params_dict, 'parse after merge into parse setup', 
        print_params=not tooManyColNamesToPrint, ignoreNone=True)

    print "parse source_frames is length:", len(parse_params['source_frames'])
    # This can be null now? parseSetup doesn't return default colnames?
    # print "parse column_names is length:", len(parse_params['column_names'])

    # none of the kwargs passed to here!
    parse_result = self.do_json_request( jsonRequest="3/Parse.json", cmd='post', postData=parse_params, timeout=timeoutSecs)
    verboseprint("Parse result:", dump_json(parse_result))

    job_key = parse_result['job']['key']['name']
    hex_key = parse_params['destination_frame']

    # TODO: dislike having different shapes for noPoll and poll
    if noPoll:
        # ??
        h2o_sandbox.check_sandbox_for_errors()
        # return self.jobs(job_key)
        return parse_result

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
def frames(self, key=None, timeoutSecs=60, **kwargs):
    if not (key is None or isinstance(key, (basestring, Key))):
        raise Exception("frames: key should be string or Key type %s %s" % (type(key), key))

    params_dict = {
        'find_compatible_models': 0,
        'row_offset': 0, # is offset working yet?
        'row_count': 5,
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
        # 'offset': 0,
        # 'len': 100
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

    request = '3/ModelBuilders.json' 
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
    result = self.do_json_request('/3/ModelBuilders.json/' + algo + "/parameters", cmd='post', 
        timeout=timeoutSecs, postData=parameters, ignoreH2oError=True, noExtraErrorCheck=True)

    verboseprint("model parameters validation: " + repr(result))
    return result


# should training_frame be required? or in parameters. same with destination_frame
# because validation_frame is in parameters
# destination_frame is old, model_id is new?
def build_model(self, algo, training_frame, parameters, destination_frame=None, model_id=None,
    timeoutSecs=60, noPoll=False, **kwargs):

    if 'destination_key' in kwargs:
        raise Exception('Change destination_key in build_model() to model_id')

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

    key_name = frames['frames'][0]['frame_id']['name'] 
    assert key_name==training_frame, \
        "/Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, key_name, training_frame)
    parameters['training_frame'] = training_frame

    if destination_frame is not None:
        print "destination_frame should be replaced by model_id now"
        parameters['model_id'] = destination_frame

    if model_id is not None:
        parameters['model_id'] = model_id

    print "build_model parameters", parameters
    start = time.time()
    result1 = self.do_json_request('/3/ModelBuilders.json/' + algo, cmd='post', 
        timeout=timeoutSecs, postData=parameters)
    # make get overwritten after polling
    elapsed = time.time() - start
    verboseprint("build_model result", dump_json(result1))
      
    if noPoll:
        result = result1
    elif ('validation_error_count' in result1) and (result1['validation_error_count']>0):
        h2p.yellow_print("parameter error in model_builders: %s" % result1)
        # parameters validation failure
        # TODO: add schema_type and schema_version into all the schemas to make this clean to check
        result = result1
        # don't bother printing a time message
    elif 'exception_msg' in result1:
        h2p.yellow_print("exception msg in model_builders: %s" % result1['exception_msg'])
        result = result1
    else:
        job_result = result1['job']
        job_key = job_result['key']['name']
        verboseprint("build_model job_key: " + repr(job_key))

        job_result = self.poll_job(job_key, timeoutSecs=timeoutSecs)
        verboseprint(job_result)

        elapsed = time.time() - start
        print "ModelBuilders", algo,  "end on", training_frame, 'took', time.time() - start, 'seconds'
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
    result['python_elapsed'] = elapsed
    return result


def compute_model_metrics(self, model, frame, timeoutSecs=60, **kwargs):
    '''
    Score a model on the h2o cluster on the given Frame and return only the model metrics. 
    '''
    assert model is not None, '"model" parameter is null'
    assert frame is not None, '"frame" parameter is null'

    models = self.models(key=model, timeoutSecs=timeoutSecs)
    assert models is not None, "/Models REST call failed"
    assert models['models'][0]['model_id']['name'] == model, "/Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['key']['name'], model)

    # TODO: test this assert, I don't think this is working. . .
    frames = self.frames(key=frame)
    assert frames is not None, "/Frames/{0} REST call failed".format(frame)
    
    print "frames:", dump_json(frames)
    # is the name not there?
    # assert frames['frames'][0]['model_id']['name'] == frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, models['models'][0]['key']['name'], frame)

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

    # FIX! what is right here now?
    # assert models['models'][0]['key']['name'] == model, "/Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['key']['name'], model)

    # TODO: test this assert, I don't think this is working. . .
    frames = self.frames(key=frame)
    assert frames is not None, "/Frames/{0} REST call failed".format(frame)

    # FIX! what is right here now?
    # assert frames['frames'][0]['key']['name'] == frame, "/Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['key']['name'], frame)

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
    result = self.do_json_request('/3/Metadata/endpoints.json', cmd='get', timeout=timeoutSecs)
    return result

def endpoint_by_number(self, num, timeoutSecs=60, **kwargs):
    '''
    Fetch the metadata for the given numbered REST API endpoint.
    '''
    parameters = { }
    result = self.do_json_request('/3/Metadata/endpoints.json/' + str(num), cmd='get', timeout=timeoutSecs)
    return result


def schemas(self, timeoutSecs=60, **kwargs):
    '''
    Fetch the list of REST API schemas.
    '''
    parameters = { }
    result = self.__do_json_request('/3/Metadata/schemas.json', cmd='get', timeout=timeoutSecs)

    return result

def schema(self, schemaname, timeoutSecs=60, **kwargs):
    '''
    Fetch the metadata for the given named REST API schema (e.g., FrameV2).
    '''
    parameters = { }
    result = self.__do_json_request('/3/Metadata/schemas.json/' + schemaname, cmd='get', timeout=timeoutSecs)

    return result

