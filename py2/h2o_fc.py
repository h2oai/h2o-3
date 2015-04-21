import time, sys, json, re, getpass, os, shutil, requests, datetime

import h2o_args
import h2o_print as h2p
from h2o_test import get_sandbox_name, check_sandbox_for_errors, dump_json, verboseprint

#*******************************************************************************
# duplicate do_json_request here, because the normal one is a method on a h2o node object which 
# doesn't exist yet. copied this from find_cloud.py
def create_url(addr, port, loc):
    return 'http://%s:%s/%s' % (addr, port, loc)

#*******************************************************************************
def do_json_request(addr=None, port=None,  jsonRequest=None, params=None, timeout=7, **kwargs):
    if params is not None:
        paramsStr =  '?' + '&'.join(['%s=%s' % (k,v) for (k,v) in params.items()])
    else:
        paramsStr = ''

    url = create_url(addr, port, jsonRequest)
    print 'Start ' + url + paramsStr
    try:
        r = requests.get(url, timeout=timeout, params=params, **kwargs)
        # the requests json decoder might fail if we didn't get something good
        rjson = r.json()
        emsg = "ERROR: Probing claimed existing cloud with Cloud.json"
        if not isinstance(rjson, (list,dict)):
            # probably good
            raise Exception(emsg + "h2o json responses should always be lists or dicts. Got %s" %\
                dump_json(rj))
        elif r.status_code != requests.codes.ok:
            rjson = None
            raise Exception(emsg + "Couldn't decode. Status: %s" % r.status_code)

    except requests.ConnectionError, e:
        rjson = None
        emsg = "ERROR: json got ConnectionError or other exception"
        # Rethrow the exception after we've checked for stack trace from h2o.
        # Out of memory errors maybe don't show up right away? 
        # so we should wait for h2o to get it out to h2o stdout. 
        # Don't want to rely on cloud teardown to check because there's no delay, 
        # and we don't want to delay all cloud teardowns by waiting.
        exc_info = sys.exc_info()
        # we don't expect to have connection errors, so any exception is a bad thing.
        h2p.red_print(
            "%s\n %s\n %s\nGoing to check sandbox, then rethrow.." % (emsg, exc_info, url + paramsStr))
        time.sleep(2)
        check_sandbox_for_errors()
        raise exc_info[1], None, exc_info[2]

    # print rjson
    return rjson

#*********************************************************************************************
def create_node(possMember, h2oNodes, expectedSize, hdfsSetup):

    (hdfs_version, hdfs_config, hdfs_name_node) = hdfsSetup

    http_addr, sep, port = possMember.rstrip('\n').partition(":")
    http_addr = http_addr.lstrip('/') # just in case it's an old-school flatfile format with leading /
    if port == '':
        port = '54321'
    if http_addr == '':
        http_addr = '127.0.0.1'
    # print "http_addr:", http_addr, "port:", port

    possMemberList = []
    gc = do_json_request(http_addr, port, '3/Cloud.json', timeout=10)
    if gc is None:
        return possMemberList
        
    version    = gc['version']
    # check to see if it's a h2o-dev version? (common problem when mixing h2o1/h2o-dev testing with --usecloud
# local builds have (unknown)    if not version.startswith('0'):
# local builds have (unknown)        raise Exception("h2o version at node[0] doesn't look like h2o-dev version. (start with 0) %s" % version)

    # we'll just exception out, if we don't get a json response with the stuff that makes up what we think is "legal"
    consensus  = gc['consensus']
    locked     = gc['locked']
    cloud_size = gc['cloud_size']
    cloud_name = gc['cloud_name']
    nodes      = gc['nodes']

    # None means don't check
    if expectedSize and (cloud_size!=expectedSize):
        raise Exception("cloud_size %s at %s disagrees with -expectedSize %s" % \
            (cloud_size, cloud_name, expectedSize))

    print "here's some info about the java heaps in the cloud you said you already built for me"
    print "Also some info about ncpus"
    # will use these for comparing. All should be equal for normal clouds
    java_heap_GB_list = []
    num_cpus_list = []
    name_list = []
    for i, n in enumerate(nodes):
        print "max_mem (GB):", "%0.2f" % ((n['max_mem']+0.0)/(1024*1024*1024))
        print "tot_mem (GB):", "%0.2f" % ((n['tot_mem']+0.0)/(1024*1024*1024))
        java_heap_GB = (n['tot_mem']+0.0)/(1024*1024*1024)
        java_heap_GB = int(round(java_heap_GB,0))
        num_cpus = n['num_cpus']
        print "java_heap_GB:", java_heap_GB
        print 'num_cpus:', num_cpus

        java_heap_GB_list.append(java_heap_GB)
        num_cpus_list.append(num_cpus)

        name = n['h2o'].lstrip('/')
        # print 'name:', name
        ### print dump_json(n)
        name_list.append(name)
        possMemberList.append(name)

        ip, sep, port = name.partition(':')

        # print "ip:", ip
        # print "port:", port
        if not ip or not port:
            raise Exception("bad ip or port parsing from h2o get_cloud nodes 'name' %s" % n['name'])

        # if you have more than one node, compare to the last one
        if i>0:
            lasti = i-1
            if java_heap_GB != java_heap_GB_list[lasti]:
                raise Exception("You have two nodes %s %s with different java heap sizes %s %s. \
                    Assuming that's bad/not your intent)" % \
                    (name[i], name[lasti], java_heap_GB, java_heap_GB_list[lasti]))

            if num_cpus != num_cpus_list[lasti]:
                raise Exception("You have two nodes %s %s with different # of cpus (threads) %s %s. \
                    Assuming that's bad/not your intent)" % \
                    (name[i], name[lasti], num_cpus, num_cpus_list[lasti]))

        # this will be this guys "node id"
        node_id = len(h2oNodes)

        use_maprfs = 'mapr' in hdfs_version
        use_hdfs = not use_maprfs # we default to enabling cdh4 on 172.16.2.176
        node = { 
            'http_addr': ip, 
            'port': int(port),  # print it as a number for the clone ingest
            'java_heap_GB': java_heap_GB,
            # this list is based on what tests actually touch (fail without these)
            'node_id': node_id,
            'remoteH2O': 'true',
            'sandbox_error_was_reported': 'false', # maybe see about changing h2o*py stuff here
            'sandbox_ignore_errors': 'false',
            'username': '0xcustomer', # most found clouds are run by 0xcustomer. This doesn't matter?
            'redirect_import_folder_to_s3_path': 'false', # no..we're not on ec2
            'redirect_import_folder_to_s3n_path': 'false', # no..we're not on ec2
            'delete_keys_at_teardown': 'false', # yes we want each test to clean up after itself. UPDATE: leave as is, for debug for now
            'use_hdfs': use_hdfs,
            'use_maprfs': use_maprfs,
            'h2o_remote_buckets_root': 'false',
            'hdfs_version': hdfs_version, # something is checking for this.
            'hdfs_name_node': hdfs_name_node, # Do we need this for hdfs url generation correctly?
            'hdfs_config': hdfs_config,
            'aws_credentials': 'false',
        }

        # this is the total list so far
        if name not in h2oNodes:
            h2oNodes[name] = node
            print "Added node %s to possMemberList" % name

    # we use this for our one level of recursion
    return possMemberList # might be empty!

#*********************************************************************************************
# returns a json expandedCloud object that should be the same as what -ccj gets after json loads?
# also writes h2o_fc-nodes.json for debug
def find_cloud(ip_port=None,
    expectedSize=1, nodesJsonPathname="h2o_fc-nodes.json",
    hdfs_version='cdh4', hdfs_config=None, hdfs_name_node='172.16.1.176', **kwargs):

    if not ip_port:
        ip_port='localhost:54321'
    # hdfs_config can be the hdfs xml config file
    # hdfs_name_node an be ip, ip:port, hostname, hostname:port", 
    # None on expected size means don't check

    hdfsSetup = (hdfs_version, hdfs_config, hdfs_name_node)
    # partition returns a 3-tuple as (LHS, separator, RHS) if the separator is found, 
    # (original_string, '', '') if the separator isn't found
    possMembersList = [ip_port]

    h2oNodes = {}
    alreadyAdded = set()
    tries = 0
    # we could just take a single node's word on the complete cloud, but this 
    # two layer try is no big deal and gives some checking robustness when a bad cloud exists
    for n1, possMember in enumerate(possMembersList):
        tries += 1
        if possMember not in alreadyAdded:
            possMembersList2 = create_node(possMember, h2oNodes, expectedSize, hdfsSetup)
            alreadyAdded.add(possMember)
            for n2, possMember2 in enumerate(possMembersList2):
                tries += 1
                if possMember2 not in alreadyAdded:
                    create_node(possMember2, h2oNodes, expectedSize, hdfsSetup)
                    alreadyAdded.add(possMember2)

    print "\nDid %s tries" % tries
    print "len(alreadyAdded):", len(alreadyAdded), alreadyAdded

    # get rid of the name key we used to hash to it
    h2oNodesList = [v for k, v in h2oNodes.iteritems()]

    print "Checking for two h2os at same ip address"
    ips = {}
    count = {}
    for h in h2oNodesList:
        # warn for more than 1 h2o on the same ip address
        # error for more than 1 h2o on the same port (something is broke!)
        # but ip+port is how we name them, so that can't happen ehrer
        ip = h['http_addr']
        if ip in ips:
            # FIX! maybe make this a fail exit in the future?
            count[ip] += 1
            print "\nWARNING: appears to be %s h2o's at the same IP address" % count[ip]
            print "initial:", ips[ip]
            print "another:", h, "\n"
        else:
            ips[ip] = h
            count[ip] = 1

    print "Writing", nodesJsonPathname
    # Figure out some stuff about how this test was run
    cs_time = str(datetime.datetime.now())
    cs_cwd = os.getcwd()
    cs_python_cmd_line = "python %s %s" % (h2o_args.python_test_name, h2o_args.python_cmd_args)
    cs_python_test_name = h2o_args.python_test_name
    cs_config_json = nodesJsonPathname
    cs_username = h2o_args.python_username
    cs_ip = h2o_args.python_cmd_ip

    # dump the nodes state to a json file # include enough extra info to have someone
    # rebuild the cloud if a test fails that was using that cloud.
    expandedCloud = {
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
        'h2o_nodes': h2oNodesList
        }

    # just write in current directory?
    with open(nodesJsonPathname, 'w+') as f:
        f.write(json.dumps(expandedCloud, indent=4))

    return expandedCloud

