import h2o2 as h2o
import h2o_cmd, h2o_jobs, h2o_print as h2p
import getpass, time, re, os, fnmatch
import h2o_args, h2o_util, h2o_nodes, h2o_print as h2p
from h2o_test import verboseprint, dump_json, check_sandbox_for_errors
import json

#****************************************************************************************
# hdfs/maprfs/s3/s3n paths should be absolute from the bucket (top level)
# so only walk around for local
# using this standalone, we probably want 'put' decision making by default (can always pass schema='local')
def find_folder_and_filename(bucket, pathWithRegex, schema='put', returnFullPath=False):
    checkPath = True
    # strip the common mistake of leading "/" in path, if bucket is specified too
    giveUpAndSearchLocally = False
    if bucket is not None and re.match("/", pathWithRegex):
        verboseprint("You said bucket:", bucket, "so stripping incorrect leading '/' from", pathWithRegex)
        pathWithRegex = pathWithRegex.lstrip('/')

    if bucket is None:  # good for absolute path name
        bucketPath = ""

    elif bucket == ".":
        bucketPath = os.getcwd()

    # only use if the build_cloud was for remote H2O
    # Never use the var for remote, if you're doing a put! (which always sources local)
    elif h2o_nodes.nodes[0].remoteH2O and schema!='put' and \
        (os.environ.get('H2O_REMOTE_BUCKETS_ROOT') or h2o_nodes.nodes[0].h2o_remote_buckets_root):
        if (bucket=='smalldata' or bucket=='datasets') and schema=='local':
            h2p.red_print("\nWARNING: you're using remote nodes, and 'smalldata' or 'datasets' git buckets, with schema!=put" +\
            "\nThose aren't git pull'ed by the test. Since they are user-maintained, not globally-maintained-by-0xdata," +\
            "\nthey may be out of date at those remote nodes?" +\
            "\nGoing to assume we find a path to them locally, and remote path will be the same")
            giveUpAndSearchLocally = True
        else:
            if os.environ.get('H2O_REMOTE_BUCKETS_ROOT'):
                rootPath = os.environ.get('H2O_REMOTE_BUCKETS_ROOT')
                print "Found H2O_REMOTE_BUCKETS_ROOT:", rootPath
            else:
                rootPath = h2o_nodes.nodes[0].h2o_remote_buckets_root
                print "Found h2o_nodes[0].h2o_remote_buckets_root:", rootPath

            bucketPath = os.path.join(rootPath, bucket)
            checkPath = False

    # does it work to use bucket "." to get current directory
    # this covers reote with put too
    elif os.environ.get('H2O_BUCKETS_ROOT'):
        rootPath = os.environ.get('H2O_BUCKETS_ROOT')
        print "Using H2O_BUCKETS_ROOT environment variable:", rootPath

        if not (os.path.exists(rootPath)):
            raise Exception("H2O_BUCKETS_ROOT in env but %s doesn't exist." % rootPath)

        bucketPath = os.path.join(rootPath, bucket)
        if not (os.path.exists(bucketPath)):
            raise Exception("H2O_BUCKETS_ROOT and path used to form %s which doesn't exist." % bucketPath)

    else:
        giveUpAndSearchLocally = True
        

    #******************************************************************************************
    if giveUpAndSearchLocally:
        # if we run remotely, we're assuming the import folder path on the remote machine
        # matches what we find on our local machine. But maybe the local user doesn't exist remotely 
        # so using his path won't work. 
        # Resolve by looking for special state in the config. If user = 0xdiag, just force the bucket location
        # This is a lot like knowing about fixed paths with s3 and hdfs
        # Otherwise the remote path needs to match the local discovered path.

        # want to check the username being used remotely first. should exist here too if going to use
        username = getpass.getuser()
        h2oUsername = h2o_nodes.nodes[0].username
        verboseprint("username:", username, "h2oUsername:", h2oUsername)

        # bucket named "datasets" is special. Don't want to find it in /home/0xdiag/datasets
        # needs to be the git clone 'datasets'. Find it by walking upwards below
        # disable it from this looking in home dir. Could change priority order?
        # resolved in order, looking for bucket (ln -s will work) in these home dirs.

        if bucket=='datasets': # special case 
            possibleUsers = []
        elif h2oUsername != username:
            possibleUsers = [username, h2oUsername, "0xdiag"]
        else:
            possibleUsers = [username, "0xdiag"]

        for u in possibleUsers:
            rootPath = os.path.expanduser("~" + u)
            bucketPath = os.path.join(rootPath, bucket)
            verboseprint("Checking bucketPath:", bucketPath, 'assuming home is', rootPath)
            if os.path.exists(bucketPath):
                verboseprint("search A did find", bucket, "at", rootPath)
                break
        else:
            # last chance to find it by snooping around
            rootPath = os.getcwd()
            verboseprint("find_bucket looking upwards from", rootPath, "for", bucket)
            # don't spin forever 
            levels = 0
            while not (os.path.exists(os.path.join(rootPath, bucket))):
                verboseprint("Didn't find", bucket, "at", rootPath)
                rootPath = os.path.split(rootPath)[0]
                levels += 1
                if (levels==6):
                    raise Exception("unable to find bucket: %s. Maybe missing link in /home/0xdiag or /home/0xcustomer or jenkins ~? or whatever user is running the python or the h2o?" % bucket)

            verboseprint("search B did find", bucket, "at", rootPath)
            bucketPath = os.path.join(rootPath, bucket)

    #******************************************************************************************
    # if there's no path, just return the bucketPath
    # but what about cases with a header in the folder too? (not putfile)
    if pathWithRegex is None:
        if returnFullPath:
            return bucketPath
        else:
            return (bucketPath, None)

    # if there is a "/" in the path, that means it's not just a pattern
    # split it
    # otherwise it is a pattern. use it to search for files in python first? 
    # FIX! do that later
    elif "/" in pathWithRegex:
        (head, tail) = os.path.split(pathWithRegex)
        folderPath = os.path.abspath(os.path.join(bucketPath, head))

        # accept all 0xcustomer-datasets without checking..since the current python user
        # may not have permission, but h2o will
        # try a couple times with os.stat in between, in case it's not automounting
        if '/mnt/0xcustomer-datasets' in folderPath:
            pass
        else:
            retry = 0
            while checkPath and (not os.path.exists(folderPath)) and retry<5:
                # we can't stat an actual file, because we could have a regex at the end of the pathname
                print "Retrying", folderPath, "in case there's a autofs mount problem"
                os.stat(folderPath)
                retry += 1
                time.sleep(1)
            
            if checkPath and not os.path.exists(folderPath):
                raise Exception("%s doesn't exist. %s under %s may be wrong?" % (folderPath, head, bucketPath))
    else:
        folderPath = bucketPath
        tail = pathWithRegex
        
    verboseprint("folderPath:", folderPath, "tail:", tail)

    if returnFullPath:
        return os.path.join(folderPath, tail)
    else:
        return (folderPath, tail)

#***************************************************************************yy
# passes additional params thru kwargs for parse
# use_header_file=
# header=
# exclude=
# src_key= only used if for put file key name (optional)
# path should point to a file or regex of files. (maybe folder works? but unnecessary
def import_only(node=None, schema='local', bucket=None, path=None,
    timeoutSecs=30, retryDelaySecs=0.1, initialDelaySecs=0, pollTimeoutSecs=180, noise=None,
    benchmarkLogging=None, noPoll=False, doSummary=True, src_key=None, noPrint=False, 
    importParentDir=True, **kwargs):

    # FIX! hack all put to local, since h2o-dev doesn't have put yet?
    # multi-machine put will fail as a result.

    # if schema=='put':
    #    h2p.yellow_print("WARNING: hacking schema='put' to 'local'..h2o-dev doesn't support upload." +  
    #        "\nMeans multi-machine with 'put' will fail")
    #    schema = 'local'

    if src_key and schema!='put':
        raise Exception("can only specify a 'src_key' param for schema='put'. You have %s %s" % (schema, src_key))

    # no bucket is sometimes legal (fixed path)
    if not node: node = h2o_nodes.nodes[0]

    if path is None:
        raise Exception("import_only: path parameter needs to be specified")

    if "/" in path:
        (head, pattern) = os.path.split(path)
    else:
        (head, pattern)  = ("", path)

    verboseprint("head:", head)
    verboseprint("pattern:", pattern)

    # to train users / okay here
    # normally we import the folder above, but if we import exactly, the path can't have regex
    # the folder can't have regex in any case
    if importParentDir:
        if re.search(r"[\*<>{}[\]~`]", head):
           raise Exception("h2o folder path %s can't be regex. path= was %s" % (head, path))
    else:
        if re.search(r"[\*<>{}[\]~`]", path):
           raise Exception("h2o path %s can't be regex. path= was %s" % (head, path))

    if schema=='put':
        # to train users
        if re.search(r"[/\*<>{}[\]~`]", pattern):
            raise Exception("h2o putfile basename %s can't be regex. path= was %s" % (pattern, path))

        if not path: 
            raise Exception("path= didn't say what file to put")

        (folderPath, filename) = find_folder_and_filename(bucket, path, schema)
        filePath = os.path.join(folderPath, filename)
        verboseprint("put filename:", filename, "folderPath:", folderPath, "filePath:", filePath)

        if not noPrint:
            h2p.green_print("\nimport_only:", h2o_args.python_test_name, "uses put:/%s" % filePath) 
            h2p.green_print("Local path to file that will be uploaded: %s" % filePath)
            h2p.blue_print("That path resolves as:", os.path.realpath(filePath))

        
        if h2o_args.abort_after_import:
            raise Exception("Aborting due to abort_after_import (-aai) argument's effect in import_only()")
    
        # h2o-dev: it always wants a key name
        if src_key is None:
            src_key = filename
        key = node.put_file(filePath, key=src_key, timeoutSecs=timeoutSecs)

        # hmm.. what should importResult be in the put case
        # set it to None. No import is done, and shouldn't be used if you're doing schema='put'
        # ..make it look like an import files result..This is just for test consistency
        importResult = json.loads('{\
          "dels": [],\
          "fails": [],\
          "files": ["%s"],\
          "destination_frames": ["%s"],\
          "path": "%s",\
          "schema_name": null, "schema_type": null, "schema_version": null\
        }'% (filename, src_key, filePath))
        return (importResult, key)

    if schema=='local' and not \
            (node.redirect_import_folder_to_s3_path or node.redirect_import_folder_to_s3n_path):
        (folderPath, pattern) = find_folder_and_filename(bucket, path, schema)
        filePath = os.path.join(folderPath, pattern)
        h2p.green_print("\nimport_only:", h2o_args.python_test_name, "uses local:/%s" % filePath)
        h2p.green_print("Path h2o will be told to use: %s" % filePath)
        h2p.blue_print("If local jvms, path resolves locally as:", os.path.realpath(filePath))
        if h2o_args.abort_after_import:
            raise Exception("Aborting due to abort_after_import (-aai) argument's effect in import_only()")

        # FIX! why are we returning importPattern here..it's different than finalImportString if we import a folder?
        # is it used for key matching by others?

        # FIX! hack ..h2o-dev is creating key names with the absolute path, not the sym link path
        # messes up for import folders that go thru /home/<user>/home-0xdiag-datasets
        # importPattern = folderURI + "/" + pattern
        # could include this on the entire importPattern if we no longer have regex basename in h2o-dev?
          
        folderURI = 'nfs:/' + folderPath
        # folderURI = 'nfs:/' + os.path.realpath(folderPath)
        if importParentDir:
            finalImportString = folderPath
        else:
            finalImportString = folderPath + "/" + pattern
        importResult = node.import_files(finalImportString, timeoutSecs=timeoutSecs)

    else:
        if bucket is not None and re.match("/", head):
            verboseprint("You said bucket:", bucket, "so stripping incorrect leading '/' from", head)
            head = head.lstrip('/')
    
        # strip leading / in head if present
        if bucket and head!="":
            folderOffset = bucket + "/" + head
        elif bucket:
            folderOffset = bucket
        else:
            folderOffset = head

        if h2o_args.abort_after_import:
            raise Exception("Aborting due to abort_after_import (-aai) argument's effect in import_only()")

        n = h2o_nodes.nodes[0]
        if schema=='s3' or node.redirect_import_folder_to_s3_path:
            # this is just like s3n now? i.e. we can point down inside the s3 bucket like s3n?
            folderOffset = re.sub("smalldata", "h2o-smalldata", folderOffset)
            folderURI = "s3://" + folderOffset
            if not n.aws_credentials:
                print "aws_credentials: %s" % n.aws_credentials
                # raise Exception("Something was missing for s3 on the java -jar cmd line when the cloud was built")
                print "ERROR: Something was missing for s3 on the java -jar cmd line when the cloud was built"

            if importParentDir:
                finalImportString = folderURI
            else:
                finalImportString = folderURI + "/" + pattern
            importResult = node.import_files(finalImportString, timeoutSecs=timeoutSecs)

        elif schema=='s3n' or node.redirect_import_folder_to_s3n_path:
            # FIX! hack for now...when we change import folder to import s3, point to unique bucket name for h2o
            # should probably deal with this up in the bucket resolution 
            # this may change other cases, but smalldata should only exist as a "bucket" for us?
            folderOffset = re.sub("smalldata", "h2o-smalldata", folderOffset)
            if not (n.use_hdfs and ((n.hdfs_version and n.hdfs_name_node) or n.hdfs_config)):
                print "use_hdfs: %s hdfs_version: %s hdfs_name_node: %s" % (n.use_hdfs, n.hdfs_version, n.hdfs_name_node)
                if n.hdfs_config:
                    print "hdfs_config: %s" % n.hdfs_config
                # raise Exception("Something was missing for s3n on the java -jar cmd line when the cloud was built")
                print "ERROR: Something was missing for s3n on the java -jar cmd line when the cloud was built"
            folderURI = "s3n://" + folderOffset
            if importParentDir:
                finalImportString = folderURI
            else:
                finalImportString = folderURI + "/" + pattern
            importResult = node.import_files(finalImportString, timeoutSecs=timeoutSecs)

        elif schema=='maprfs':
            if not n.use_maprfs:
                print "use_maprfs: %s" % n.use_maprfs
                # raise Exception("Something was missing for maprfs on the java -jar cmd line when the cloud was built")
                print "ERROR: Something was missing for maprfs on the java -jar cmd line when the cloud was built"
            # if I use the /// and default, the key names that get created by h2o only have 1 slash
            # so the parse doesn't find the key name
            if n.hdfs_name_node:
                folderURI = "maprfs://" + n.hdfs_name_node + "/" + folderOffset
            else:
                # this is different than maprfs? normally we specify the name though
                # folderURI = "maprfs:///" + folderOffset
                folderURI = "maprfs:/" + folderOffset
            if importParentDir:
                finalImportString = folderURI
            else:
                finalImportString = folderURI + "/" + pattern
            importResult = node.import_files(finalImportString, timeoutSecs=timeoutSecs)

        elif schema=='hdfs':
            # check that some state from the cloud building time was right
            # the requirements for this may change and require updating
            if not (n.use_hdfs and ((n.hdfs_version and n.hdfs_name_node) or n.hdfs_config)):
                print "use_hdfs: %s hdfs_version: %s hdfs_name_node: %s" % (n.use_hdfs, n.hdfs_version, n.hdfs_name_node)
                if n.hdfs_config:
                    print "hdfs_config: %s" % n.hdfs_config
                # raise Exception("Something was missing for hdfs on the java -jar cmd line when the cloud was built")
                print "ERROR: Something was missing for hdfs on the java -jar cmd line when the cloud was built"

            if n.hdfs_name_node:
                folderURI = "hdfs://" + n.hdfs_name_node + "/" + folderOffset
            else:
                # this is different than maprfs? normally we specify the name though
                folderURI = "hdfs://" + folderOffset
            if importParentDir:
                finalImportString = folderURI
            else:
                finalImportString = folderURI + "/" + pattern
            importResult = node.import_files(finalImportString, timeoutSecs=timeoutSecs)

        else: 
            raise Exception("schema not understood: %s" % schema)

    print "\nimport_only:", h2o_args.python_test_name, schema, "uses", finalImportString
    importPattern = folderURI + "/" + pattern
    return (importResult, importPattern)


#****************************************************************************************
# can take header, header_from_file, exclude params
def parse_only(node=None, pattern=None, hex_key=None, importKeyList=None, 
    timeoutSecs=30, retryDelaySecs=0.1, initialDelaySecs=0, pollTimeoutSecs=180,
    noise=None, benchmarkLogging=None, noPoll=False, **kwargs):

    if not node: node = h2o_nodes.nodes[0]
    # Get the list of all keys and use those that match the pattern
    # FIX! this can be slow. Can we use h2o to filter the list for us?

    # HACK. to avoid the costly frames, pass the imported key list during import_parse
    # won't work for cases where we do multiple import_only, then parse (for multi-dir import)
    matchingList = []
    if importKeyList:
        # the pattern is a full path/key name, so no false matches
        for key_name in importKeyList:
            if fnmatch.fnmatch(str(key_name), pattern):
                matchingList.append(key_name)
    else:
        h2p.yellow_print("WARNING: using frames to look up key names for possible parse regex")
        framesResult = node.frames(timeoutSecs=timeoutSecs)
        for frame in framesResult['frames']:
            key_name = frame['key']['name']
            if fnmatch.fnmatch(str(key_name), pattern):
                matchingList.append(key_name)

    if len(matchingList)==0:
        raise Exception("Didn't find %s in key list %s or Frames result" % (pattern, importKeyList))

    start = time.time()
    # put quotes on all keys
    parseResult = node.parse(key=matchingList, hex_key=hex_key,
        timeoutSecs=timeoutSecs, retryDelaySecs=retryDelaySecs, 
        initialDelaySecs=initialDelaySecs, pollTimeoutSecs=pollTimeoutSecs, noise=noise,
        benchmarkLogging=benchmarkLogging, noPoll=noPoll, **kwargs)
    # FIX! extract and print the result key name (from parseResult)
    print "\nparse took", time.time() - start, "seconds"

    parseResult['python_source'] = pattern
    return parseResult


#****************************************************************************************
def import_parse(node=None, schema='local', bucket=None, path=None,
    src_key=None, hex_key=None, 
    timeoutSecs=30, retryDelaySecs=0.1, initialDelaySecs=0, pollTimeoutSecs=180, noise=None,
    benchmarkLogging=None, noPoll=False, doSummary=True, noPrint=True, 
    importParentDir=True, **kwargs):

    # FIX! hack all put to local, since h2o-dev doesn't have put yet?
    # multi-machine put will fail as a result.
    # if schema=='put':
    #    h2p.yellow_print("WARNING: hacking schema='put' to 'local'..h2o-dev doesn't support upload." +  
    #        "\nMeans multi-machine with 'put' will fail")
    #    schema = 'local'

    if not node: node = h2o_nodes.nodes[0]
    (importResult, importPattern) = import_only(node, schema, bucket, path,
        timeoutSecs, retryDelaySecs, initialDelaySecs, pollTimeoutSecs, noise, 
        benchmarkLogging, noPoll, doSummary, src_key, noPrint, importParentDir, **kwargs)

    verboseprint("importPattern:", importPattern)
    verboseprint("importResult", dump_json(importResult))

    assert len(importResult['destination_frames']) >= 1, "No keys imported, maybe bad bucket %s or path %s" % (bucket, path)
    # print "importResult:", importResult

    # get rid of parse timing in tests now
    start = time.time()
    parseResult = parse_only(node, importPattern, hex_key, importResult['destination_frames'],
        timeoutSecs, retryDelaySecs, initialDelaySecs, pollTimeoutSecs, noise, 
        benchmarkLogging, noPoll, **kwargs)
    elapsed = time.time() - start
    print importPattern, "parsed in", elapsed, "seconds.", "%d pct. of timeout" % ((elapsed*100)/timeoutSecs), "\n"
    parseResult['python_elapsed'] = elapsed

    verboseprint("parseResult:", dump_json(parseResult))

    # do SummaryPage here too, just to get some coverage
    # only if not noPoll. otherwise parse isn't done
    if doSummary and not noPoll:
        # if parse blows up, we want error isolation ..i.e. find stack traces here, rather than the next guy blowing up
        check_sandbox_for_errors()
        print "WARNING: not doing inspect/summary for now after parse"
    else:
        # isolate a parse from the next thing
        check_sandbox_for_errors()

    return parseResult

#****************************************************************************************

# returns full key name, from current store view
def find_key(pattern=None):
    try:
        patternObj = re.compile(pattern)
    except:
        raise Exception("Need legal string pattern in find_key, not %s", pattern)

    frames = h2o_nodes.nodes[0].frames()['frames']
    keyList = [f['key']['name'] for f in frames] 
    print "find_key keyList:", keyList

    result = []
    for key in keyList:
        if patternObj.search(key):
            result.append(key)

    if not result:
        for key in keyList:
            # if python regex didn't find anything, maybe the pattern is unix-style file match
            if fnmatch.fnmatch(key, pattern):
                result.append(key)

    if len(result) == 0:
        verboseprint("Warning: No match for %s" % pattern)
        return None

    if len(result) > 1:
        verboseprint("Warning: multiple imported keys match the key pattern %s, Using: %s" % (pattern, result[0]))

    return result[0]


#****************************************************************************************
# the storeViewResult for every node may or may not be the same
# supposed to be the same? In any case
# pattern can't be regex to h2o?
# None should be same as no pattern
def delete_keys(node=None, pattern=None, timeoutSecs=120):
    if not node: node = h2o_nodes.nodes[0]

    kwargs = {'filter': pattern}
    deletedCnt = 0
    triedKeys = []
    while True:
        # FIX! h2o is getting a bad store_view NPE stack trace if I grabe all the 
        # keys at the end of a test, prior to removing. Just grab 20 at a time like h2o 
        # used to do for me. Maybe the keys are changing state, and going slower will eliminate the race
        # against prior work (but note that R might see the same problem
        storeViewResult = h2o_cmd.runStoreView(node, timeoutSecs=timeoutSecs, view=20, **kwargs)
        # we get 20 at a time with default storeView
        keys = storeViewResult['keys']
        
        if not keys:
            break

        # look for keys we already sent a remove on. Maybe those are locked.
        # give up on those
        deletedThisTime = 0
        for k in keys:
            if k in triedKeys:
                print "Already tried to delete %s. Must have failed. Not trying again" % k
            # don't delete the DRF __Tree__ keys. deleting the model does that. causes race conditions
            elif '__Tree__' in k['key']:
                print "Not deleting a tree key from DRF: %s" % k
            elif 'DRF_' in k['key']:
                print "Not deleting DRF key..they may be problematic in flight: %s" % k
            elif '__RFModel__' in k['key']:
                print "Not deleting __RFModel__ key..seeing NPE's if I try to delete them: %s" % k
            else:
                print "Deleting", k['key'], "at", node
                node.remove_key(k['key'], timeoutSecs=timeoutSecs)
                deletedCnt += 1
                deletedThisTime += 1
            triedKeys.append(k)
        # print "Deleted", deletedCnt, "keys at %s:%s" % (node.http_addr, node.port)
        if deletedThisTime==0:
            break
    # this is really the count that we attempted. Some could have failed.
    return deletedCnt

# could detect if pattern is used, and use the h2o "delete all keys" method if not
def delete_keys_at_all_nodes(node=None, pattern=None, timeoutSecs=120):
    print "Frame is too slow to look up key names when a lot of unparsed files were imported"
    print "Just using remove_all_keys and saying 0 removed"
    print "WARNING: pattern is ignored"
    if 1==1:
        # can this be called when the cloud didn't get built?
        if h2o.n0:
            h2o.n0.remove_all_keys()
        return 0
    else:
        print "Going to delete all keys one at a time (slower than 'remove all keys')"
        # TEMP: change this to remove_all_keys which ignores locking and removes keys?
        # getting problems when tests fail in multi-test-on-one-h2o-cluster runner*sh tests
        if not node: node = h2o_nodes.nodes[0]
        print "Will cancel any running jobs, because we can't unlock keys on running jobs"
        # I suppose if we used a pattern, we wouldn't have to worry about running jobs..oh well.
        h2o_jobs.cancelAllJobs()
        print "unlock all keys first to make sure broken keys get removed"
        node.unlock()
        totalDeletedCnt = 0
        deletedCnt = delete_keys(node, pattern=pattern, timeoutSecs=timeoutSecs)
        totalDeletedCnt += deletedCnt

        if pattern:
            print "Total: Deleted", totalDeletedCnt, "keys with filter=", pattern, "at", len(h2o_nodes.nodes), "nodes"
        else:
            print "Total: Deleted", totalDeletedCnt, "keys at", len(h2o_nodes.nodes), "nodes"
            # do a remove_all_keys to clean out any locked keys also (locked keys will complain above)
            # doesn't work if you remove job keys first, since it looks at the job list and gets confused
            ### node.remove_all_keys(timeoutSecs=timeoutSecs)

        return totalDeletedCnt


def count_keys(node=None, pattern=None, timeoutSecs=90):
    if not node: node = h2o_nodes.nodes[0]
    kwargs = {'filter': pattern}
    nodeCnt = 0
    offset = 0
    while True:
        # we get 20 at a time with default storeView
        # if we get < 20, we're done
        storeViewResult = h2o_cmd.runStoreView(node, timeoutSecs=timeoutSecs, offset=offset, view=20, **kwargs)
        keys = storeViewResult['keys']
        if not keys:
            break
        nodeCnt += len(storeViewResult['keys'])
        if len(keys) < 20:
            break
        offset += 20

    print nodeCnt, "keys at %s:%s" % (node.http_addr, node.port)
    return nodeCnt

def count_keys_at_all_nodes(node=None, pattern=None, timeoutSecs=90):
    if not node: node = h2o_nodes.nodes[0]
    totalCnt = 0
    # do it in reverse order, since we always talk to 0 for other stuff
    # this will be interesting if the others don't have a complete set
    # theoretically, the deletes should be 0 after the first node 
    # since the deletes should be global
    for node in reversed(h2o_nodes.nodes):
        nodeCnt = count_keys(node, pattern=pattern, timeoutSecs=timeoutSecs)
        totalCnt += nodeCnt
    if pattern:
        print "Total: ", totalCnt, "keys with filter=", pattern, "at", len(h2o_nodes.nodes), "nodes"
    else:
        print "Total: ", totalCnt, "keys at", len(h2o_nodes.nodes), "nodes"
    return totalCnt


#****************************************************************************************
# Since we can't trust a single node storeview list, this will get keys that match text
# for deleting, from a list saved from an import
def delete_keys_from_import_result(node=None, pattern=None, importResult=None, timeoutSecs=30):
    if not node: node = h2o_nodes.nodes[0]
    # the list could be from hdfs/s3 or local. They have to different list structures
    deletedCnt = 0
    if 'succeeded' in importResult:
        kDict = importResult['succeeded']
        for k in kDict:
            key = k['key']
            if (pattern in key) or pattern is None:
                print "Removing", key
                removeKeyResult = node.remove_key(key=key)
                deletedCnt += 1
    elif 'destination_frames' in importResult:
        kDict = importResult['destination_frames']
        for k in kDict:
            key = k
            if (pattern in key) or pattern is None:
                print "Removing", key
                removeKeyResult = node.remove_key(key=key)
                deletedCnt += 1
    else:
        raise Exception ("Can't find 'files' or 'succeeded' in your file dict. why? not from hdfs/s3 or local?")
    print "Deleted", deletedCnt, "keys at", node
    return deletedCnt
