import sys, os, time, json, datetime, errno, urlparse, stat, getpass

debug_rest = False

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

def list_to_dict(l, key):
    result = {}
    for v in l:
        # print 'v: ', repr(v)
        # print 'key: ', key
        k = followPath(v, key.split("/"))
        # print 'k: ', k
        result[k] = v
    return result

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
