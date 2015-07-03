#!/usr/bin/env python

import os
import time
import boto
import boto.ec2


# Environment variables you MUST set (either here or by passing them in).
# -----------------------------------------------------------------------
#
# os.environ['AWS_ACCESS_KEY_ID'] = '...'
# os.environ['AWS_SECRET_ACCESS_KEY'] = '...'


# Options you might want to change.
# ---------------------------------

instanceNameRoot = 'H2ORStudioDemo'


# Options to help debugging.
# --------------------------

debug = 0
# debug = 1


# Options you should not change unless you really mean to.
# --------------------------------------------------------

regionName = 'us-east-1'


#--------------------------------------------------------------------------
# No need to change anything below here.
#--------------------------------------------------------------------------

print 'Using boto version', boto.Version
if (debug):
    boto.set_stream_logger('h2o-ec2')
ec2 = boto.ec2.connect_to_region(regionName, debug=debug)

reservations = ec2.get_all_instances()
for reservation in reservations:
    instances = reservation.instances
    for instance in instances:
        instanceName = '(empty)'
        if 'Name' in instance.tags:
            instanceName = instance.tags['Name'];
        if instanceName.startswith(instanceNameRoot):
            print '   ', instance
            print '       ', instanceName
            print '       ', instance.public_dns_name
            print '       ', instance.ip_address
            print '       ', instance.private_dns_name
            print '       ', instance.private_ip_address
