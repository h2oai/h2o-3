import sys, os, getpass, logging, time, inspect, requests, json
import h2o_test_utils
from h2o_test_utils import log, log_rest
import h2o_print as h2p

class H2O(object):
    # static (class) variables
    ipaddr_from_cmd_line = None
    debugger = False
    json_url_history = []
    python_test_name = inspect.stack()[1][1]
    verbose = False
    experimental_algos = ["pca", "svd", "glrm"]

    ## TODO: support api_version parameter for all api calls!
    # Also a global in the H2O object set at creation time.

    # TODO: ensure that all of this is really necessary:
    def __init__(self,
                 use_this_ip_addr=None, port=54321, capture_output=True,
                 use_debugger=None, classpath=None,
                 use_hdfs=False, use_maprfs=False,
                 # hdfs_version="cdh4", hdfs_name_node="192.168.1.151",
                 # hdfs_version="cdh3", hdfs_name_node="192.168.1.176",
                 hdfs_version=None, hdfs_name_node=None, hdfs_config=None,
                 aws_credentials=None,
                 use_flatfile=False, java_heap_GB=None, java_heap_MB=None, java_extra_args=None,
                 use_home_for_ice=False, node_id=None, username=None,
                 random_udp_drop=False,
                 redirect_import_folder_to_s3_path=None,
                 redirect_import_folder_to_s3n_path=None,
                 disable_h2o_log=False,
                 enable_benchmark_log=False,
                 h2o_remote_buckets_root=None,
                 delete_keys_at_teardown=False,
                 cloud_name=None,
    ):

        if use_hdfs:
            # see if we can touch a 0xdata machine
            try:
                # long timeout in ec2...bad
                a = requests.get('http://192.168.1.176:80', timeout=1)
                hdfs_0xdata_visible = True
            except:
                hdfs_0xdata_visible = False

            # different defaults, depending on where we're running
            if hdfs_name_node is None:
                if hdfs_0xdata_visible:
                    hdfs_name_node = "192.168.1.176"
                else: # ec2
                    hdfs_name_node = "10.78.14.235:9000"

            if hdfs_version is None:
                if hdfs_0xdata_visible:
                    hdfs_version = "cdh3"
                else: # ec2
                    hdfs_version = "0.20.2"

        self.redirect_import_folder_to_s3_path = redirect_import_folder_to_s3_path
        self.redirect_import_folder_to_s3n_path = redirect_import_folder_to_s3n_path

        self.aws_credentials = aws_credentials
        self.port = port
        # None is legal for self.addr.
        # means we won't give an ip to the jar when we start.
        # Or we can say use use_this_ip_addr=127.0.0.1, or the known address
        # if use_this_addr is None, use 127.0.0.1 for urls and json
        # Command line arg 'ipaddr_from_cmd_line' dominates:
        if H2O.ipaddr_from_cmd_line:
            self.addr = H2O.ipaddr_from_cmd_line
        else:
            self.addr = use_this_ip_addr

        if self.addr is not None:
            self.http_addr = self.addr
        else:
            self.http_addr = get_ip_address()

        # command line should always dominate for enabling
        if H2O.debugger: use_debugger = True
        self.use_debugger = use_debugger

        self.classpath = classpath
        self.capture_output = capture_output

        self.use_hdfs = use_hdfs
        self.use_maprfs = use_maprfs
        self.hdfs_name_node = hdfs_name_node
        self.hdfs_version = hdfs_version
        self.hdfs_config = hdfs_config

        self.use_flatfile = use_flatfile
        self.java_heap_GB = java_heap_GB
        self.java_heap_MB = java_heap_MB
        self.java_extra_args = java_extra_args

        self.use_home_for_ice = use_home_for_ice
        self.node_id = node_id

        if username:
            self.username = username
        else:
            self.username = getpass.getuser()

        # don't want multiple reports from tearDown and tearDownClass
        # have nodes[0] remember (0 always exists)
        self.sandbox_error_was_reported = False
        self.sandbox_ignore_errors = False

        self.random_udp_drop = random_udp_drop
        self.disable_h2o_log = disable_h2o_log

        # this dumps stats from tests, and perf stats while polling to benchmark.log
        self.enable_benchmark_log = enable_benchmark_log
        self.h2o_remote_buckets_root = h2o_remote_buckets_root
        self.delete_keys_at_teardown = delete_keys_at_teardown

        if cloud_name:
            self.cloud_name = cloud_name
        else:
            self.cloud_name = 'pytest-%s-%s' % (getpass.getuser(), os.getpid())


    ''' 
    Printable string representation of an H2O node object. 
    '''
    def __str__(self):
        return '%s - http://%s:%d/' % (type(self), self.http_addr, self.port)


    # TODO: UGH, move this.
    @staticmethod
    def verboseprint(*args, **kwargs):
        if H2O.verbose:
            for x in args: # so you don't have to create a single string
                print x,
            for x in kwargs: # so you don't have to create a single string
                print x,
            print
            sys.stdout.flush()


    def __url(self, loc, port=None):
        # always use the new api port
        if port is None: port = self.port
        if loc.startswith('/'):
            delim = ''
        else:
            delim = '/'
        u = 'http://%s:%d%s%s' % (self.http_addr, port, delim, loc)
        return u


    '''
    Make a REST request to the h2o server and if succesful return a dict containing the JSON result.
    '''
#    @profile
    def __do_json_request(self, jsonRequest=None, fullUrl=None, timeout=10, params=None, postData=None, returnFast=False,
                          cmd='get', extraComment=None, ignoreH2oError=False, noExtraErrorCheck=False, raiseIfNon200=True, **kwargs):
        H2O.verboseprint("__do_json_request, timeout: " + str(timeout))
        # if url param is used, use it as full url. otherwise crate from the jsonRequest
        if fullUrl:
            url = fullUrl
        else:
            url = self.__url(jsonRequest)

        # remove any params that are 'None'
        # need to copy dictionary, since can't delete while iterating
        if params is not None:
            params_serialized = params.copy()
            for k in params_serialized:
                if params_serialized[k] is None:
                    del params[k]
            paramsStr = '?' + '&'.join(['%s=%s' % (k, v) for (k, v) in params.items()])
        else:
            paramsStr = ''

        # The requests package takes array parameters and explodes them: ['f00', 'b4r'] becomes "f00,b4r".
        # NOTE: this handles 1D arrays only; if we need ND this needs to be recursive.
        # NOTE: we currently don't need to do this for GET, so that's not implemented.
        if postData is not None:
            munged_postData = {}
            for k, v in postData.iteritems():
                if type(v) is list:
                    if len(v) == 0:
                        munged_postData[k] = '[]'
                    else:
                        first = True
                        array_str = '['
                        for val in v:
                            if not first: array_str += ', '

                            if val is None:
                                array_str += 'null'
                            elif isinstance(val, basestring):
                                array_str += "\"" + str(val) + "\""
                            else:
                                array_str += str(val)
                            first  = False
                        array_str += ']'
                        munged_postData[k] = array_str
                elif type(v) is dict:
                    if len(v) == 0:
                        munged_postData[k] = '{}'
                    else:
                        first = True
                        map_str = '{'
                        print("v: " + repr(v))
                        for key, val in v.iteritems():
                            if not first: map_str += ', '

                            if val is None:
                                map_str += "\"" + key + "\"" + ': null'
                            elif isinstance(val, basestring):
                                map_str += "\"" + str(key) + "\"" + ":" + "\"" + str(val) + "\""
                            else:
                                map_str += "\"" + key + "\"" + ':' + str(val)
                            first  = False
                        map_str += '}'
                        munged_postData[k] = map_str

                else:
                    # not list:
                    munged_postData[k] = v
        else:  
            # None
            munged_postData = postData

        # print("munged_postData: " + repr(munged_postData))

        if extraComment:
            log('Start ' + url + paramsStr, comment=extraComment)
        else:
            log('Start ' + url + paramsStr)

        log_rest("")
        log_rest("----------------------------------------------------------------------\n")
        if extraComment:
            log_rest("# Extra comment info about this request: " + extraComment)
        if cmd == 'get':
            log_rest("GET")
        else:
            log_rest("POST")
        log_rest(url + paramsStr)

        # file get passed thru kwargs here
        try:
            if 'post' == cmd:
                # NOTE == cmd: for now, since we don't have deserialization from JSON in h2o-dev, we use form-encoded POST.
                # This is temporary.
                # 
                # This following does application/json (aka, posting JSON in the body):
                # r = requests.post(url, timeout=timeout, params=params, data=json.dumps(munged_postData), **kwargs)
                # 
                # This does form-encoded, which doesn't allow POST of nested structures
                r = requests.post(url, timeout=timeout, params=params, data=munged_postData, **kwargs)
            elif 'delete' == cmd:
                r = requests.delete(url, timeout=timeout, params=params, **kwargs)                
            elif 'get' == cmd:
                r = requests.get(url, timeout=timeout, params=params, **kwargs)
            else:
                raise ValueError("Unknown HTTP command (expected 'get', 'post' or 'delete'): " + cmd)

        except Exception, e:
            # rethrow the exception after we've checked for stack trace from h2o
            # out of memory errors maybe don't show up right away? so we should wait for h2o
            # to get it out to h2o stdout. We don't want to rely on cloud teardown to check
            # because there's no delay, and we don't want to delay all cloud teardowns by waiting.
            # (this is new/experimental)
            exc_info = sys.exc_info()
            # use this to ignore the initial connection errors during build cloud when h2o is coming up
            if not noExtraErrorCheck: 
                h2p.red_print(
                    "ERROR: got exception on %s to h2o. \nGoing to check sandbox, then rethrow.." % (url + paramsStr))
                time.sleep(2)
                H2O.check_sandbox_for_errors(python_test_name=H2O.python_test_name);
            log_rest("")
            log_rest("EXCEPTION CAUGHT DOING REQUEST: " + str(e.message))
            raise exc_info[1], None, exc_info[2]

            H2O.verboseprint("r: " + repr(r))

        if raiseIfNon200 and 200 != r.status_code:
            print "JSON call returned non-200 status: ", url
            print "r.status_code: " + str(r.status_code)
            print "r.headers: " + repr(r.headers)
            print "r.text: " + r.text

        log_rest("")
        try:
            if r is None:
                log_rest("r is None")
            else:
                log_rest("HTTP status code: " + str(r.status_code))
                # The following accesses to r.text were taking most of the runtime:
                log_text = False
                if log_text:
                    if hasattr(r, 'text'):
                        if r.text is None:
                            log_rest("r.text is None")
                        else:
                            log_rest(r.text)
                    else:
                        log_rest("r does not have attr text")
        except Exception, e:
            # Paranoid exception catch.  
            # Ignore logging exceptions in the case that the above error checking isn't sufficient.
            print "Caught exception from result logging: ", e, "; result: ", repr(r)

        # fatal if no response
        if raiseIfNon200 and not r:
            raise Exception("Maybe bad url? no r in __do_json_request in %s:" % inspect.stack()[1][3])

        # this is used to open a browser on results, or to redo the operation in the browser
        # we don't' have that may urls flying around, so let's keep them all
        H2O.json_url_history.append(r.url)
        # if r.json():
        #     raise Exception("Maybe bad url? no r.json in __do_json_request in %s:" % inspect.stack()[1][3])

        rjson = None
        if returnFast:
            return
        try:
            rjson = r.json()
        except:
            print h2o_test_utils.dump_json(r.text)
            if not isinstance(r, (list, dict)):
                raise Exception("h2o json responses should always be lists or dicts, see previous for text")

            raise Exception("Could not decode any json from the request.")

        # TODO
        # TODO
        # TODO
        # TODO: we should really only look in the response object.  This check
        # prevents us from having a field called "error" (e.g., for a scoring result).
        for e in ['error', 'Error', 'errors', 'Errors']:
            # error can be null (python None). This happens in exec2
            if e in rjson and rjson[e]:
                H2O.verboseprint("rjson:" + h2o_test_utils.dump_json(rjson))
                emsg = 'rjson %s in %s: %s' % (e, inspect.stack()[1][3], rjson[e])
                if ignoreH2oError:
                    # well, we print it..so not totally ignore. test can look at rjson returned
                    print emsg
                else:
                    print emsg
                    raise Exception(emsg)

        for w in ['warning', 'Warning', 'warnings', 'Warnings']:
            # warning can be null (python None).
            if w in rjson and rjson[w]:
                H2O.verboseprint(dump_json(rjson))
                print 'rjson %s in %s: %s' % (w, inspect.stack()[1][3], rjson[w])

        
        # Allow the caller to check things like __http_request.status_code.
        # The response object is not JSON-serializable, so we capture the fields we want here:
        response = {}
        # response['headers'] = r.headers
        response['url'] = r.url
        response['status_code'] = r.status_code
        response['text'] = r.text
        rjson['__http_response'] = response

        return rjson
        # end of __do_json_request


    ''' 
    Check the output for errors.  Note: false positives are possible; a whitelist is available.
    '''
    @staticmethod
    def check_sandbox_for_errors(cloudShutdownIsError=False, sandboxIgnoreErrors=False, python_test_name=''):
        # TODO: nothing right now
        return
        # dont' have both tearDown and tearDownClass report the same found error
        # only need the first
        if nodes and nodes[0].sandbox_error_report(): # gets current state
            return

        # Can build a cloud that ignores all sandbox things that normally fatal the test
        # Kludge, test will set this directly if it wants, rather than thru build_cloud parameter.
        # we need the sandbox_ignore_errors, for the test teardown_cloud..the state disappears!
        ignore = sandboxIgnoreErrors or (nodes and nodes[0].sandbox_ignore_errors)
        errorFound = h2o_sandbox.check_sandbox_for_errors(
            LOG_DIR=LOG_DIR,
            sandboxIgnoreErrors=ignore,
            cloudShutdownIsError=cloudShutdownIsError,
            python_test_name=python_test_name)

        if errorFound and nodes:
            nodes[0].sandbox_error_report(True) # sets


    ###################
    # REST API ACCESSORS

    '''
    Fetch all the jobs or a single job from the /Jobs endpoint.
    '''
    def jobs(self, job_key=None, timeoutSecs=10, **kwargs):
        params_dict = {
            'job_key': job_key
        }
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'jobs', H2O.verbose)
        result = self.__do_json_request('/3/Jobs', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Poll a single job from the /Jobs endpoint until it is "status": "DONE" or "CANCELLED" or "FAILED" or we time out.
    '''
    # TODO: add delays, etc.
    def poll_job(self, job_key, timeoutSecs=10, retryDelaySecs=0.5, **kwargs):
        params_dict = {
        }
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'poll_job', H2O.verbose)

        start_time = time.time()
        while True:
            H2O.verboseprint('Polling for job: ' + job_key + '. . .')
            result = self.__do_json_request('/3/Jobs/' + job_key, timeout=timeoutSecs, params=params_dict)
            
            status = result['jobs'][0]['status']
            if status == 'DONE' or status == 'CANCELLED' or status == 'FAILED':
                H2O.verboseprint('Job ' + status + ': ' + job_key + '.')
                return result

            if time.time() - start_time > timeoutSecs:
                print 'Job: ' + job_key + ' timed out in: ' + str(timeoutSecs) + '.'
                # downstream checkers should tolerate None. Print msg in case it's overlooked.
                return None

            time.sleep(retryDelaySecs)


    ''' 
    Create a Frame.
    '''
    def create_frame(self, timeoutSecs=180, **kwargs):
        a = self.__do_json_request('3/CreateFrame', cmd="post",
            timeout=timeoutSecs,
            params=kwargs
        )
        H2O.verboseprint("\ncreate_frame result:", h2o_test_utils.dump_json(a))
        return a


    ''' 
    Split a Frame.
    '''
    def split_frame(self, timeoutSecs=180, **kwargs):
        a = self.__do_json_request('/3/SplitFrame', cmd="post",
            timeout=timeoutSecs,
            postData=kwargs
        )
        job_json = self.poll_job(a["key"]["name"], timeoutSecs=timeoutSecs)
        H2O.verboseprint("\nsplit_frame result:", h2o_test_utils.dump_json(a))
        return a

    '''
    Create interactions.
    '''
    def interaction(self, timeoutSecs=180, **kwargs):
        a = self.__do_json_request('/3/Interaction', cmd="post",
                                   timeout=timeoutSecs,
                                   postData=kwargs
        )
        H2O.verboseprint("\ninteraction result:", h2o_test_utils.dump_json(a))
        return a

    ''' 
    Import a file or files into h2o.  The 'file' parameter accepts a directory or a single file.
    192.168.0.37:54323/ImportFiles.html?file=%2Fhome%2F0xdiag%2Fdatasets
    '''
    def import_files(self, path, timeoutSecs=180):
        a = self.__do_json_request('/3/ImportFiles',
            timeout=timeoutSecs,
            params={"path": path}
        )
        H2O.verboseprint("\nimport_files result:", h2o_test_utils.dump_json(a))
        return a


    '''
    Parse an imported raw file or files into a Frame.
    '''
    def parse(self, key, dest_key=None,
              timeoutSecs=300, retryDelaySecs=0.2, initialDelaySecs=None, pollTimeoutSecs=180,
              noise=None, benchmarkLogging=None, noPoll=False, **kwargs):

        #
        # Call ParseSetup?source_frames=[keys] . . .
        #

        if benchmarkLogging:
            cloudPerfH2O.get_log_save(initOnly=True)

        # TODO: multiple keys
        parse_setup_params = {
            'source_frames': '["' + key + '"]'  # NOTE: quote key names
        }
        # h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'parse_setup', print_params=H2O.verbose)
        setup_result = self.__do_json_request(jsonRequest="/3/ParseSetup", cmd='post', timeout=timeoutSecs, postData=parse_setup_params)
        H2O.verboseprint("ParseSetup result:", h2o_test_utils.dump_json(setup_result))

        # 
        # and then Parse?source_frames=<keys list> and params from the ParseSetup result
        # Parse?source_frames=[nfs://Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv]&destination_frame=prostate.hex&parse_type=CSV&separator=44&number_columns=9&check_header=0&single_quotes=false&column_names=['ID',CAPSULE','AGE','RACE','DPROS','DCAPS','PSA','VOL','GLEASON]
        #

        parse_params = {
            'source_frames': '["' + setup_result['source_frames'][0]['name'] + '"]', # TODO: cons up the whole list
            'destination_frame': dest_key if dest_key else setup_result['destination_frame'],
            'parse_type': setup_result['parse_type'],
            'separator': setup_result['separator'],
            'single_quotes': setup_result['single_quotes'],
            'check_header': setup_result['check_header'],
            'number_columns': setup_result['number_columns'],
            'column_names': setup_result['column_names'], # gets stringified inside __do_json_request()
            'column_types': setup_result['column_types'], # gets stringified inside __do_json_request()
	    'na_strings': setup_result['na_strings'],
            'chunk_size': setup_result['chunk_size'],
        }
        H2O.verboseprint("parse_params: " + repr(parse_params))
        h2o_test_utils.check_params_update_kwargs(parse_params, kwargs, 'parse', print_params=H2O.verbose)

        parse_result = self.__do_json_request(jsonRequest="/3/Parse", cmd='post', timeout=timeoutSecs, postData=parse_params, **kwargs)
        H2O.verboseprint("Parse result:", h2o_test_utils.dump_json(parse_result))

        # print("Parse result:", repr(parse_result))
        job_key = parse_result['job']['key']['name']

        # TODO: dislike having different shapes for noPoll and poll
        if noPoll:
            return this.jobs(job_key)

        job_json = self.poll_job(job_key, timeoutSecs=timeoutSecs)

        if job_json:
            dest_key = job_json['jobs'][0]['dest']['name']
            return self.frames(dest_key)

        return None


    '''
    Return a single Frame or all of the Frames in the h2o cluster.  The
    frames are contained in a list called "frames" at the top level of the
    result.  Currently the list is unordered.
    TODO:
    When find_compatible_models is implemented then the top level 
    dict will also contain a "models" list.
    '''
    def frames(self, key=None, timeoutSecs=10, **kwargs):
        params_dict = {
            'find_compatible_models': 0,
            'row_offset': 0,
            'row_count': 100
        }
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'frames', H2O.verbose)
        
        if key:
            result = self.__do_json_request('/3/Frames/' + key, timeout=timeoutSecs, params=params_dict)
        else:
            result = self.__do_json_request('/3/Frames', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Return the columns for a single Frame in the h2o cluster.  
    '''
    def columns(self, key, timeoutSecs=10, **kwargs):
        params_dict = { 
            'row_offset': 0,
            'row_count': 100
        }
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'columns', H2O.verbose)
        
        result = self.__do_json_request('/3/Frames/' + key + '/columns', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Return a single column for a single Frame in the h2o cluster.  
    '''
    def column(self, key, column, timeoutSecs=10, **kwargs):
        params_dict = { 
            'row_offset': 0,
            'row_count': 100
        }
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'column', H2O.verbose)
        
        result = self.__do_json_request('/3/Frames/' + key + '/columns/' + column, timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Return the summary for a single column for a single Frame in the h2o cluster.  
    '''
    def summary(self, key, column, timeoutSecs=10, **kwargs):
        params_dict = { 
            'row_offset': 0,
            'row_count': 100
        }
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'summary', H2O.verbose)
        
        result = self.__do_json_request('/3/Frames/' + key + '/columns/' + column + '/summary', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Delete a frame on the h2o cluster, given its key.
    '''
    def delete_frame(self, key, ignoreMissingKey=True, timeoutSecs=60, **kwargs):
        assert key is not None, 'FAIL: "key" parameter is null'

        result = self.__do_json_request('/3/Frames/' + key, cmd='delete', timeout=timeoutSecs)

        # TODO: look for what?
        if not ignoreMissingKey and 'f00b4r' in result:
            raise ValueError('Frame key not found: ' + key)

        return result


    '''
    Delete all frames on the h2o cluster.
    '''
    def delete_frames(self, timeoutSecs=60, **kwargs):
        parameters = { }
        result = self.__do_json_request('/3/Frames', cmd='delete', timeout=timeoutSecs)

        return result


    '''
    Return a model builder or all of the model builders known to the
    h2o cluster.  The model builders are contained in a dictionary
    called "model_builders" at the top level of the result.  The
    dictionary maps algorithm names to parameters lists.  Each of the
    parameters contains all the metdata required by a client to
    present a model building interface to the user.
    '''
    def model_builders(self, algo=None, timeoutSecs=10, **kwargs):
        params_dict = {
        }
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'model_builders', H2O.verbose)

        if algo:
            if algo in H2O.experimental_algos:
               _rest_version = 99
            else:
              _rest_version = 3
            result = self.__do_json_request(str(_rest_version)+'/ModelBuilders/' + algo, timeout=timeoutSecs, params=params_dict)
        else:
            result = self.__do_json_request('3/ModelBuilders', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Check a dictionary of model builder parameters on the h2o cluster using the given algorithm and model parameters.
    '''
    def validate_model_parameters(self, algo, training_frame, parameters, timeoutSecs=60, **kwargs):
        assert algo is not None, 'FAIL: "algo" parameter is null'
        # Allow this now: assert training_frame is not None, '"training_frame" parameter is null'
        assert parameters is not None, 'FAIL: "parameters" parameter is null'

        model_builders = self.model_builders(timeoutSecs=timeoutSecs)
        assert model_builders is not None, "FAIL: /ModelBuilders REST call failed"
        assert algo in model_builders['model_builders'], "FAIL: algo " + algo + " not found in model_builders list: " + repr(model_builders)
        builder = model_builders['model_builders'][algo]
        
        # TODO: test this assert, I don't think this is working. . .
        if training_frame is not None:
            frames = self.frames(key=training_frame)
            assert frames is not None, "FAIL: /Frames/{0} REST call failed".format(training_frame)
            assert frames['frames'][0]['frame_id']['name'] == training_frame, "FAIL: /Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, frames['frames'][0]['frame_id']['name'], training_frame)
            parameters['training_frame'] = training_frame

        # TODO: add parameter existence checks
        # TODO: add parameter value validation
        if algo in H2O.experimental_algos:
          _rest_version = 99
        else:
          _rest_version = 3
        result = self.__do_json_request('/' + str(_rest_version) + '/ModelBuilders/' + algo + "/parameters", cmd='post', timeout=timeoutSecs, postData=parameters, ignoreH2oError=True, noExtraErrorCheck=True, raiseIfNon200=False)  # NOTE: DO NOT die if validation errors 

        H2O.verboseprint("model parameters validation: " + repr(result))
        return result


    '''
    Build a model on the h2o cluster using the given algorithm, training 
    Frame and model parameters.
    '''
    def build_model(self, algo, training_frame, parameters, model_id = None, timeoutSecs=60, asynchronous=False, **kwargs):
        # basic parameter checking
        assert algo is not None, 'FAIL: "algo" parameter is null'
        assert training_frame is not None, 'FAIL: "training_frame" parameter is null'
        assert parameters is not None, 'FAIL: "parameters" parameter is null'

        # check that algo is known (TODO: remove after testing that error from POST is good enough)
        model_builders = self.model_builders(timeoutSecs=timeoutSecs)
        assert model_builders is not None, "FAIL: /ModelBuilders REST call failed"
        assert algo in model_builders['model_builders'], "FAIL: failed to find algo " + algo + " in model_builders list: " + repr(model_builders)
        builder = model_builders['model_builders'][algo]
        
        # TODO: test this assert, I don't think this is working. . .
        # Check for frame:
        frames = self.frames(key=training_frame)
        assert frames is not None, "FAIL: /Frames/{0} REST call failed".format(training_frame)
        assert frames['frames'][0]['frame_id']['name'] == training_frame, "FAIL: /Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, frames['frames'][0]['frame_id']['name'], training_frame)
        parameters['training_frame'] = training_frame

        if model_id is not None:
            parameters['model_id'] = model_id
        result = self.__do_json_request('/3/ModelBuilders/' + algo, cmd='post', timeout=timeoutSecs, postData=parameters, raiseIfNon200=False)  # NOTE: DO NOT die if validation errors

        if asynchronous:
            return result
        elif 'error_count' in result and result['error_count'] > 0:
            # parameters validation failure
            return result
        elif result['__http_response']['status_code'] != 200:
            return result
        else:
            assert 'job' in result, "FAIL: did not find job key in model build result: " + repr(result)
            job = result['job']
            job_key = job['key']['name']
            H2O.verboseprint("model building job_key: " + repr(job_key))
            job_json = self.poll_job(job_key, timeoutSecs=timeoutSecs)
            return result


    '''
    Build a Cartesian grid of models on the h2o cluster using the given algorithm, training 
    Frame, model parameters and grid parameters.
    '''
    def build_model_grid(self, algo, training_frame, parameters, grid_parameters, grid_id = None, timeoutSecs=60, asynchronous=False, **kwargs):
        # basic parameter checking
        assert algo is not None, 'FAIL: "algo" parameter is null'
        assert training_frame is not None, 'FAIL: "training_frame" parameter is null'
        assert parameters is not None, 'FAIL: "parameters" parameter is null'
        assert grid_parameters is not None, 'FAIL: "grid_parameters" parameter is null'

        # check that algo is known (TODO: remove after testing that error from POST is good enough)
        model_builders = self.model_builders(timeoutSecs=timeoutSecs)
        assert model_builders is not None, "FAIL: /ModelBuilders REST call failed"
        assert algo in model_builders['model_builders'], "FAIL: failed to find algo " + algo + " in model_builders list: " + repr(model_builders)
        builder = model_builders['model_builders'][algo]
        
        # TODO: test this assert, I don't think this is working. . .
        # Check for frame:
        frames = self.frames(key=training_frame)
        assert frames is not None, "FAIL: /Frames/{0} REST call failed".format(training_frame)
        assert frames['frames'][0]['frame_id']['name'] == training_frame, "FAIL: /Frames/{0} returned Frame {1} rather than Frame {2}".format(training_frame, frames['frames'][0]['frame_id']['name'], training_frame)
        parameters['training_frame'] = training_frame

        # UGH: grid parameters are totally non-standard; the model parameters are mixed with grid_id and hyper_parameters.  See GridSearchSchema.fillFromParms().
        post_parameters = {}
        post_parameters.update(parameters)
        post_parameters['hyper_parameters'] = grid_parameters
        # gridParams['grid_parameters'] = json.dumps(hyperParameters)

        print("post_parameters: " + repr(post_parameters))

        if grid_id is not None:
            post_parameters['grid_id'] = grid_id

        result = self.__do_json_request('/99/Grid/' + algo, cmd='post', timeout=timeoutSecs, postData=post_parameters, raiseIfNon200=False)  # NOTE: DO NOT die if validation errors
        if result['__meta']['schema_type'] == 'H2OError':
            print("ERROR: building model grid: " + grid_id)
            print(" reason: " + result['dev_msg'])
            print(" stacktrace: " + "\n ".join(result['stacktrace']))
            raise ValueError("ERROR: building model grid: " + grid_id + ";  reason: " + result['dev_msg'])

        if asynchronous:
            return result
        elif 'error_count' in result and result['error_count'] > 0:
            # parameters validation failure
            return result
        elif result['__http_response']['status_code'] != 200:
            return result
        else:
            assert 'job' in result, "FAIL: did not find job key in model build result: " + repr(result)
            print("not async, result: " + repr(result))
            job = result['job']
            job_key = job['key']['name']
            H2O.verboseprint("model building job_key: " + repr(job_key))
            job_json = self.poll_job(job_key, timeoutSecs=timeoutSecs)
            return result


    '''
    Score a model on the h2o cluster on the given Frame and return only the model metrics. 
    '''
    def compute_model_metrics(self, model, frame, timeoutSecs=60, **kwargs):
        assert model is not None, 'FAIL: "model" parameter is null'
        assert frame is not None, 'FAIL: "frame" parameter is null'

        models = self.models(key=model, timeoutSecs=timeoutSecs)
        assert models is not None, "FAIL: /Models REST call failed"
        assert models['models'][0]['model_id']['name'] == model, "FAIL: /Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['model_id']['name'], model)

        # TODO: test this assert, I don't think this is working. . .
        frames = self.frames(key=frame)
        assert frames is not None, "FAIL: /Frames/{0} REST call failed".format(frame)
        assert frames['frames'][0]['frame_id']['name'] == frame, "FAIL: /Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['frame_id']['name'], frame)

        result = self.__do_json_request('/3/ModelMetrics/models/' + model + '/frames/' + frame, cmd='post', timeout=timeoutSecs)

        mm = result['model_metrics'][0]
        H2O.verboseprint("model metrics: " + repr(mm))
        return mm


    def predict(self, model, frame, predictions_frame = None, timeoutSecs=60, **kwargs):
        assert model is not None, 'FAIL: "model" parameter is null'
        assert frame is not None, 'FAIL: "frame" parameter is null'

        models = self.models(key=model, timeoutSecs=timeoutSecs)
        assert models is not None, "FAIL: /Models REST call failed"
        assert models['models'][0]['model_id']['name'] == model, "FAIL: /Models/{0} returned Model {1} rather than Model {2}".format(model, models['models'][0]['model_id']['name'], model)

        # TODO: test this assert, I don't think this is working. . .
        frames = self.frames(key=frame)
        assert frames is not None, "FAIL: /Frames/{0} REST call failed".format(frame)
        assert frames['frames'][0]['frame_id']['name'] == frame, "FAIL: /Frames/{0} returned Frame {1} rather than Frame {2}".format(frame, frames['frames'][0]['frame_id']['name'], frame)

        postData = { 'predictions_frame': predictions_frame }

        result = self.__do_json_request('/3/Predictions/models/' + model + '/frames/' + frame, cmd='post', postData=postData, timeout=timeoutSecs)
        return result


    '''
    ModelMetrics list. 
    '''
    def model_metrics(self, model=None, frame=None, timeoutSecs=60, **kwargs):
        if model is None and frame is None:
            result = self.__do_json_request('/3/ModelMetrics', cmd='get', timeout=timeoutSecs)
        elif model is not None and frame is not None:
            result = self.__do_json_request('/3/ModelMetrics/models/' + model + '/frames/' + frame, cmd='get', timeout=timeoutSecs)
        else:
            raise ValueError("model_metrics can't yet handle the filter case")
        return result


    '''
    Delete ModelMetrics. 
    '''
    def delete_model_metrics(self, model, frame, timeoutSecs=60, **kwargs):
        assert model is not None, 'FAIL: "model" parameter is null'
        assert frame is not None, 'FAIL: "frame" parameter is null'

        result = self.__do_json_request('/3/ModelMetrics/models/' + model + '/frames/' + frame, cmd='delete', timeout=timeoutSecs)

        return result


    '''
    Return all of the models in the h2o cluster, or a single model given its key.  
    The models are contained in a list called "models" at the top level of the
    result.  Currently the list is unordered.
    TODO:
    When find_compatible_frames is implemented then the top level 
    dict will also contain a "frames" list.
    '''
    def models(self, api_version=3, key=None, timeoutSecs=20, **kwargs):
        params_dict = {
            'find_compatible_frames': False
        }
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'models', H2O.verbose)

        if key:
            result = self.__do_json_request(str(api_version) + '/Models/' + key, timeout=timeoutSecs, params=params_dict)
        else:
            result = self.__do_json_request(str(api_version) + '/Models', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Delete a model on the h2o cluster, given its key.
    '''
    def delete_model(self, key, ignoreMissingKey=True, timeoutSecs=60, **kwargs):
        assert key is not None, 'FAIL: "key" parameter is null'

        result = self.__do_json_request('/3/Models/' + key, cmd='delete', timeout=timeoutSecs)

        # TODO: look for what?
        if not ignoreMissingKey and 'f00b4r' in result:
            raise ValueError('Model key not found: ' + key)

        return result


    '''
    Delete all models on the h2o cluster.
    '''
    def delete_models(self, timeoutSecs=60, **kwargs):
        parameters = { }
        result = self.__do_json_request('/3/Models', cmd='delete', timeout=timeoutSecs)

        return result


    '''
    Return all of the grid search results in the h2o cluster.
    The grid IDs are contained in a list called "grids" at the top level of the
    result.  Currently the list is unordered.
    '''
    def grids(self, api_version=99, timeoutSecs=20, **kwargs):
        params_dict = {
        }        
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'grids', H2O.verbose)

        result = self.__do_json_request(str(api_version) + '/Grids', timeout=timeoutSecs, params=params_dict)
        return result


    '''
    Return a grid search result from the h2o cluster given its key.  
    The models IDs are contained in a list called "model_ids" at the top level of the
    result.  Currently the list is unordered.
    '''
    def grid(self, api_version=99, key=None, timeoutSecs=20, **kwargs):
        params_dict = {
        }        
        h2o_test_utils.check_params_update_kwargs(params_dict, kwargs, 'grids', H2O.verbose)

        if key:
            result = self.__do_json_request(str(api_version) + '/Grids/' + key, timeout=timeoutSecs, params=params_dict)
        else:
            raise ValueError('Grid key not given: ' + key)
        return result


    '''
    Fetch the list of REST API endpoints.
    '''
    def endpoints(self, timeoutSecs=60, **kwargs):
        parameters = { }
        result = self.__do_json_request('/3/Metadata/endpoints', cmd='get', timeout=timeoutSecs)

        return result

    '''
    Fetch the metadata for the given numbered REST API endpoint.
    '''
    def endpoint_by_number(self, num, timeoutSecs=60, **kwargs):
        parameters = { }
        result = self.__do_json_request('/3/Metadata/endpoints/' + str(num), cmd='get', timeout=timeoutSecs)

        return result

    '''
    Fetch the list of REST API schemas.
    '''
    def schemas(self, timeoutSecs=60, **kwargs):
        parameters = { }
        result = self.__do_json_request('/3/Metadata/schemas', cmd='get', timeout=timeoutSecs)

        return result

    '''
    Fetch the metadata for the given named REST API schema (e.g., FrameV3).
    '''
    def schema(self, schemaname, timeoutSecs=60, **kwargs):
        parameters = { }
        result = self.__do_json_request('/3/Metadata/schemas/' + schemaname, cmd='get', timeout=timeoutSecs)

        return result

'''
    def grid(self, algo, parameters, hyperParameters, timeoutSecs=60, asynchronous=False, **kwargs):
        assert algo is not None, 'FAIL: "algo" parameter is null'
        assert parameters is not None, 'FAIL: "parameters" parameter is null'

        gridParams = parameters
        gridParams['grid_parameters'] = json.dumps(hyperParameters)

        result = self.__do_json_request('/99/Grid/' + algo, cmd='post', postData=gridParams, raiseIfNon200=False)

        if asynchronous:
            return result
        elif result['__http_response']['status_code'] != 200:
            return result
        else:
            assert 'job' in result, "FAIL: did not find job key in model build result: " + repr(result)
            job = result['job']
            job_key = job['key']['name']
            H2O.verboseprint("grid search job_key: " + repr(job_key))
            job_json = self.poll_job(job_key, timeoutSecs=timeoutSecs)
            return result
'''
