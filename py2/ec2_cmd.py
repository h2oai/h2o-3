#!/usr/bin/python

import argparse
import boto
import os, time, sys, socket
import h2o_cmd
import h2o2 as h2o
import h2o_hosts
import json
import commands
import traceback

'''
    Simple EC2 utility:
     
       * to setup clooud of 5 nodes: ./ec2_cmd.py create --instances 5
       * to terminated the cloud   : ./ec2_cmd.py terminate --hosts <host file returned by previous command>
'''

DEFAULT_NUMBER_OF_INSTANCES = 4
DEFAULT_HOSTS_FILENAME = 'ec2-config-{0}.json'
DEFAULT_REGION = 'us-east-1'
DEFAULT_INSTANCE_NAME='node_{0}'.format(os.getenv('USER'))
ADVANCED_SSH_OPTIONS='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o GlobalKnownHostsFile=/dev/null'

'''
Default EC2 instance setup
'''
DEFAULT_EC2_INSTANCE_CONFIGS = {
  'us-east-1':{
              'image_id'        : 'ami-cf5132a6', #'ami-30c6a059', #'ami-b85cc4d1', # 'ami-cd9a11a4',
              'security_groups' : [ 'MrJenkinsTest' ],
              'key_name'        : 'mrjenkins_test',
              'instance_type'   : 'm1.xlarge',
              'region'          : 'us-east-1',
              'pem'             : '~/.ec2/keys/mrjenkins_test.pem',
              'username'        : '0xdiag',      
              'aws_credentials' : '~/.ec2/AwsCredentials.properties',
              'hdfs_config'     : '~/.ec2/core-site.xml',
             },
  'us-west-1':{
              'image_id'        : 'ami-a6cbe6e3', # 'ami-cd9a11a4',
              'security_groups' : [ 'MrJenkinsTest' ],
              'key_name'        : 'mrjenkins_test',
              'instance_type'   : 'm1.xlarge',
              'pem'             : '~/.ec2/keys/mrjenkins_test.pem',
              'username'        : '0xdiag',      
              'aws_credentials' : '~/.ec2/AwsCredentials.properties',
              'hdfs_config'     : '~/.ec2/core-site.xml',
              'region'          : 'us-west-1',
             },
}

''' Memory mappings for instance kinds '''
MEMORY_MAPPING = {
    'm1.small'     : { 'xmx' : 1  },
    'm1.medium'    : { 'xmx' : 3  },
    'm1.large'     : { 'xmx' : 5  },  # $0.24/hr
    'm1.xlarge'    : { 'xmx' : 12 },  # $0.48/hr
    'm2.xlarge'    : { 'xmx' : 13 },  # $0.41/hr
    'm2.2xlarge'   : { 'xmx' : 30 },  # $0.82/hr
    'm2.4xlarge'   : { 'xmx' : 64 },  # $1.64/hr
    'm3.medium'    : { 'xmx' : 3  },
    'm3.large'     : { 'xmx' : 5  },
    'm3.xlarge'    : { 'xmx' : 12 },  # $0.50/hr
    'm3.2xlarge'   : { 'xmx' : 26 },  # $1.00/hr
    'c1.medium'    : { 'xmx' : 1  },
    'c1.xlarge'    : { 'xmx' : 6  },  # $0.58/hr
    'c3.large'     : { 'xmx' : 3  },
    'c3.xlarge'    : { 'xmx' : 5  },  # $0.30/hr
    'c3.2xlarge'   : { 'xmx' : 12 },
    'c3.4xlarge'   : { 'xmx' : 27 },
    'c3.8xlarge'   : { 'xmx' : 56 },
    'cc2.8xlarge'  : { 'xmx' : 56 },
    'hi1.4xlarge'  : { 'xmx' : 56 },  # $3.10/hr 60.5GB dram. 8 cores/8 threads. 2TB SSD. 10GE
    'cr1.8xlarge'  : { 'xmx' : 230},  # $4.60/hr 244GB dram. 2 E5-2670. 240GB SSD. 10GE
    'g2.2xlarge'   : { 'xmx' : 12 },
    'i2.xlarge'    : { 'xmx' : 27 },
    'i2.2xlarge'   : { 'xmx' : 57 },
    'cg1.4xlarge'  : { 'xmx' : 19 },
    'i2.4xlarge'   : { 'xmx' : 116},
    'hs1.8xlarge'  : { 'xmx' : 112},
    'i2.8xlarge'   : { 'xmx' : 236},
}

''' EC2 API default configuration. The corresponding values are replaces by EC2 user config. '''
EC2_API_RUN_INSTANCE = {
'image_id'        :None,
'min_count'       :1,
'max_count'       :1,
'key_name'        :None,
'security_groups' :None,
'user_data'       :None,
'addressing_type' :None,
'instance_type'   :None,
'placement'       :None,
'monitoring_enabled':False,
'subnet_id'       :None,
'block_device_map':None,
'disable_api_termination':False,
'instance_initiated_shutdown_behavior':None
}

def inheritparams(parent, kid):
    newkid = {}
    for k,v in kid.items():
        if parent.has_key(k):
            newkid[k] = parent[k]

    return newkid

def find_file(base):
    f = base
    if not os.path.exists(f): f = os.path.expanduser(base)
    if not os.path.exists(f): f = os.path.expanduser("~")+ '/' + base
    if not os.path.exists(f):
        return None
    return f

''' Returns a boto connection to given region ''' 
def ec2_connect(region):
    check_required_env_variables()
    import boto.ec2
    conn = boto.ec2.connect_to_region(region)
    if not conn:
        raise Exception("\033[91m[ec2] Cannot create EC2 connection into {0} region!\033[0m".format(region))

    return conn

def check_required_env_variables():
    ok = True
    if not os.environ['AWS_ACCESS_KEY_ID']: 
        warn("AWS_ACCESS_KEY_ID need to be defined!")
        ok = False
    if not os.environ['AWS_SECRET_ACCESS_KEY']:
        warn("AWS_SECRET_ACCESS_KEY need to be defined!")
        ok = False

    if not ok: raise Exception("\033[91m[ec2] Missing AWS environment variables!\033[0m")

''' Run number of EC2 instance.
Waits forthem and optionaly waits for ssh service.
'''
def run_instances(count, ec2_config, region, waitForSSH=True, tags=None):
    '''Create a new reservation for count instances'''

    ec2params = inheritparams(ec2_config, EC2_API_RUN_INSTANCE)
    ec2params.setdefault('min_count', count)
    ec2params.setdefault('max_count', count)

    reservation = None
    conn = ec2_connect(region)
    try:
        reservation = conn.run_instances(**ec2params)
        log('Reservation: {0}'.format(reservation.id))
        log('Waiting for {0} EC2 instances {1} to come up, this can take 1-2 minutes.'.format(len(reservation.instances), reservation.instances))
        start = time.time()
        time.sleep(1)
        for instance in reservation.instances:
            while instance.update() == 'pending':
               time.sleep(1)
               h2o_cmd.dot()

            if not instance.state == 'running':
                raise Exception('\033[91m[ec2] Error waiting for running state. Instance is in state {0}.\033[0m'.format(instance.state))

        log('Instances started in {0} seconds'.format(time.time() - start))
        log('Instances: ')
        for inst in reservation.instances: log("   {0} ({1}) : public ip: {2}, private ip: {3}".format(inst.public_dns_name, inst.id, inst.ip_address, inst.private_ip_address))
        
        if waitForSSH:
            # kbn: changing to private address, so it should fail if not in right domain
            # used to have the public ip address
            wait_for_ssh([ i.private_ip_address for i in reservation.instances ])

        # Tag instances
        try:
            if tags:
                conn.create_tags([i.id for i in reservation.instances], tags)                        
        except:
            warn('Something wrong during tagging instances. Exceptions IGNORED!')
            print sys.exc_info()
            pass

        return reservation
    except:
        print "\033[91mUnexpected error\033[0m :", sys.exc_info()
        if reservation:
            terminate_reservation(reservation, region)
        raise

''' Wait for ssh port 
'''
def ssh_live(ip, port=22):
    return h2o_cmd.port_live(ip,port)

def terminate_reservation(reservation, region):
    terminate_instances([ i.id for i in reservation.instances ], region)

def terminate_instances(instances, region):
    '''terminate all the instances given by its ids'''
    if not instances: return
    conn = ec2_connect(region)
    log("Terminating instances {0}.".format(instances))
    conn.terminate_instances(instances)
    log("Done")

def stop_instances(instances, region):
    '''stop all the instances given by its ids'''
    if not instances: return
    conn = ec2_connect(region)
    log("Stopping instances {0}.".format(instances))
    conn.stop_instances(instances)
    log("Done")

def start_instances(instances, region):
    '''Start all the instances given by its ids'''
    if not instances: return
    conn = ec2_connect(region)
    log("Starting instances {0}.".format(instances))
    conn.start_instances(instances)
    log("Done")

def reboot_instances(instances, region):
    '''Reboot all the instances given by its ids'''
    if not instances: return
    conn = ec2_connect(region)
    log("Rebooting instances {0}.".format(instances))
    conn.reboot_instances(instances)
    log("Done")

def wait_for_ssh(ips, port=22, skipAlive=True, requiredsuccess=3):
    ''' Wait for ssh service to appear on given hosts'''
    log('Waiting for SSH on following hosts: {0}'.format(ips))
    for ip in ips:
        if not skipAlive or not ssh_live(ip, port): 
            log('Waiting for SSH on instance {0}...'.format(ip))
            count = 0
            while count < requiredsuccess:
                if ssh_live(ip, port):
                    count += 1
                else:
                    count = 0
                time.sleep(1)
                h2o_cmd.dot()


def dump_hosts_config(ec2_config, reservation, filename=DEFAULT_HOSTS_FILENAME, save=True, h2o_per_host=1):
    if not filename: filename=DEFAULT_HOSTS_FILENAME

    cfg = {}
    f = find_file(ec2_config['aws_credentials'])
    if f: cfg['aws_credentials'] = f
    else: warn_file_miss(ec2_config['aws_credentials'])
    f = find_file(ec2_config['pem'])
    if f: cfg['key_filename'] = f
    else: warn_file_miss(ec2_config['key_filename'])
    f = find_file(ec2_config['hdfs_config'])
    if f: cfg['hdfs_config']  = f
    else: warn_file_miss(ec2_config['hdfs_config'])
    cfg['username']        = ec2_config['username'] 
    cfg['use_flatfile']    = True
    cfg['h2o_per_host']    = h2o_per_host
    cfg['java_heap_GB']    = MEMORY_MAPPING[ec2_config['instance_type']]['xmx']
    cfg['java_extra_args'] = '' # No default Java arguments '-XX:MaxDirectMemorySize=1g'
    cfg['base_port']       = 54321
    cfg['ip'] = [ i.private_ip_address for i in reservation.instances ]
    cfg['ec2_instances']   = [ { 'id': i.id, 'private_ip_address': i.private_ip_address, 'public_ip_address': i.ip_address, 'public_dns_name': i.public_dns_name } for i in reservation.instances ]
    cfg['ec2_reservation_id']  = reservation.id
    cfg['ec2_region']      = ec2_config['region']
    # cfg['redirect_import_folder_to_s3_path'] = True
    # New! we can redirect import folder to s3n thru hdfs, now (ec2)
    # cfg['redirect_import_folder_to_s3n_path'] = True
    # kbn 9/28/14..change it back to s3 to see what breaks
    cfg['redirect_import_folder_to_s3_path'] = True
    # put ssh commands into comments
    cmds = get_ssh_commands(ec2_config, reservation)
    idx  = 1
    for cmd in cmds: 
        cfg['ec2_comment_ssh_{0}'.format(idx)] = cmd
        idx += 1

    if save:
        # save config
        filename = filename.format(reservation.id)
        with open(filename, 'w+') as f:
            f.write(json.dumps(cfg, indent=4))

        log("Host config dumped into {0}".format(filename))
        log("To terminate instances call:")
        log("\033[93mpython ec2_cmd.py terminate --hosts {0}\033[0m".format(filename))
        log("To start H2O cloud call:")
        log("\033[93mpython ec2_cmd.py start_h2o --hosts {0}\033[0m".format(filename))
        log("To watch cloud in browser follow address:")
        log("   http://{0}:{1}".format(reservation.instances[0].public_dns_name, cfg['base_port']))

    return (cfg, filename)

def get_ssh_commands(ec2_config, reservation, ssh_options=""):
    cmds = []
    if not ssh_options: ssh_options = ""
    for i in reservation.instances:
        cmds.append( "ssh -i ~/.ec2/keys/mrjenkins_test.pem {1} ubuntu@{0}".format(i.private_ip_address,ssh_options) )
    return cmds

def dump_ssh_commands(ec2_config, reservation):
    cmds = get_ssh_commands(ec2_config, reservation)
    for cmd in cmds:
        print cmd 

# for cleaning /tmp after it becomes full, or any one string command (can separate with semicolon)
def execute_using_ssh_commands(ec2_config, reservation, command_string='df'):
    if not command_string: log("Nothing to execute. Exiting...")
    cmds = get_ssh_commands(ec2_config, reservation, ADVANCED_SSH_OPTIONS)
    for cmd in cmds:
        c = cmd + " '" + command_string + "'"
        print "\n"+c
        ret,out = commands.getstatusoutput(c)
        print out

def load_ec2_region(region):
    for r in DEFAULT_EC2_INSTANCE_CONFIGS:
        if r == region:
            return region

    raise Exception('\033[91m[ec2] Unsupported EC2 region: {0}. The available regions are: {1}\033[0m'.format(region, [r for r in DEFAULT_EC2_INSTANCE_CONFIGS ]))

def load_ec2_config(config_file, region, instance_type=None, image_id=None):
    if config_file:
        f = find_file(config_file)
        with open(f, 'rb') as fp:
             ec2_cfg = json.load(fp)
    else:
        ec2_cfg = {}

    for k,v in DEFAULT_EC2_INSTANCE_CONFIGS[region].items():
        ec2_cfg.setdefault(k, v)

    if instance_type: ec2_cfg['instance_type'] = instance_type
    if image_id     : ec2_cfg['image_id'     ] = image_id

    return ec2_cfg

def load_ec2_reservation(reservation, region):
    conn = ec2_connect(region)
    lr   = [ r for r in conn.get_all_instances() if r.id == reservation ]
    if not lr: raise Exception('Reservation id {0} not found !'.format(reservation))

    return lr[0]

def load_hosts_config(config_file):
    f = find_file(config_file)
    with open(f, 'rb') as fp:
         host_cfg = json.load(fp)
    return host_cfg

def log(msg):
    print "\033[92m[ec2] \033[0m", msg

def warn(msg):
    print "\033[92m[ec2] \033[0m \033[91m{0}\033[0m".format(msg)

def warn_file_miss(f):
    warn("File {0} is missing! Please update the generated config manually.".format(f))

def invoke_hosts_action(action, hosts_config, args, ec2_reservation=None):
    ids = [ inst['id'] for inst in hosts_config['ec2_instances'] ]
    ips = [ inst['private_ip_address'] for inst in hosts_config['ec2_instances'] ]
    region = hosts_config['ec2_region']

    if (action == 'terminate'):
        terminate_instances(ids, region)
    elif (action == 'stop'):
        stop_instances(ids, region)
    elif (action == 'reboot'):
        reboot_instances(ids, region)
        wait_for_ssh(ips, skipAlive=False, requiredsuccess=10)
    elif (action == 'start'):
        start_instances(ids, region)
        # FIXME after start instances receive new IPs: wait_for_ssh(ips)
    elif (action == 'distribute_h2o'):
        pass
    elif (action == 'start_h2o'):
        try:
            h2o.config_json = args.hosts
            log("Starting H2O cloud...")
            h2o_hosts.build_cloud_with_hosts(timeoutSecs=120, retryDelaySecs=5)
            h2o.touch_cloud()
            log("Cloud started. Let's roll!")
            log("You can start for example here \033[93mhttp://{0}:{1}\033[0m".format(hosts_config['ec2_instances'][0]['public_dns_name'],hosts_config['base_port']))
            if args.timeout: 
                log("Cloud will shutdown after {0} seconds or use Ctrl+C to shutdown it.".format(args.timeout))
                time.sleep(args.timeout)
            else: 
                log("To kill the cloud please use Ctrl+C as usual.")
                while (True): time.sleep(3600)
        except:
            print traceback.format_exc()
        finally:
            log("Goodbye H2O cloud...")
            h2o.tear_down_cloud()
            log("Cloud is gone.")
 
    elif (action == 'stop_h2o'):
        pass
    elif (action == 'clean_tmp'):
        execute_using_ssh_commands(hosts_config, ec2_reservation, command_string='sudo rm -rf /tmp/*; df')
    elif (action == 'nexec'):
        execute_using_ssh_commands(hosts_config, ec2_reservation, command_string=args.cmd)

def report_reservations(region, reservation_id=None):
    conn = ec2_connect(region)
    reservations = conn.get_all_instances()
    if reservation_id: reservations = [i for i in reservations if i.id == reservation_id ]
    log('Reservations:')
    for r in reservations: report_reservation(r); log('')

def report_reservation(r):
    log('  Reservation : {0}'.format(r.id))
    log('  Instances   : {0}'.format(len(r.instances)))
    for i in r.instances:
        log('    [{0} : {4}] {5} {1}/{2}/{3} {6}'.format(i.id, i.public_dns_name, i.ip_address, i.private_ip_address,i.instance_type, format_state(i.state), format_name(i.tags)))

def format_state(state):
    if state == 'stopped': return '\033[093mSTOPPED\033[0m'
    if state == 'running': return '\033[092mRUNNING\033[0m'
    if state == 'terminated': return '\033[090mTERMINATED\033[0m'
    return state.upper()

def format_name(tags):
    if 'Name' in tags: return '\033[91m<{0}>\033[0m'.format(tags['Name'])
    else: return '\033[94m<NONAME>\033[0m'

def merge_reservations(reservations):
    pass

def create_tags(**kwargs):
    tags = { }
    for key,value in kwargs.iteritems():
        tags[key] = value

    return tags

def main():
    parser = argparse.ArgumentParser(description='H2O EC2 instances launcher')
    parser.add_argument('action', choices=['help', 'demo', 'create', 'terminate', 'stop', 'reboot', 'start', 'distribute_h2o', 'start_h2o', 'show_defaults', 'dump_reservation', 'show_reservations', 'clean_tmp','nexec'], help='EC2 instances action!')
    parser.add_argument('-c', '--config',    help='Configuration file to configure NEW EC2 instances (if not specified default is used - see "show_defaults")', type=str, default=None)
    parser.add_argument('-i', '--instances', help='Number of instances to launch', type=int, default=DEFAULT_NUMBER_OF_INSTANCES)
    parser.add_argument('-H', '--hosts',     help='Hosts file describing existing "EXISTING" EC2 instances ', type=str, default=None)
    parser.add_argument('-r', '--region',    help='Specifies target create region', type=str, default=DEFAULT_REGION)
    parser.add_argument('--reservation',     help='Reservation ID, for example "r-1824ec65"', type=str, default=None)
    parser.add_argument('--name',            help='Name for launched instances', type=str, default=DEFAULT_INSTANCE_NAME)
    parser.add_argument('--timeout',         help='Timeout in seconds.', type=int, default=None)
    parser.add_argument('--instance_type',   help='Enfore a type of EC2 to launch (e.g., m2.2xlarge).', type=str, default=None)
    parser.add_argument('--cmd',             help='Shell command to be executed by nexec.', type=str, default=None)
    parser.add_argument('--image_id',        help='Override defautl image_id', type=str, default=None)
    parser.add_argument('--h2o_per_host',    help='Number of JVM launched per node', type=int, default=1)
    args = parser.parse_args()

    ec2_region = load_ec2_region(args.region)
    if (args.action == 'help'):
        parser.print_help()
    elif (args.action == 'create' or args.action == 'demo'):
        ec2_config = load_ec2_config(args.config, ec2_region, args.instance_type, args.image_id)
        tags       = create_tags(Name=args.name)
        log("EC2 region : {0}".format(ec2_region))
        log("EC2 itype  : {0}".format(ec2_config['instance_type']))
        log("EC2 ami    : {0}".format(ec2_config['image_id']))
        log("EC2 config : {0}".format(ec2_config))
        log("Instances  : {0}".format(args.instances))
        log("Tags       : {0}".format(tags))
        reservation = run_instances(args.instances, ec2_config, ec2_region, tags=tags)
        
        hosts_cfg, filename   = dump_hosts_config(ec2_config, reservation, filename=args.hosts, h2o_per_host=args.h2o_per_host)
        dump_ssh_commands(ec2_config, reservation)
        if (args.action == 'demo'):
            args.hosts = filename
            try:
                invoke_hosts_action('start_h2o', hosts_cfg, args)
            finally:
                invoke_hosts_action('terminate', hosts_cfg, args)

    elif (args.action == 'show_defaults'):
        print 
        print "\033[92mConfig\033[0m : {0}".format(json.dumps(DEFAULT_EC2_INSTANCE_CONFIGS,indent=2))
        print "\033[92mInstances\033[0m         : {0}".format(DEFAULT_NUMBER_OF_INSTANCES)
        print "\033[92mSupported regions\033[0m : {0}".format( [ i for i in DEFAULT_EC2_INSTANCE_CONFIGS ] )
        print
    elif (args.action == 'merge_reservations'):
        merge_reservations(args.reservations, args.region)
    elif (args.action == 'dump_reservation'):
        ec2_config = load_ec2_config(args.config, ec2_region)
        ec2_reservation = load_ec2_reservation(args.reservation, ec2_region)
        dump_hosts_config(ec2_config, ec2_reservation, filename=args.hosts)
    elif (args.action == 'show_reservations'):
        report_reservations(args.region, args.reservation)
    else: 
        if args.hosts: 
            hosts_config = load_hosts_config(args.hosts)
            if hosts_config['ec2_reservation_id']: ec2_reservation = load_ec2_reservation(hosts_config['ec2_reservation_id'], ec2_region)
            else: ec2_reservation = None
        elif args.reservation: # TODO allows for specifying multiple reservations and merge them
            ec2_config      = load_ec2_config(args.config, ec2_region)
            ec2_reservation = load_ec2_reservation(args.reservation, ec2_region)
            hosts_config,_  = dump_hosts_config(ec2_config, ec2_reservation, save=False)
        invoke_hosts_action(args.action, hosts_config, args, ec2_reservation)
        if (args.action == 'terminate' and args.hosts):
            log("Deleting {0} host file.".format(args.hosts))
            os.remove(args.hosts)

if __name__ == '__main__':
    main()

