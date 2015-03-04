import getpass, json, random, os, sys
import h2o_args

from h2o_objects import RemoteHost

# some circular import issues, so go with the full import
import h2o_bc # write_flatfile, get_base_port
import h2o2 as h2o # build_cloud

from h2o_test import verboseprint, clean_sandbox, find_file

def find_config(base):
    f = base
    if not os.path.exists(f): f = 'testdir_hosts/' + base
    if not os.path.exists(f): f = 'py/testdir_hosts/' + base
    if not os.path.exists(f):
        raise Exception("unable to find config %s" % base)
    return f

#************************************************************
def upload_jar_to_remote_hosts(hosts, slow_connection=False):
    def prog(sofar, total):
        # output is bad for jenkins.
        username = getpass.getuser()
        if username != 'jenkins':
            p = int((10.0*sofar)/total)
            sys.stdout.write('\rUploading jar [%s%s] %02d%%' % ('#'*p, ' ' * (10-p), (100*sofar)/total))
            sys.stdout.flush()

    if not slow_connection:
        for h in hosts:
            f = find_file('build/h2o.jar')
            h.upload_file(f, progress=prog)
            # skipping progress indicator for the flatfile
            h.upload_file(h2o_bc.flatfile_pathname())
    else:
        f = find_file('build/h2o.jar')
        hosts[0].upload_file(f, progress=prog)
        hosts[0].push_file_to_remotes(f, hosts[1:])

        f = find_file(h2o_bc.flatfile_pathname())
        hosts[0].upload_file(f, progress=prog)
        hosts[0].push_file_to_remotes(f, hosts[1:])

#************************************************************
# node_count is sometimes used positionally...break that out. all others are keyword args
def build_cloud_with_hosts(node_count=None, **kwargs):
    # legacy: we allow node_count to be positional. 
    # if it's used positionally, stick in in kwargs (overwrite if there too)
    if node_count is not None:
        # we use h2o_per_host in the config file. will translate to node_count for build_cloud
        kwargs['h2o_per_host'] = node_count
        # set node_count to None to make sure we don't use it below. 'h2o_per_host' should be used
        node_count = None

    # for new params:
    # Just update this list with the param name and default and you're done
    allParamsDefault = {
        # any combination of force_ip/network could be interesting
        # network may mean you don't need force_ip
        'force_ip': False,
        'network': None,
        'use_flatfile': False,
        # default to true, so when we flip import folder to hdfs+s3n import on ec2, 
        # the cloud is built correctly
        'use_hdfs': True, 
        'hdfs_name_node': None, 
        'hdfs_config': None,
        'hdfs_version': None,
        'java_heap_GB': None,
        'java_heap_MB': None,
        'java_extra_args': None,

        'timeoutSecs': 60, 
        'retryDelaySecs': 2, 
        'cleanup': True,
        'slow_connection': False,

        'h2o_per_host': 2,
        'ip':'["127.0.0.1"]', # this is for creating the hosts list
        'base_port': None,
        'username':'0xdiag',
        'password': None,
        'rand_shuffle': True,

        'use_home_for_ice': False,
        'key_filename': None,
        'aws_credentials': None,
        'redirect_import_folder_to_s3_path': None,
        'redirect_import_folder_to_s3n_path': None,
        'disable_h2o_log': False,
        'enable_benchmark_log': False,
        'h2o_remote_buckets_root': None,
        'conservative': False,
        'create_json': False,
        # pass this from cloud building to the common "release" h2o_test.py classes
        # for deciding whether keys should be deleted when a test ends.
        'delete_keys_at_teardown': False, 
        'clone_cloud': False,
        'cloud_name': None,
        'force_tcp': None,
        'random_udp_drop': None,
        'sandbox_ignore_errors': None,
    }
    # initialize the default values
    paramsToUse = {}
    for k,v in allParamsDefault.iteritems():
        paramsToUse[k] = allParamsDefault.setdefault(k, v)

    # allow user to specify the config json at the command line. config_json is a global.
    if h2o_args.config_json:
        configFilename = find_config(h2o_args.config_json)
    else:
        # configs may be in the testdir_hosts
        configFilename = find_config(h2o_bc.default_hosts_file())

    verboseprint("Loading host config from", configFilename)
    with open(configFilename, 'rb') as fp:
         hostDict = json.load(fp)

    for k,v in hostDict.iteritems():
        # Don't take in params that we don't have in the list above
        # Because michal has extra params in here for ec2! and comments!
        if k in paramsToUse:
            paramsToUse[k] = hostDict.setdefault(k, v)

    # Now overwrite with anything passed by the test
    # whatever the test passes, always overrules the config json
    for k,v in kwargs.iteritems():
        paramsToUse[k] = kwargs.setdefault(k, v)


    # Let's assume we should set the h2o_remote_buckets_root (only affects
    # schema=local), to the home directory of whatever remote user
    # is being used for the hosts. Better than living with a decision
    # we made from scanning locally (remote might not match local)
    # assume the remote user has a /home/<username> (linux targets?)
    # This only affects import folder path name generation by python tests
    if paramsToUse['username']:
        paramsToUse['h2o_remote_buckets_root'] = "/home/" + paramsToUse['username']

    verboseprint("All build_cloud_with_hosts params:", paramsToUse)

    #********************
    global hosts
    hosts = []
    # Update: special case paramsToUse['ip'] = ["127.0.0.1"] and use the normal build_cloud
    # this allows all tests in testdir_host to be run with a special config that points to 127.0.0.1
    # hosts should be None for everyone if normal build_cloud is desired
    if paramsToUse['ip']== ["127.0.0.1"]:
        hosts = None
    else:
        verboseprint("About to RemoteHost, likely bad ip if hangs")
        hosts = []
        for h in paramsToUse['ip']:
            verboseprint("Connecting to:", h)
            # expand any ~ or ~user in the string
            key_filename = paramsToUse['key_filename']
            if key_filename: # don't try to expand if None
               key_filename=os.path.expanduser(key_filename)
            hosts.append(RemoteHost(addr=h, 
                username=paramsToUse['username'], password=paramsToUse['password'], key_filename=key_filename))

    # done with these, don't pass to build_cloud
    # this was the list of ip's from the config file, replaced by 'hosts' to build_cloud
    paramsToUse.pop('ip') 

    # we want to save username in the node info. don't pop
    # paramsToUse.pop('username')
    paramsToUse.pop('password')
    paramsToUse.pop('key_filename')

    # flatfile is going into sandbox (LOG_DIR) now..so clean it first 
    # (will make sandbox dir if it doesn't exist already)    
    clean_sandbox()

    # handles hosts=None correctly
    base_port = h2o_bc.get_base_port(base_port=paramsToUse['base_port'])

    h2o_bc.write_flatfile(
        node_count=paramsToUse['h2o_per_host'],
        # let the env variable H2O_PORT_OFFSET add in there
        base_port=base_port,
        hosts=hosts,
        rand_shuffle=paramsToUse['rand_shuffle'],
        )

    if hosts is not None:
        # this uploads the flatfile too
        upload_jar_to_remote_hosts(hosts, slow_connection=paramsToUse['slow_connection'])
        # timeout wants to be larger for large numbers of hosts * h2oPerHost
        # use 60 sec min, 5 sec per node.
        timeoutSecs = max(60, 8*(len(hosts) * paramsToUse['h2o_per_host']))
    else: # for 127.0.0.1 case
        timeoutSecs = 60
    paramsToUse.pop('slow_connection')

    # sandbox gets cleaned in build_cloud
    # legacy param issue
    node_count = paramsToUse['h2o_per_host']
    paramsToUse.pop('h2o_per_host')
    print "java_heap_GB", paramsToUse['java_heap_GB']
    # don't wipe out or create the sandbox. already did here, and put flatfile there
    nodes = h2o_bc.build_cloud(node_count, hosts=hosts, init_sandbox=False, **paramsToUse)

    # we weren't doing this before, but since build_cloud returns nodes
    # people might expect this works similarly
    return nodes
