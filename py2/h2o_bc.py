import os, getpass, sys, random, time, datetime, shutil, json, inspect
import h2o_args
import h2o_nodes
import h2o_print as h2p, h2o_util
import h2o_import as h2i


from h2o_test import \
    get_sandbox_name, clean_sandbox, check_sandbox_for_errors, clean_sandbox_doneToLine,\
    verboseprint, OutWrapper, log, flatfile_pathname, dump_json, find_file, check_h2o_version

from h2o_objects import LocalH2O, RemoteH2O, ExternalH2O
import h2o_fc
import h2o_hosts

# print "h2o_bc"
LOG_DIR = get_sandbox_name()

#************************************************************
def default_hosts_file():
    if os.environ.has_key("H2O_HOSTS_FILE"):
        return os.environ["H2O_HOSTS_FILE"]
    return 'pytest_config-{0}.json'.format(getpass.getuser())

#************************************************************
# node_count is number of H2O instances per host if hosts is specified.
# hack: this returns true for the --usecloud/-uc cases, to force it thru
# build_cloud/build_cloud_with_json/find_cloud. also for the -ccj cases
def decide_if_localhost():
    if h2o_args.usecloud:
        print "* Will ask h2o node about cloud using -uc argument:", h2o_args.usecloud
        return True

    if h2o_args.clone_cloud_json:
        print "* Using description of already built cloud",\
            "in JSON you passed as -ccj argument:", h2o_args.clone_cloud_json
        return True

    if h2o_args.config_json:
        print "* Will build cloud using config JSON you passed as -cj argument:", h2o_args.config_json
        return False

    # look for local hosts file
    hostsFile = default_hosts_file()
    if os.path.exists(hostsFile):
        print "* Will build cloud using config JSON file that matches your username",\
            "discovered in this directory: {0}.".format(hostsFile)
        return False

    if 'hosts' in os.getcwd():
        print "Since you're in a *hosts* directory, we're using a config json"
        print "* Expecting default username's config json here. Better exist!"
        return False

    print "No special config control. Building local cloud per test..."
    return True

# used to shift ports when running multiple tests on same machine in parallel (in different shells)
def get_base_port(base_port):
    a = 0
    if os.environ.has_key("H2O_PORT_OFFSET"):
        # this will fail if it's not an integer
        a = int(os.environ["H2O_PORT_OFFSET"])
        # some of the tests select a number 54321, 54323, or 54327,
        # so want to be at least 8 or so apart for multiple test runs.
        # (54321, 54323, 54325 and 54327 are used in testdir_single_jvm)
        # if we're running multi-node with a config json, then obviously the gap needs to be cognizant
        # of the number of nodes
        verboseprint("H2O_PORT_OFFSET", a)
        if a<8 or a>500:
            raise Exception("H2O_PORT_OFFSET % os env variable should be either not set " +
                "or between 8 and 500" % a)

    b = None
    if os.environ.has_key("H2O_PORT"):
        # this will fail if it's not an integer
        b = int(os.environ["H2O_PORT"])
        verboseprint("H2O_PORT", a)
        if b<54321 or b>54999:
            raise Exception("H2O_PORT %s os env variable should be either not set",
                "or between 54321 and 54999." % b)

    if h2o_args.port_from_cmd_line:
        base_port = h2o_args.port_from_cmd_line
    elif b:
        base_port = b
    else:
        if not base_port:
            if getpass.getuser()=='jenkins':
                base_port = 54340
            else:
                base_port = 54321

        if a:
            base_port += a
    return base_port

# I suppose we could shuffle the flatfile order!
# but it uses hosts, so if that got shuffled, we got it covered?
# the i in xrange part is not shuffled. maybe create the list first, for possible random shuffle
# FIX! default to random_shuffle for now..then switch to not.
def write_flatfile(node_count=2, base_port=None, hosts=None, rand_shuffle=True):
    # too bad this must be in two places..here and build_cloud()..could do a global above?
    base_port = get_base_port(base_port)

    # always create the flatfile.
    ports_per_node = 2
    pff = open(flatfile_pathname(), "w+")
    # doing this list outside the loops so we can shuffle for better test variation
    hostPortList = []

    if hosts is None:
        ip = h2o_args.python_cmd_ip
        for i in range(node_count):
            hostPortList.append(ip + ":" + str(base_port + ports_per_node * i))
    else:
        for h in hosts:
            for i in range(node_count):
                # removed leading "/"
                hostPortList.append(h.h2o_addr + ":" + str(base_port + ports_per_node * i))

    # note we want to shuffle the full list of host+port
    if rand_shuffle:
        random.shuffle(hostPortList)
    for hp in hostPortList:
        pff.write(hp + "\n")
    pff.close()


# assume h2o_nodes_json file in the current directory
def build_cloud_with_json(h2o_nodes_json='h2o-nodes.json'):

    # local sandbox may not exist. Don't clean if it does, just append
    if not os.path.exists(LOG_DIR):
        os.mkdir(LOG_DIR)

    log("#*********************************************************************")
    log("Starting new test: " + h2o_args.python_test_name + " at build_cloud_with_json()")
    log("#*********************************************************************")

    print "This only makes sense if h2o is running as defined by", h2o_nodes_json
    print "For now, assuming it's a cloud on this machine, and here's info on h2o processes running here"
    print "No output means no h2o here! Some other info about stuff on the system is printed first though."
    import h2o_os_util

    if not os.path.exists(h2o_nodes_json):
        raise Exception("build_cloud_with_json: Can't find " + h2o_nodes_json + " file")

    ## h2o_os_util.show_h2o_processes()

    with open(h2o_nodes_json, 'rb') as f:
        cloneJson = json.load(f)

    # These are supposed to be in the file.
    # Just check the first one. if not there, the file must be wrong
    if not 'cloud_start' in cloneJson:
        raise Exception("Can't find 'cloud_start' in %s, wrong file? h2o-nodes.json?" % h2o_nodes_json)
    else:
        cs = cloneJson['cloud_start']
        print "Info on the how the cloud we're cloning was started (info from %s)" % h2o_nodes_json
        # required/legal values in 'cloud_start'. A robust check is good for easy debug when we add stuff
        valList = ['time', 'cwd', 'python_test_name', 'python_cmd_line', 'config_json', 'username', 'ip']
        for v in valList:
            if v not in cs:
                raise Exception("Can't find %s in %s, wrong file or version change?" % (v, h2o_nodes_json))
            print "cloud_start['%s']: %s" % (v, cs[v])

        # this is the internal node state for python..nodes rebuild
        nodeStateList = cloneJson['h2o_nodes']

    nodeList = []
    if not nodeStateList:
        raise Exception("nodeStateList is empty. %s file must be empty/corrupt" % h2o_nodes_json)

    try:
        for nodeState in nodeStateList:
            print "Cloning state for node", nodeState['node_id'], 'from', h2o_nodes_json

            newNode = ExternalH2O(nodeState)
            nodeList.append(newNode)

        # If it's an existing cloud, it may already be locked. so never check.
        # we don't have the cloud name in the -ccj since it may change (and the file be static?)
        # so don't check expectedCloudName
        verify_cloud_size(nodeList, expectedCloudName=None, expectedLocked=None)

        # best to check for any errors right away?
        # (we won't report errors from prior tests due to marker stuff?
        ## check_sandbox_for_errors(python_test_name=h2o_args.python_test_name)

        # put the test start message in the h2o log, to create a marker
        nodeList[0].h2o_log_msg()

    except:
        # nodeList might be empty in some exception cases?
        # no shutdown issued first, though

        ## if cleanup and nodeList:
        ##     for n in nodeList: n.terminate()
        check_sandbox_for_errors(python_test_name=h2o_args.python_test_name)
        raise

    # like cp -p. Save the config file, to sandbox
    print "Saving the ", h2o_nodes_json, "we used to", LOG_DIR
    shutil.copy(h2o_nodes_json, LOG_DIR + "/" + os.path.basename(h2o_nodes_json))

    print ""
    h2p.red_print("Ingested from json:",
        nodeList[0].java_heap_GB, "GB java heap(s) with",
        len(nodeList), "total nodes")
    print ""

    # save it to a global copy, in case it's needed for tearDown
    h2o_nodes.nodes[:] = nodeList
    return nodeList

# node_count is per host if hosts is specified.
# don't wrap more than once
stdout_wrapped = False
def build_cloud(node_count=1, base_port=None, hosts=None,
    timeoutSecs=30, retryDelaySecs=1, cleanup=True, rand_shuffle=True,
    conservative=False, create_json=False, clone_cloud=None,
    init_sandbox=True, usecloud=False, usecloud_size=None, **kwargs):

    # expectedSize is only used if usecloud

    # usecloud can be passed thru build_cloud param, or command line
    # not in config json though so no build_cloud_with_hosts path.

    # redirect to build_cloud_with_json if a command line arg
    # wants to force a test to ignore it's build_cloud/build_cloud_with_hosts
    # (both come thru here)
    # clone_cloud is just another way to get the effect (maybe ec2 config file thru
    # build_cloud_with_hosts?
    global stdout_wrapped
    if not h2o_args.disable_time_stamp and not stdout_wrapped:
        sys.stdout = OutWrapper(sys.stdout)
        stdout_wrapped = True

    if h2o_args.usecloud or usecloud:
        # for now, just have fixed name in local file.  (think of this as a temp or debug file)
        # eventually we'll pass the json object instead  for speed?
        nodesJsonPathname = "h2o_fc-nodes.json"

    elif h2o_args.clone_cloud_json:
        nodesJsonPathname = h2o_args.clone_cloud_json

    elif clone_cloud:
        nodesJsonPathname = clone_cloud

    else:
        # normal build_cloud() doesn't use
        nodesJsonPathname = None

    # usecloud dominates over all
    if (h2o_args.clone_cloud_json or clone_cloud) or (h2o_args.usecloud or usecloud):
        # then build_cloud_with_json with json object
        # we don't need to specify these defaults, but leave here to show that we can pass
        # I suppose kwargs will have it
        if h2o_args.usecloud:
            ip_port = h2o_args.usecloud
        elif usecloud:
            ip_port = usecloud
        else:
            ip_port = None

        # h2o_args dominates
        if h2o_args.usecloud_size:
            # only used for expected size
            useCloudExpectedSize = h2o_args.usecloud_size
        else:
            useCloudExpectedSize = usecloud_size

        if (h2o_args.usecloud or usecloud):
            nodesJsonObject = h2o_fc.find_cloud(ip_port=ip_port,
                expectedSize=useCloudExpectedSize, nodesJsonPathname=nodesJsonPathname, **kwargs)
                # potentially passed in kwargs
                # hdfs_version='cdh4', hdfs_config=None, hdfs_name_node='172.16.1.176',
        else:
            if h2o_args.clone_cloud_json:
                nodesJsonPathname = h2o_args.clone_cloud_json
            else:
                nodesJsonPathname = clone_cloud

        nodeList = build_cloud_with_json(h2o_nodes_json=nodesJsonPathname)
        return nodeList

    # else
    # moved to here from unit_main. so will run with nosetests too!
    # Normally do this.
    # Don't if build_cloud_with_hosts() did and put a flatfile in there already!
    if init_sandbox:
        clean_sandbox()

    log("#*********************************************************************")
    log("Starting new test: " + h2o_args.python_test_name + " at build_cloud() ")
    log("#*********************************************************************")

    # start up h2o to report the java version (once). output to python stdout
    # only do this for regression testing

    # temporarily disable this, to go a little faster
    #    if getpass.getuser() == 'jenkins':
    #        check_h2o_version()

    ports_per_node = 2
    nodeList = []
    # shift the port used to run groups of tests on the same machine at the same time?
    base_port  = get_base_port(base_port)

    try:
        # if no hosts list, use psutil method on local host.
        totalNodes = 0
        # doing this list outside the loops so we can shuffle for better test variation
        # this jvm startup shuffle is independent from the flatfile shuffle
        portList = [base_port + ports_per_node * i for i in range(node_count)]
        if hosts is None:
            # if use_flatfile, we should create it
            # because tests will just call build_cloud with use_flatfile=True
            # best to just create it all the time..may or may not be used
            write_flatfile(node_count=node_count, base_port=base_port)
            hostCount = 1
            if rand_shuffle:
                random.shuffle(portList)
            for p in portList:
                verboseprint("psutil starting node", i)
                newNode = LocalH2O(port=p, node_id=totalNodes, **kwargs)
                nodeList.append(newNode)
                totalNodes += 1
        else:
            # if hosts, the flatfile was created and uploaded to hosts already
            # I guess don't recreate it, don't overwrite the one that was copied beforehand.
            # we don't always use the flatfile (use_flatfile=False)
            # Suppose we could dispatch from the flatfile to match it's contents
            # but sometimes we want to test with a bad/different flatfile then we invoke h2o?
            hostCount = len(hosts)
            hostPortList = []
            for h in hosts:
                for port in portList:
                    hostPortList.append((h, port))
            if rand_shuffle: random.shuffle(hostPortList)
            for (h, p) in hostPortList:
                verboseprint('ssh starting node', totalNodes, 'via', h)
                newNode = h.remote_h2o(port=p, node_id=totalNodes, **kwargs)
                nodeList.append(newNode)
                totalNodes += 1

        verboseprint("Attempting Cloud stabilize of", totalNodes, "nodes on", hostCount, "hosts")
        start = time.time()
        # UPDATE: best to stabilize on the last node!
        # FIX! for now, always check sandbox, because h2oddev has TIME_WAIT port problems
        stabilize_cloud(nodeList[0], nodeList,
            timeoutSecs=timeoutSecs, retryDelaySecs=retryDelaySecs, noExtraErrorCheck=False)
        stabilizeTime = time.time() - start
        verboseprint(len(nodeList), "Last added node stabilized in ", stabilizeTime, " secs")

        # assume all the heap sizes are the same as zero
        if nodeList[0].java_heap_GB:
            heapSize = str(nodeList[0].java_heap_GB) + " GB"
        elif nodeList[0].java_heap_GB:
            heapSize = str(nodeList[0].java_heap_MB) + " MB"
        else:
            heapSize = "(unknown)"

        h2p.red_print("Built cloud: %s java heap(s) with %d nodes on %d hosts, stabilizing in %d secs" % \
            (heapSize, len(nodeList), hostCount, stabilizeTime))

        # FIX! using "consensus" in node[-1] should mean this is unnecessary?
        # maybe there's a bug. For now do this. long term: don't want?
        # UPDATE: do it for all cases now 2/14/13
        if conservative: # still needed?
            for n in nodeList:
                # FIX! for now, always check sandbox, because h2oddev has TIME_WAIT port problems
                stabilize_cloud(n, nodeList, timeoutSecs=timeoutSecs, noExtraErrorCheck=False)

        # this does some extra checking now
        # verifies cloud name too if param is not None
        verify_cloud_size(nodeList, expectedCloudName=nodeList[0].cloud_name, expectedLocked=0)

        # FIX! should probably check that the cloud's lock=0. It will go to 1 later.
        # but if it's an existing cloud, it may already be locked.
        # That will be in build_cloud_with_json, though

        # best to check for any errors due to cloud building right away?
        check_sandbox_for_errors(python_test_name=h2o_args.python_test_name)

        # put the test start message in the h2o log, to create a marker
        nodeList[0].h2o_log_msg()

    except:
        # nodeList might be empty in some exception cases?
        # no shutdown issued first, though
        if cleanup and nodeList:
            for n in nodeList: n.terminate()
        check_sandbox_for_errors(python_test_name=h2o_args.python_test_name)
        raise

    print len(nodeList), "total jvms in H2O cloud"

    if h2o_args.config_json:
        # like cp -p. Save the config file, to sandbox
        print "Saving the ", h2o_args.config_json, "we used to", LOG_DIR
        shutil.copy(h2o_args.config_json, LOG_DIR + "/" + os.path.basename(h2o_args.config_json))

    if create_json:
        # Figure out some stuff about how this test was run
        cs_time = str(datetime.datetime.now())
        cs_cwd = os.getcwd()
        cs_python_cmd_line = "python %s %s" % (h2o_args.python_test_name, h2o_args.python_cmd_args)
        cs_python_test_name = h2o_args.python_test_name
        if h2o_args.config_json:
            cs_config_json = os.path.abspath(h2o_args.config_json)
        else:
            cs_config_json = None
        cs_username = h2o_args.python_username
        cs_ip = h2o_args.python_cmd_ip

        # dump the nodes state to a json file # include enough extra info to have someone
        # rebuild the cloud if a test fails that was using that cloud.
        q = {
            'cloud_start':
                {
                    'time': cs_time,
                    'cwd': cs_cwd,
                    'python_test_name': cs_python_test_name,
                    'python_cmd_line': cs_python_cmd_line,
                    'config_json': cs_config_json,
                    'username': cs_username,
                    'ip': cs_ip,
                },
            'h2o_nodes': h2o_util.json_repr(nodeList),
        }

        with open('h2o-nodes.json', 'w+') as f:
            f.write(json.dumps(q, indent=4))

    # save it to a local global copy, in case it's needed for tearDown
    h2o_nodes.nodes[:] = nodeList
    return nodeList

# final overrides the disable --usecloud causues
def tear_down_cloud(nodeList=None, sandboxIgnoreErrors=False, force=False):
    if h2o_args.sleep_at_tear_down:
        print "Opening browser to cloud, and sleeping for 3600 secs, before cloud teardown (for debug)"
        import h2o_browse as h2b
        h2b.browseTheCloud()
        sleep(3600)

    if not nodeList: nodeList = h2o_nodes.nodes

    # this could fail too. Should this be set by -uc/--usecloud? or command line argument
    if nodeList and nodeList[0].delete_keys_at_teardown:
        start = time.time()
        h2i.delete_keys_at_all_nodes(timeoutSecs=300)
        elapsed = time.time() - start
        print "delete_keys_at_all_nodes(): took", elapsed, "secs"

    # could the nodeList still be empty in some exception cases? Assume not for now

    # FIX! don't send shutdown if we're using an existing cloud
    # also, copy the "delete keys at teardown from testdir_release
    # Assume there's a last "test" that's run to shutdown the cloud

    # don't tear down with -ccj either
    # FIX! what about usecloud or cloud_cloud_json params from build_cloud time?
    if force or not (h2o_args.usecloud or h2o_args.clone_cloud_json):
        try:
            # update: send a shutdown to all nodes. 
            # h2o maybe doesn't progagate well if sent to one node
            # the api watchdog shouldn't complain about this?
            # just send one?

            # for n in nodeList:
            #     n.shutdown_all()
            h2o_nodes.nodes[0].shutdown_all()
        except:
            pass

        # ah subtle. we might get excepts in issuing the shutdown, don't abort out
        # of trying the process kills if we get any shutdown exception (remember we go to all nodes)
        # so we might? nodes are shutting down?
        # FIX! should we wait a bit for a clean shutdown, before we process kill?
        # It can take more than 1 sec though.
        try:
            time.sleep(2)
            for n in nodeList:
                n.terminate()
                verboseprint("tear_down_cloud n:", n)
        except:
            pass

    check_sandbox_for_errors(sandboxIgnoreErrors=sandboxIgnoreErrors, python_test_name=h2o_args.python_test_name)
    # get rid of all those pesky line marker files. Unneeded now
    clean_sandbox_doneToLine()
    nodeList[:] = []
    h2o_nodes.nodes = []
    # we can't destroy the copy in h2o.nodes? (circular). He's responsible for that
    # h2o.nodes = []


# don't need any more?
# Used before to make sure cloud didn't go away between unittest defs
def touch_cloud(nodeList=None):
    if not nodeList: nodeList = h2o_nodes.nodes
    for n in nodeList:
        n.is_alive()

# timeoutSecs is per individual node get_cloud()
# verify cloud name if cloudName provided
def verify_cloud_size(nodeList=None, expectedCloudName=None, expectedLocked=None, verbose=False,
    timeoutSecs=10, ignoreHealth=True):

    if not nodeList: nodeList = h2o_nodes.nodes
    expectedSize = len(nodeList)
    # cloud size and consensus have to reflect a single grab of information from a node.
    cloudStatus = [n.get_cloud(timeoutSecs=timeoutSecs) for n in nodeList]

    cloudSizes = [(c['cloud_size']) for c in cloudStatus]
    cloudConsensus = [c['consensus'] for c in cloudStatus]
    cloudName = [c['cloud_name'] for c in cloudStatus]
    cloudLocked = [c['locked'] for c in cloudStatus]
    cloudVersion = [c['version'] for c in cloudStatus]

    # all match 0?
    # if "(unknown)" starts appearing in version..go to h2o1 h2o_bc.py/h2o_fc.py/h2o_methods.py and copy allowing.
    expectedVersion = cloudVersion[0]
    # check to see if it's a h2o-dev version? (common problem when mixing h2o1/h2o-dev testing with --usecloud
    # local builds have (unknown) in h2o if you build.sh (instead of make)
    # gradle builds should always be right with version?
    if not expectedVersion.startswith('3'):
        raise Exception("h2o version at node[0] doesn't look like h2o-dev version. (start with 3) %s" % 
            expectedVersion)

    for i, v in enumerate(cloudVersion):
        if v != expectedVersion:
            versionStr = (",".join(map(str, cloudVersion)))
            raise Exception("node %s. Inconsistent cloud version. nodeList report version: %s" % 
                (i, versionStr))

    if not ignoreHealth:
        for c in cloudStatus:
            if 'cloud_healthy' not in c:
                raise Exception("cloud_healthy missing: %s" % dump_json(c))

        cloudHealthy = [c['cloud_healthy'] for c in cloudStatus]
        if not all(cloudHealthy):
            msg = "Some node reported cloud_healthy not true: %s" % cloudHealthy
            raise Exception(msg)

    # gather up all the node_healthy status too
    for i, c in enumerate(cloudStatus):
        nodesHealthy = [n['healthy'] for n in c['nodes']]
        if not all(nodesHealthy):
            print "node %s cloud status: %s" % (i, dump_json(c))
            msg = "node %s says some node is not reporting node_healthy: %s" % (c['cloud_name'], nodesHealthy)
            if not ignoreHealth:
                raise Exception(msg)

    if expectedSize == 0 or len(cloudSizes) == 0 or len(cloudConsensus) == 0:
        print "\nexpectedSize:", expectedSize
        print "cloudSizes:", cloudSizes
        print "cloudConsensus:", cloudConsensus
        raise Exception("Nothing in cloud. Can't verify size")

    consensusStr = (",".join(map(str, cloudConsensus)))
    sizeStr = (",".join(map(str, cloudSizes)))
    for s in cloudSizes:
        if s != expectedSize:
            raise Exception("Inconsistent cloud size. nodeList report size: %s consensus: %s instead of %d." %
               (sizeStr, consensusStr, expectedSize))

    # check that all cloud_names are right
    if expectedCloudName:
        for i, cn in enumerate(cloudName):
            if cn != expectedCloudName:
                print "node %s has the wrong cloud name: %s expectedCloudName: %s." % (i, cn, expectedCloudName)
                # print "node %s cloud status: %s" % (i, dump_json(cloudStatus[i]))
                # tear everyone down, in case of zombies. so we don't have to kill -9 manually
                print "tearing cloud down"
                tear_down_cloud(nodeList=nodeList, sandboxIgnoreErrors=False)
                raise Exception("node %s has the wrong cloud name: %s expectedCloudName: %s" % \
                    (i, cn, expectedCloudName))

    # check that all locked are right
    if expectedLocked:
        for i, cl in enumerate(cloudLocked):
            if cl != expectedLocked:
                print "node %s has the wrong locked: %s expectedLocked: %s." % (i, cl, expectedLocked)
                # print "node %s cloud status: %s" % (i, dump_json(cloudStatus[i]))
                # tear everyone down, in case of zombies. so we don't have to kill -9 manually
                print "tearing cloud down"
                tear_down_cloud(nodeList=nodeList, sandboxIgnoreErrors=False)
                raise Exception("node %s has the wrong locked: %s expectedLocked: %s" % (i, cn, expectedLocked))

    return (sizeStr, consensusStr, expectedSize)


def stabilize_cloud(node, nodeList, timeoutSecs=14.0, retryDelaySecs=0.25, noExtraErrorCheck=False):
    node_count = len(nodeList)

    # want node saying cloud = expected size, plus thinking everyone agrees with that.
    def test(n, tries=None, timeoutSecs=14.0):
        c = n.get_cloud(noExtraErrorCheck=noExtraErrorCheck, timeoutSecs=timeoutSecs)

        # FIX! unique to h2o-dev for now, because of the port reuse problems (TCP_WAIT) compared to h2o
        # flag them early rather than after timeout
        check_sandbox_for_errors(python_test_name=h2o_args.python_test_name)

        # don't want to check everything. But this will check that the keys are returned!
        consensus = c['consensus']
        locked = c['locked']
        cloud_size = c['cloud_size']
        cloud_name = c['cloud_name']

        if 'nodes' not in c:
            emsg = "\nH2O didn't include a list of nodes in get_cloud response after initial cloud build"
            raise Exception(emsg)

        # only print it when you get consensus
        if cloud_size != node_count:
            print "\nNodes in cloud while building:"
            for i,ci in enumerate(c['nodes']):
                # 'h2o' disappeared?
                if 'h2o' not in ci:
                    print "ci:", dump_json(ci)
                    # apparently this can happen in cases where I didn't join a cloud because 
                    # of a different md5 version. We'll eventually exception out?
                    # raise Exception("What happened to the 'h2o' ci dict entry?, not there")
                else:
                    print "node %s" % i, ci['h2o']
                    ### print "node %s" % i, ci['h2o']['node']

        if cloud_size > node_count:
            emsg = (
                "\n\nERROR: cloud_size: %d reported via json is bigger than we expect: %d" % \
                    (cloud_size, node_count) +
                "\nLikely have zombie(s) with the same cloud name on the network." +
                "\nLook at the cloud IP's in 'grep Paxos sandbox/*stdout*' for some IP's you didn't expect." +
                "\n\nYou probably don't have to do anything, as the cloud shutdown in this test should" +
                "\nhave sent a Shutdown.json to all in that cloud (you'll see a kill -2 in the *stdout*)." +
                "\nIf you try again, and it still fails, go to those IPs and kill the zombie h2o's." +
                "\nIf you think you really have an intermittent cloud build, report it." +
                "\n" +
                "\nbuilding cloud size of 2 with 127.0.0.1 may temporarily report 3 incorrectly," +
                "\nwith no zombie?"
            )
            for ci in c['nodes']:
                emsg += "\n" + ci['h2o']['node']
            raise Exception(emsg)

        a = (cloud_size == node_count) and consensus
        if a:
            verboseprint("\tLocked won't happen until after keys are written")
            verboseprint("\nNodes in final cloud:")
            for ci in c['nodes']:
                verboseprint("ci", ci)
                # this isn't in there all the time?
                # verboseprint(ci['h2o']['node'])

        return a

    # wait to talk to the first one
    node.wait_for_node_to_accept_connections(nodeList,
        timeoutSecs=timeoutSecs, noExtraErrorCheck=True)

    # then wait till it says the cloud is the right size
    node.stabilize(test, error=('trying to build cloud of size %d' % node_count),
         timeoutSecs=timeoutSecs, retryDelaySecs=retryDelaySecs)

#*************************************************************************************
def init(*args, **kwargs):
    localhost = decide_if_localhost()
    if localhost:
        nodes = build_cloud(*args, **kwargs)
    else:
        nodes = h2o_hosts.build_cloud_with_hosts(*args, **kwargs)
    return nodes
