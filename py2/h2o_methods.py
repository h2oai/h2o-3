
# some methods should use 'put' instead of 'get'
# some seem to require 'delete' now?
# use the right (latest) version of this:
# http://s3.amazonaws.com/h2o-release/h2o-dev/master/1019/docs-website/REST/endpoints/markdown/toc.md

import os, sys, time, requests, zipfile, StringIO, re
import h2o_args
# from h2o_cmd import runInspect, infoFromSummary
import h2o_cmd, h2o_util, h2o_browse as h2b, h2o_sandbox

from h2o_objects import H2O
from h2o_test import verboseprint, dump_json, check_sandbox_for_errors, get_sandbox_name, log

import urllib

def poll_job2(self, firstResult, algo=None, timeoutSecs=60, noPoll=False, **kwargs):
    if noPoll:
        result = firstResult
    elif ('validation_error_count' in firstResult) and (firstResult['validation_error_count'] > 0):
        h2p.yellow_print("parameter error in %s" % algo)
        result = firstResult
    else:
        job_result = result1['jobs'][0]
        job_key = job_result['key']['name']
        verboseprint("%s job_key: %s" % (algo, job_key))

        job_result = self.poll_job(job_key, timeoutSecs=timeoutSecs)
        verboseprint(job_result)

        elapsed = time.time() - start
        print algo, " end on ", training_frame, 'took', time.time() - start, 'seconds'
        print "%d pct. of timeout" % ((elapsed/timeoutSecs) * 100)

        if job_result:
            jobs = job_result['jobs'][0]
            description = jobs['description']
            dest = jobs['dest']
            msec = jobs['msec']
            status = jobs['status']
            progress = jobs['progress']

            if status=='FAILED':
                print dump_json(job_result)
                raise Exception("Taking exception on %s job status: %s %s %s %s" % \
                    (algo, status, progress, msec, description))
            result = job_result

        else:
            raise Exception("build_model didn't get a job_result when it expected one")

    verboseprint("result:", result)
    h2o_sandbox.check_sandbox_for_errors()
    return result

# This is done before import h2o_ray, which imports h2o_methods!
# ignoreNone is used if new = None shouldn't overwrite. Normally it does!
def check_params_update_kwargs(params_dict, kw, function, print_params, ignoreNone=False):
    # only update params_dict..don't add
    # throw away anything else as it should come from the model (propagating what RF used)
    for k,v in kw.iteritems():
        if k in params_dict:
            if v or not ignoreNone:
                # what if a type conversion happens here?
                params_dict[k] = v
        else:
            raise Exception("illegal parameter '%s' with value '%s' in %s" % (k, v, function))

    if print_params:
        print "\n%s parameters:" % function, params_dict
        sys.stdout.flush()


def get_cloud(self, noExtraErrorCheck=False, timeoutSecs=10):
    # hardwire it to allow a 60 second timeout
    a = self.do_json_request('3/Cloud.json', noExtraErrorCheck=noExtraErrorCheck, timeout=timeoutSecs)
    # verboseprint(a)

    version    = a['version']
# local builds have (unknown)    if not version.startswith('0'):
# local builds have (unknown)        raise Exception("h2o version at node[0] doesn't look like h2o-dev version. (start with 0) %s" % version)

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
    if not message:
        message = "\n"
        message += "\n#***********************"
        message += "\npython_test_name: " + h2o_args.python_test_name
        message += "\n#***********************"
    params = {'message': message}
    self.do_json_request('3/LogAndEcho.json', cmd='post', params=params, timeout=timeoutSecs)
    # print "HACK: not doing 3/LogAndEcho.json"

def get_timeline(self):
    return self.do_json_request('3/Timeline.json')

# Shutdown url is like a reset button. Doesn't send a response before it kills stuff
# safer if random things are wedged, rather than requiring response
# so request library might retry and get exception. allow that.
def shutdown_all(self):
    try:
        self.do_json_request('3/Shutdown.json', cmd='post', noExtraErrorCheck=True)
    except:
        print "Got exception on Shutdown.json. Ignoring"
        pass
    # don't want delayes between sending these to each node
    # if you care, wait after you send them to each node
    # Seems like it's not so good to just send to one node
    # time.sleep(1) # a little delay needed?
    return True


#*******************************************************************************
# examples from prithvi
# http://localhost:54321/Typeahead.json/files?src=?&limit=?
# http://localhost:54321/Typeahead.json/files?src=.%2Fsmalldata%2Fairlines%2F&limit=10
def typeahead(self, timeoutSecs=10, **kwargs):
    params_dict = {
        'src': None,
        'limit': None,
    }
    check_params_update_kwargs(params_dict, kwargs, 'typeahead', print_params=True)
    # odd ...needs /files
    a = self.do_json_request('3/Typeahead.json/files', params=params_dict, timeout=timeoutSecs)
    verboseprint("\ntypeahead result:", dump_json(a))
    return a

#*******************************************************************************
def unlock (self, timeoutSecs=30, **kwargs):
    a = self.do_json_request('3/UnlockKeys.json', params=None, timeout=timeoutSecs)
    return a
    # print "WARNING: faking unlock keys"
    # pass

def remove_all_keys(self, timeoutSecs=120):
    return self.do_json_request('3/DKV', cmd='delete', timeout=timeoutSecs)

# ignore errors on remove..key might already be gone due to h2o removing it now after parse
def remove_key(self, key, timeoutSecs=120):
    a = self.do_json_request('3/DKV.json',
        params={"key": key}, ignoreH2oError=True, cmd='delete', timeout=timeoutSecs)
    self.unlock()
    return a

def jobs_admin(self, timeoutSecs=120, **kwargs):
    params_dict = {
        # 'expression': None,
    }
    params_dict.update(kwargs)
    verboseprint("\njobs_admin:", params_dict)
    a = self.do_json_request('3/Jobs.json', timeout=timeoutSecs, params=params_dict)
    verboseprint("\njobs_admin result:", dump_json(a))
    # print "WARNING: faking jobs admin"
    # a = { 'jobs': {} }
    return a
#******************************************************************************************8
def put_file(self, f, key=None, timeoutSecs=60):
    if key is None:
        key = os.path.basename(f)
        ### print "putfile specifying this key:", key

    fileObj = open(f, 'rb')
    resp = self.do_json_request(
        # don't use .json suffix here...causes 404 (for now)
        '3/PostFile',
        cmd='post',
        timeout=timeoutSecs,
        params={"destination_frame": key},
        files={"file": fileObj},
        extraComment=str(f))

    verboseprint("\nput_file response: ", dump_json(resp))
    fileObj.close()
    return key

def csv_download(self, key, csvPathname, timeoutSecs=60, **kwargs):
    params = {'key': key}
    paramsStr = '?' + '&'.join(['%s=%s' % (k, v) for (k, v) in params.items()])
    url = self.url('3/DownloadDataset.json')
    log('Start ' + url + paramsStr, comment=csvPathname)

    # do it (absorb in 1024 byte chunks)
    r = requests.get(url, params=params, timeout=timeoutSecs)
    print "csv_download r.headers:", r.headers
    if r.status_code == 200:
        f = open(csvPathname, 'wb')
        for chunk in r.iter_content(1024):
            f.write(chunk)
    print csvPathname, "size:", h2o_util.file_size_formatted(csvPathname)

def log_view(self, timeoutSecs=10, **kwargs):
    a = self.do_json_request('LogView.json', timeout=timeoutSecs)
    verboseprint("\nlog_view result:", dump_json(a))
    return a

def log_download(self, logDir=None, timeoutSecs=30, **kwargs):
    if logDir == None:
        logDir = get_sandbox_name()

    url = self.url('Logs/download')
    log('Start ' + url);
    print "\nDownloading h2o log(s) using:", url
    r = requests.get(url, timeout=timeoutSecs, **kwargs)
    if not r or not r.ok:
        raise Exception("Maybe bad url? no r in log_download %s in %s:" % inspect.stack()[1][3])

    z = zipfile.ZipFile(StringIO.StringIO(r.content))
    print "z.namelist:", z.namelist()
    print "z.printdir:", z.printdir()

    nameList = z.namelist()
    # the first is the h2ologs dir name.
    h2oLogDir = logDir + "/" + nameList.pop(0)
    print "h2oLogDir:", h2oLogDir
    print "logDir:", logDir

    # it's a zip of zipped files
    # first unzip it
    z = zipfile.ZipFile(StringIO.StringIO(r.content))
    z.extractall(logDir)
    # unzipped file should be in LOG_DIR now
    # now unzip the files in that directory
    for zname in nameList:
        resultList = h2o_util.flat_unzip(logDir + "/" + zname, logDir)

    print "\nlogDir:", logDir
    for logfile in resultList:
        numLines = sum(1 for line in open(logfile))
        print logfile, "Lines:", numLines
    print
    return resultList

#******************************************************************************************8
def inspect(self, key, offset=None, view=None, max_column_display=1000, ignoreH2oError=False,
            timeoutSecs=30):
    params = {
        # "src_key": key,
        "key": key,
        "offset": offset,
        # view doesn't exist for 2. let it be passed here from old tests but not used
    }
    a = self.do_json_request('3/Inspect.json',
        params=params,
        ignoreH2oError=ignoreH2oError,
        timeout=timeoutSecs
    )
    return a

#******************************************************************************************8
def split_frame(self, timeoutSecs=120, noPoll=False, **kwargs):
    params_dict = {
        'dataset': None,
        'ratios': None,
        'destKeys': None, # ['bigger', 'smaller']
    }
    check_params_update_kwargs(params_dict, kwargs, 'split_frame', print_params=True)
    firstResult = self.do_json_request('3/SplitFrame.json', cmd='post', timeout=timeoutSecs, params=params_dict)
    print "firstResult:", dump_json(firstResult)
    # FIX! what is ['dest']['name'] ..It's not there at the beginning?
    job_key = firstResult['key']['name']

    if noPoll:
        h2o_sandbox.check_sandbox_for_errors()
        return firstResult

    # is it polllable while it's in the CREATED state? msec looks wrong. start_time is 0
    time.sleep(2)
    result = self.poll_job(job_key)
    verboseprint("split_frame result:", dump_json(result))
    return result

#******************************************************************************************8
def create_frame(self, timeoutSecs=120, noPoll=False, **kwargs):
    # FIX! have to add legal params
    params_dict = {

    }
    check_params_update_kwargs(params_dict, kwargs, 'create_frame', print_params=True)
    firstResult = self.do_json_request('3/CreateFrame.json', cmd='post', timeout=timeoutSecs, params=params_dict)
    job_key = firstResult['dest']['name']

    if noPoll:
        h2o_sandbox.check_sandbox_for_errors()
        return firstResult

    result = self.poll_job(job_key)
    verboseprint("create_frame result:", dump_json(result))
    return result

#******************************************************************************************8
def interaction(self, timeoutSecs=120, noPoll=False, **kwargs):
    # FIX! have to add legal params
    params_dict = {

    }
    check_params_update_kwargs(params_dict, kwargs, 'interaction', print_params=True)
    firstResult = self.do_json_request('3/Interaction.json', cmd='post', timeout=timeoutSecs, params=params_dict)
    job_key = firstResult['dest']['name']

    if noPoll:
        h2o_sandbox.check_sandbox_for_errors()
        return firstResult

    result = self.poll_job(job_key)
    verboseprint("interaction result:", dump_json(result))
    return result

#******************************************************************************************8
def rapids(self, timeoutSecs=120, ignoreH2oError=False, **kwargs):
    # FIX! assume both of these are strings for now, not lists
    if 'ast' in kwargs and kwargs['ast'] is not None:
        assert isinstance(kwargs['ast'], basestring), "only string assumed? %s" % kwargs['ast']
    if 'funs' in kwargs and kwargs['funs'] is not None:
        assert isinstance(kwargs['funs'], basestring), "only string assumed? %s" % kwargs['funs']

    # currently runExec only does one or the other
    params_dict = {
        'ast': None,
        'funs': None,
    }

    check_params_update_kwargs(params_dict, kwargs, 'rapids', True)
    result = self.do_json_request('99/Rapids.json', cmd='post', timeout=timeoutSecs, postData=params_dict)

    verboseprint("rapids result:", dump_json(result))

    # FIX! maybe add something for ignoring conditionally?
    if 'exception' in result and result['exception'] and not ignoreH2oError:
        exception = result['exception']
        raise Exception('rapids with kwargs:\n%s\ngot exception:\n"%s"\n' % (dump_json(kwargs), exception))

    h2o_sandbox.check_sandbox_for_errors()
    return result

#******************************************************************************************8
def rapids_iseval(self, timeoutSecs=120, ignoreH2oError=False, **kwargs):
    # FIX! assume both of these are strings for now, not lists
    if 'ast_key' in kwargs and kwargs['ast_key'] is not None:
        assert isinstance(kwargs['ast_key'], basestring), "only string assumed? %s" % kwargs['ast_key']

    # currently runExec only does one or the other
    params_dict = {
        'ast_key': None,
    }

    check_params_update_kwargs(params_dict, kwargs, 'rapids_iseval', True)
    # doesn't like 'put' here?
    # doesn't like empty key
    result = self.do_json_request('3/Rapids.json/isEval', cmd='get', timeout=timeoutSecs, params=params_dict)
    verboseprint("rapids_iseval result:", dump_json(result))

    # FIX! maybe add something for ignoring conditionally?
    if 'exception' in result and result['exception'] and not ignoreH2oError:
        exception = result['exception']
        raise Exception('rapids with kwargs:\n%s\ngot exception:\n"%s"\n' % (dump_json(kwargs), exception))

    h2o_sandbox.check_sandbox_for_errors()
    return result

#******************************************************************************************8
def quantiles(self, timeoutSecs=300, print_params=True, **kwargs):
    params_dict = {
        'destination_key': None,
        'training_frame': None,
        'validation_frame': None,
        'ignored_columns': None,
        'score_each_iteration': None,
        'probs': None,
    }
    check_params_update_kwargs(params_dict, kwargs, 'quantiles', print_params)
    a = self.do_json_request('3/Quantiles.json', timeout=timeoutSecs, params=params_dict)
    verboseprint("\nquantiles result:", dump_json(a))
    h2o_sandbox.check_sandbox_for_errors()
    return a

#******************************************************************************************8
# attach methods to H2O object
# this happens before any H2O instances are created
# this file is imported into h2o


# ray has jobs below..is this old?
H2O.jobs_admin = jobs_admin

H2O.get_cloud = get_cloud
H2O.shutdown_all = shutdown_all
H2O.h2o_log_msg = h2o_log_msg

H2O.inspect = inspect
H2O.quantiles = quantiles
H2O.rapids = rapids
H2O.rapids_iseval = rapids_iseval
H2O.unlock = unlock
H2O.typeahead = typeahead
H2O.get_timeline = get_timeline

H2O.split_frame = split_frame
H2O.create_frame = create_frame
H2O.interaction = interaction

H2O.log_view = log_view
H2O.log_download = log_download
H2O.csv_download = csv_download
H2O.put_file = put_file

H2O.remove_all_keys = remove_all_keys
H2O.remove_key = remove_key

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
