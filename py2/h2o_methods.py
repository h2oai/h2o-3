
import os, sys, time, requests, zipfile, StringIO
import h2o_args
# from h2o_cmd import runInspect, infoFromSummary
import h2o_cmd, h2o_util
import h2o_browse as h2b

from h2o_objects import H2O
from h2o_test import verboseprint, dump_json, check_sandbox_for_errors, get_sandbox_name, log

# print "h2o_methods"

# this is done before import h2o_ray, which imports h2o_methods!
# ignoreNone is used if new = None shouldn't overwrite. Normally it does!
def check_params_update_kwargs(params_dict, kw, function, print_params, ignoreNone=False):
    # only update params_dict..don't add
    # throw away anything else as it should come from the model (propagating what RF used)
    for k,v in kw.iteritems():
        if k in params_dict:
            if v or not ignoreNone:
                # what if a type conversion happens here? (checkHeader = -1 overwriting an existing value?)
                params_dict[k] = v
                print params_dict[k], v
        else:
            raise Exception("illegal parameter '%s' with value '%s' in %s" % (k, v, function))

    if print_params:
        print "\n%s parameters:" % function, params_dict
        sys.stdout.flush()


def get_cloud(self, noExtraErrorCheck=False, timeoutSecs=10):
    # hardwire it to allow a 60 second timeout
    a = self.do_json_request('Cloud.json', noExtraErrorCheck=noExtraErrorCheck, timeout=timeoutSecs)
    # verboseprint(a)

    version    = a['version']
    if not version.startswith('0'):
        raise Exception("h2o version at node[0] doesn't look like h2o-dev version. (start with 0) %s" % version)

    consensus = a['consensus']
    locked = a['locked']
    cloud_size = a['cloud_size']
    cloud_name = a['cloud_name']
    node_id = self.node_id
    verboseprint('%s%s %s%s %s%s %s%s %s%s' % (
        "\tnode_id: ", node_id,
        "\tcloud_size: ", cloud_size,
        "\tconsensus: ", consensus,
        "\tlocked: ", locked,
        "\tversion: ", version,
    ))
    return a

def h2o_log_msg(self, message=None, timeoutSecs=15):
    if 1 == 0:
        return
    if not message:
        message = "\n"
        message += "\n#***********************"
        message += "\npython_test_name: " + h2o_args.python_test_name
        message += "\n#***********************"
    params = {'message': message}
    self.do_json_request('2/LogAndEcho', params=params, timeout=timeoutSecs)

def get_timeline(self):
    return self.do_json_request('Timeline.json')

# Shutdown url is like a reset button. Doesn't send a response before it kills stuff
# safer if random things are wedged, rather than requiring response
# so request library might retry and get exception. allow that.
def shutdown_all(self):
    try:
        self.do_json_request('Shutdown.json', noExtraErrorCheck=True)
    except:
        print "Got exception on Shutdown.json. Ignoring"
        pass
    # don't want delayes between sending these to each node
    # if you care, wait after you send them to each node
    # Seems like it's not so good to just send to one node
    # time.sleep(1) # a little delay needed?
    return (True)


# noise is a 2-tuple ("StoreView", none) for url plus args for doing during poll to create noise
# so we can create noise with different urls!, and different parms to that url
# no noise if None
def poll_url(self, response,
             timeoutSecs=10, retryDelaySecs=0.5, initialDelaySecs=0, pollTimeoutSecs=180,
             noise=None, benchmarkLogging=None, noPoll=False, reuseFirstPollUrl=False, noPrint=False):
    verboseprint('poll_url input: response:', dump_json(response))
    ### print "poll_url: pollTimeoutSecs", pollTimeoutSecs
    ### print "at top of poll_url, timeoutSecs: ", timeoutSecs

    # for the rev 2 stuff..the job_key, destination_key and redirect_url are just in the response
    # look for 'response'..if not there, assume the rev 2

    def get_redirect_url(response):
        url = None
        params = None
        # StoreView has old style, while beta_features
        if 'response_info' in response: 
            response_info = response['response_info']

            if 'redirect_url' not in response_info:
                raise Exception("Response during polling must have 'redirect_url'\n%s" % dump_json(response))

            if response_info['status'] != 'done':
                redirect_url = response_info['redirect_url']
                if redirect_url:
                    url = self.url(redirect_url)
                    params = None
                else:
                    if response_info['status'] != 'done':
                        raise Exception(
                            "'redirect_url' during polling is null but status!='done': \n%s" % dump_json(response))
        else:
            if 'response' not in response:
                raise Exception("'response' not in response.\n%s" % dump_json(response))

            if response['response']['status'] != 'done':
                if 'redirect_request' not in response['response']:
                    raise Exception("'redirect_request' not in response. \n%s" % dump_json(response))

                url = self.url(response['response']['redirect_request'])
                params = response['response']['redirect_request_args']

        return (url, params)

    # if we never poll
    msgUsed = None

    if 'response_info' in response: # trigger v2 for GBM always?
        status = response['response_info']['status']
        progress = response.get('progress', "")
    else:
        r = response['response']
        status = r['status']
        progress = r.get('progress', "")

    doFirstPoll = status != 'done'
    (url, params) = get_redirect_url(response)
    # no need to recreate the string for messaging, in the loop..
    if params:
        paramsStr = '&'.join(['%s=%s' % (k, v) for (k, v) in params.items()])
    else:
        paramsStr = ''

    # FIX! don't do JStack noise for tests that ask for it. JStack seems to have problems
    noise_enable = noise and noise != ("JStack", None)
    if noise_enable:
        print "Using noise during poll_url:", noise
        # noise_json should be like "Storeview"
        (noise_json, noiseParams) = noise
        noiseUrl = self.url(noise_json + ".json")
        if noiseParams is None:
            noiseParamsStr = ""
        else:
            noiseParamsStr = '&'.join(['%s=%s' % (k, v) for (k, v) in noiseParams.items()])

    start = time.time()
    count = 0
    if initialDelaySecs:
        time.sleep(initialDelaySecs)

    # can end with status = 'redirect' or 'done'
    # Update: on DRF2, the first RF redirects to progress. So we should follow that, and follow any redirect to view?
    # so for v2, we'll always follow redirects?
    # For v1, we're not forcing the first status to be 'poll' now..so it could be redirect or done?(NN score? if blocking)

    # Don't follow the Parse redirect to Inspect, because we want parseResult['destination_key'] to be the end.
    # note this doesn't affect polling with Inspect? (since it doesn't redirect ?
    while status == 'poll' or doFirstPoll or (status == 'redirect' and 'Inspect' not in url):
        count += 1
        if ((time.time() - start) > timeoutSecs):
            # show what we're polling with
            emsg = "Exceeded timeoutSecs: %d secs while polling." % timeoutSecs + \
                   "status: %s, url: %s?%s" % (status, urlUsed, paramsUsedStr)
            raise Exception(emsg)

        if benchmarkLogging:
            import h2o
            h2o.cloudPerfH2O.get_log_save(benchmarkLogging)

        # every other one?
        create_noise = noise_enable and ((count % 2) == 0)
        if create_noise:
            urlUsed = noiseUrl
            paramsUsed = noiseParams
            paramsUsedStr = noiseParamsStr
            msgUsed = "\nNoise during polling with"
        else:
            urlUsed = url
            paramsUsed = params
            paramsUsedStr = paramsStr
            msgUsed = "\nPolling with"

        print status, progress, urlUsed
        time.sleep(retryDelaySecs)

        response = self.do_json_request(fullUrl=urlUsed, timeout=pollTimeoutSecs, params=paramsUsed)
        verboseprint(msgUsed, urlUsed, paramsUsedStr, "Response:", dump_json(response))
        # hey, check the sandbox if we've been waiting a long time...rather than wait for timeout
        if ((count % 6) == 0):
            check_sandbox_for_errors(python_test_name=h2o_args.python_test_name)

        if (create_noise):
            # this guarantees the loop is done, so we don't need to worry about
            # a 'return r' being interpreted from a noise response
            status = 'poll'
            progress = ''
        else:
            doFirstPoll = False
            status = response['response_info']['status']
            progress = response.get('progress', "")
            # get the redirect url
            if not reuseFirstPollUrl: # reuse url for all v1 stuff
                (url, params) = get_redirect_url(response)

            if noPoll:
                return response

    # won't print if we didn't poll
    if msgUsed:
        verboseprint(msgUsed, urlUsed, paramsUsedStr, "Response:", dump_json(response))
    return response

def h2o_log_msg(self, message=None, timeoutSecs=15):
    if 1 == 0:
        return
    if not message:
        message = "\n"
        message += "\n#***********************"
        message += "\npython_test_name: " + h2o_args.python_test_name
        message += "\n#***********************"
    params = {'message': message}
    self.do_json_request('LogAndEcho.json', params=params, timeout=timeoutSecs)


def jobs_admin (*args, **kwargs):
    print "WARNING: faking jobs admin"
    a = { 'jobs': {} }
    return a

def unlock (*args, **kwargs):
    print "WARNING: faking unlock keys"
    pass

#******************************************************************************************8
# attach methods to H2O object
# this happens before any H2O instances are created
# this file is imported into h2o

H2O.get_cloud = get_cloud
H2O.h2o_log_msg = h2o_log_msg
H2O.jobs_admin = jobs_admin
H2O.unlock = unlock
H2O.get_timeline = get_timeline
# H2O.shutdown_all = shutdown_all

# attach some methods from ray
import h2o_ray
H2O.jobs = h2o_ray.jobs
H2O.poll_job = h2o_ray.poll_job
H2O.import_files = h2o_ray.import_files
H2O.parse = h2o_ray.parse
H2O.frames = h2o_ray.frames
H2O.columns = h2o_ray.columns
H2O.column = h2o_ray.column
H2O.summary = h2o_ray.summary
H2O.delete_frame = h2o_ray.delete_frame
H2O.delete_frames = h2o_ray.delete_frames
H2O.model_builders = h2o_ray.model_builders
H2O.validate_model_parameters = h2o_ray.validate_model_parameters
H2O.build_model = h2o_ray.build_model
H2O.compute_model_metrics = h2o_ray.compute_model_metrics
H2O.predict = h2o_ray.predict
H2O.model_metrics = h2o_ray.model_metrics
H2O.models = h2o_ray.models
H2O.delete_model = h2o_ray.delete_model
H2O.delete_models = h2o_ray.delete_models
H2O.endpoints = h2o_ray.endpoints
H2O.endpoint_by_number = h2o_ray.endpoint_by_number
