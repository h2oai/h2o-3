
import sys, os, glob, time, datetime, stat, json, tempfile, shutil, psutil, random, urlparse, getpass
import h2o_args
import h2o_nodes
import h2o_sandbox
from copy import copy

# http://stackoverflow.com/questions/10026797/using-json-keys-as-python-attributes-in-nested-json
# http://stackoverflow.com/questions/5021041/are-there-any-gotchas-with-this-python-pattern
# other:
# class AttributeDict(dict): 
#     __getattr__ = dict.__getitem__
#     __setattr__ = dict.__setitem__

# http://stackoverflow.com/questions/4984647/accessing-dict-keys-like-an-attribute-in-python#answer-14620633
class AttrDict(dict):
    def __init__(self, *args, **kwargs):
        super(AttrDict, self).__init__(*args, **kwargs)
        self.__dict__ = self
# j = '{"y": [2, 3, {"a": 55, "b": 66}], "x": 4}'
# aa = json.loads(j, object_hook=AttrDict)

# generic python object generation with dotted attributes, from json object which was created with dict = AttrDict
class OutputObj(AttrDict):
    def __iter__(self):
        # for attr, value in self.__dict__.iteritems():
        for attr, value in self.iteritems():
            yield attr, value

    def __init__(self, output, name, noPrint=False):
        super(OutputObj, self).__init__()
        assert isinstance(output, dict), "json obj given to OutputObj should be dict"

        # hacky, but simplest to get all dicts to AttrDicts?
        aa = json.dumps(output)
        bb = json.loads(aa, object_hook=AttrDict)
        self.update(bb)

        self.name = name

        # just because validation_messages exist, doesn't mean there's a failure
        if ('validation_error_count' in self) and (self.validation_error_count >= 1):
            print "The h2o json response says something failed. validation_error_count: %s" % self.validation_error_count
            if 'validation_messages' not in self:
                raise Exception("No validation_messages for the validation_error_count!=0: %s")
            else:
                raise Exception("The h2o json response says something failed. validation_messages: %s" % \
                    dump_json(self.validation_messages))
            
        if not noPrint:
            for k,v in self.iteritems():
                if k == 'parameters':
                    print "Not showing 'parameters'"
                elif k == 'data':
                    print "Not showing 'data'"
                elif k == 'frame':
                    print "Not showing 'frame'"
                elif k == 'model':
                    print "Not showing 'model'"
                elif k == 'columns':
                    print "Not showing 'columns'"
                elif k == '__meta':
                    print "Not showing '__meta'"
                elif k == 'vec_keys':
                    print "Not showing 'vec_keys'"
                elif k == 'chunk_summary':
                    print "Not showing 'chunk_summary'"
                elif k == 'distribution_summary':
                    print "Not showing 'distribution_summary'"
                elif k == 'vec_ids':
                    print "Not showing 'vec_ids'"
                # this is if I drill into an inspect column with an object
                elif k == 'domain' and self.name=='inspect_column': 
                    print "Not showing 'domain'"
                else:
                    #  if it's a list with > 20, just print it normal
                    if isinstance(v, list) and len(v) > 20:
                        print self.name, k, v
                    elif not isinstance(v,dict):
                        print self.name, k, v
                    else:
                        # don't print any __meta entry in a dict
                        v2 = v
                        if '__meta' in v2:
                            v2 = copy(v)
                            del v2['__meta']
                        # print self.name, k, dump_json(v2)
                        # if the key has '_summary' in it, do a dump_jsvon
                        if '_summary' in k:
                            print self.name, k, dump_json(v2)
                        else:
                            print self.name, k, v2

    # these might be useful
    def rec_getattr(self, attr):
        """Get object's attribute. May use dot notation.
        >>> class C(object): pass
        >>> a = C()
        >>> a.b = C()
        >>> a.b.c = 4
        >>> rec_getattr(a, 'b.c')
        4
        """
        return reduce(getattr, attr.split("."), self)

    def rec_setattr(self, attr, value):
        """Set object's attribute. May use dot notation.
        >>> class C(object): pass
        >>> a = C()
        >>> a.b = C()
        >>> a.b.c = 4
        >>> rec_setattr(a, 'b.c', 2)
        >>> a.b.c
        2
        """
        attrs = attr.split(".")
        setattr(reduce(getattr, attrs[:-1], self), attrs[-1], value)



# this is just for putting timestamp in front of all stdout
class OutWrapper:
    def __init__(self, out):
        self._out = out

    def write(self, x):
            # got this with random data to parse.. why? it shows up in our stdout?
            # UnicodeEncodeError:
            #  'ascii' codec can't encode character u'\x80' in position 41: ordinal not in range(128)
            # could we be getting unicode object, or is it just the bytes
            try:
                s = x.replace('\n', '\n[{0}] '.format(datetime.datetime.now()))
                self._out.write(s)
            except:
                self._out.write(s.encode('utf8'))

    def flush(self):
        self._out.flush()

def verboseprint(*args, **kwargs):
    if h2o_args.verbose:
        for x in args: # so you don't have to create a single string
            print x,
        for x in kwargs: # so you don't have to create a single string
            print x,
        print
        # so we can see problems when hung?
        sys.stdout.flush()

def sleep(secs):
    if getpass.getuser() == 'jenkins':
        period = max(secs, 120)
    else:
        period = secs
        # if jenkins, don't let it sleep more than 2 minutes
    # due to left over h2o.sleep(3600)
    time.sleep(period)

def find_file(base):
    # epand ~ or ~user with home dir
    f = os.path.expanduser(base)
    if not os.path.exists(f): f = '../' + base
    if not os.path.exists(f): f = '../../' + base
    if not os.path.exists(f): f = '../../../' + base
    if not os.path.exists(f): f = 'py/' + base
    # these 2 are for finding from h2o-perf
    if not os.path.exists(f): f = '../h2o/' + base
    if not os.path.exists(f): f = '../../h2o/' + base
    if not os.path.exists(f):
        raise Exception("unable to find file %s" % base)
    return os.path.abspath(f)

# The cloud is uniquely named per user (only) and pid
# do the flatfile the same way
# Both are the user that runs the test. The config might have a different username on the
# remote machine (0xdiag, say, or hduser)
def flatfile_pathname():
    return (LOG_DIR + '/pytest_flatfile-%s' % getpass.getuser())

# used to rename the sandbox when running multiple tests in same dir (in different shells)
def get_sandbox_name():
    if os.environ.has_key("H2O_SANDBOX_NAME"):
        a = os.environ["H2O_SANDBOX_NAME"]
        print "H2O_SANDBOX_NAME", a
        return a
    else:
        return "sandbox"

# shutil.rmtree doesn't work on windows if the files are read only.
# On unix the parent dir has to not be readonly too.
# May still be issues with owner being different, like if 'system' is the guy running?
# Apparently this escape function on errors is the way shutil.rmtree can
# handle the permission issue. (do chmod here)
# But we shouldn't have read-only files. So don't try to handle that case.

def handleRemoveError(func, path, exc):
    # If there was an error, it could be due to windows holding onto files.
    # Wait a bit before retrying. Ignore errors on the retry. Just leave files.
    # Ex. if we're in the looping cloud test deleting sandbox.
    excvalue = exc[1]
    print "Retrying shutil.rmtree of sandbox. Will ignore errors. Exception was", excvalue.errno
    time.sleep(2)
    try:
        func(path)
    except OSError:
        pass

LOG_DIR = get_sandbox_name()

def clean_sandbox():
    if os.path.exists(LOG_DIR):
        # shutil.rmtree fails to delete very long filenames on Windoze
        # look at h2o/py/h2o_test for alternate methods if a problem
        # remember save_model creates directory now in syn_datasets (maybe should created it in sandbox?)
        print "Removing", LOG_DIR, "(if slow, might be old ice dir spill files)"
        start = time.time()
        shutil.rmtree(LOG_DIR, ignore_errors=False, onerror=handleRemoveError)
        elapsed = time.time() - start
        print "Took %s secs to remove %s" % (elapsed, LOG_DIR)
    # it should have been removed, but on error it might still be there
    if not os.path.exists(LOG_DIR):
        os.mkdir(LOG_DIR)

# who knows if this one is ok with windows...doesn't rm dir, just
# the stdout/stderr files
def clean_sandbox_stdout_stderr():
    if os.path.exists(LOG_DIR):
        files = []
        # glob.glob returns an iterator
        for f in glob.glob(LOG_DIR + '/*stdout*'):
            verboseprint("cleaning", f)
            os.remove(f)
        for f in glob.glob(LOG_DIR + '/*stderr*'):
            verboseprint("cleaning", f)
            os.remove(f)

def clean_sandbox_doneToLine():
    if os.path.exists(LOG_DIR):
        files = []
        # glob.glob returns an iterator
        for f in glob.glob(LOG_DIR + '/*doneToLine*'):
            verboseprint("cleaning", f)
            os.remove(f)


# just use a global here for the sticky state
sandbox_error_was_reported = False
def check_sandbox_for_errors(cloudShutdownIsError=False, sandboxIgnoreErrors=False, python_test_name=''):
    # dont' have both tearDown and tearDownClass report the same found error
    # only need the first
    global sandbox_error_was_reported
    if sandbox_error_was_reported: # gets current state
        return

    # Can build a cloud that ignores all sandbox things that normally fatal the test
    # Kludge, test will set this directly if it wants, rather than thru build_cloud parameter.
    # we need the sandbox_ignore_errors, for the test teardown_cloud..the state disappears!
    ignore = sandboxIgnoreErrors or (h2o_nodes.nodes and h2o_nodes.nodes[0].sandbox_ignore_errors)
    errorFound = h2o_sandbox.check_sandbox_for_errors(
        LOG_DIR=LOG_DIR,
        sandboxIgnoreErrors=ignore,
        cloudShutdownIsError=cloudShutdownIsError,
        python_test_name=python_test_name)

    if errorFound:
        sandbox_error_was_reported = True

def tmp_file(prefix='', suffix='', tmp_dir=None):
    if not tmp_dir:
        tmpdir = LOG_DIR
    else:
        tmpdir = tmp_dir

    fd, path = tempfile.mkstemp(prefix=prefix, suffix=suffix, dir=tmpdir)
    # make sure the file now exists
    # os.open(path, 'a').close()
    # give everyone permission to read it (jenkins running as
    # 0xcustomer needs to archive as jenkins
    permissions = stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP | stat.S_IROTH
    os.chmod(path, permissions)
    return (fd, path)


def tmp_dir(prefix='', suffix=''):
    return tempfile.mkdtemp(prefix=prefix, suffix=suffix, dir=LOG_DIR)

def make_syn_dir():
    # move under sandbox
    # the LOG_DIR must have been created for commands.log before any datasets would be created
    SYNDATASETS_DIR = LOG_DIR + '/syn_datasets'
    if os.path.exists(SYNDATASETS_DIR):
        shutil.rmtree(SYNDATASETS_DIR)
    os.mkdir(SYNDATASETS_DIR)
    return SYNDATASETS_DIR

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
                f.write('    # ')
                f.write(comment)
            f.write("\n")
        elif comment: # for comment-only
            f.write(comment + "\n")
            # jenkins runs as 0xcustomer,
            # and the file wants to be archived by jenkins who isn't in his group
    permissions = stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP | stat.S_IROTH
    os.chmod(filename, permissions)


def dump_json(j):
    return json.dumps(j, sort_keys=True, indent=2)

# can't have a list of cmds, because cmd is a list
# cmdBefore gets executed first, and we wait for it to complete
def spawn_cmd(name, cmd, capture_output=True, **kwargs):
    if capture_output:
        outfd, outpath = tmp_file(name + '.stdout.', '.log')
        errfd, errpath = tmp_file(name + '.stderr.', '.log')
        # everyone can read
        ps = psutil.Popen(cmd, stdin=None, stdout=outfd, stderr=errfd, **kwargs)
    else:
        outpath = '<stdout>'
        errpath = '<stderr>'
        ps = psutil.Popen(cmd, **kwargs)

    comment = 'PID %d, stdout %s, stderr %s' % (
        ps.pid, os.path.basename(outpath), os.path.basename(errpath))
    log(' '.join(cmd), comment=comment)
    return (ps, outpath, errpath)


def spawn_wait(ps, stdout, stderr, capture_output=True, timeout=None):
    rc = ps.wait(timeout)
    if capture_output:
        out = file(stdout).read()
        err = file(stderr).read()
    else:
        out = 'stdout not captured'
        err = 'stderr not captured'

    if rc is None:
        ps.terminate()
        raise Exception("%s %s timed out after %d\nstdout:\n%s\n\nstderr:\n%s" %
                        (ps.name, ps.cmdline, timeout or 0, out, err))
    elif rc != 0:
        raise Exception("%s %s failed.\nstdout:\n%s\n\nstderr:\n%s" %
                        (ps.name, ps.cmdline, out, err))
    return rc


def spawn_cmd_and_wait(name, cmd, capture_output=True, timeout=None, **kwargs):
    (ps, stdout, stderr) = spawn_cmd(name, cmd, capture_output, **kwargs)
    spawn_wait(ps, stdout, stderr, capture_output, timeout)

def check_h2o_version():
    # assumes you want to know about 3 ports starting at base_port
    command1Split = ['java', '-jar', find_file('target/h2o.jar'), '--version']
    command2Split = ['egrep', '-v', '( Java | started)']
    print "Running h2o to get java version"
    p1 = Popen(command1Split, stdout=PIPE)
    p2 = Popen(command2Split, stdin=p1.stdout, stdout=PIPE)
    output = p2.communicate()[0]
    print output

def setup_random_seed(seed=None):
    # h2o_args.unit_main() or h2o.init() or this function, may be the first to call it
    # that makes sure it's called to setup any --seed init before we look for a 
    # command line arg here. (h2o.setup_random_seed() is done before h2o.init() in tests)
    # parse_our_args() will be a noop if it was already called once
    noseRunning = sys.argv[0].endswith('nosetests')
    if not noseRunning:
        # this will be a no-op if already called once
        h2o_args.parse_our_args()

    if h2o_args.random_seed is not None:
        SEED = h2o_args.random_seed
    elif seed is not None:
        SEED = seed
    else:
        SEED = random.randint(0, sys.maxint)
    random.seed(SEED)
    print "\nUsing random seed:", SEED
    return SEED

