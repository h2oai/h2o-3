#!/usr/bin/env python

import os
import sys
import time
import boto
import boto.ec2


# Environment variables you MUST set (either here or by passing them in).
# -----------------------------------------------------------------------
#
os.environ['AWS_ACCESS_KEY_ID'] = ''
os.environ['AWS_SECRET_ACCESS_KEY'] = ''
os.environ['AWS_SSH_PRIVATE_KEY_FILE'] = ''

# Launch EC2 instances with an IAM role
# --------------------------------------
# 
iam_profile_resource_name = None
# or
iam_profile_name = None

# Options you MUST tailor to your own AWS account.
# ------------------------------------------------

# SSH key pair name.
keyName = '' 

# AWS security group name.
# Note:
#     H2O uses TCP and UDP ports 54321 and 54322.
#     RStudio uses TCP port 8787.
securityGroupName = 'SecurityDisabled'


# Options you might want to change.
# ---------------------------------

numInstancesToLaunch = 2
instanceType = 'm3.2xlarge'
instanceNameRoot = 'h2o-instance'
# set to a string USD amount if you'd like to request spot instances
# read more about spot here: https://aws.amazon.com/ec2/spot/
spotBid = None


# Options to help debugging.
# --------------------------

debug = 0
# debug = 1
dryRun = False
# dryRun = True


# Options you should not change unless you really mean to.
# --------------------------------------------------------

regionName = 'us-east-1'
amiId = 'ami-0b100e61'

#regionName = 'us-west-1'
#amiID = 'ami-c1afd6a1'


#--------------------------------------------------------------------------
# No need to change anything below here.
#--------------------------------------------------------------------------

# Note: this python script was initially developed with boto 2.13.3.
def botoVersionMismatch():
    print 'WARNING:  Unsupported boto version.  Please upgrade boto to at least 2.13.x and try again.'
    print 'Comment this out to run anyway.'
    print 'Exiting.'
    sys.exit(1)

if not 'AWS_ACCESS_KEY_ID' in os.environ:
    print 'ERROR: You must set AWS_ACCESS_KEY_ID in the environment.'
    sys.exit(1)

if not 'AWS_SECRET_ACCESS_KEY' in os.environ:
    print 'ERROR: You must set AWS_SECRET_ACCESS_KEY in the environment.'
    sys.exit(1)

if not 'AWS_SSH_PRIVATE_KEY_FILE' in os.environ:
    print 'ERROR: You must set AWS_SSH_PRIVATE_KEY_FILE in the environment.'
    sys.exit(1)

publicFileName = 'nodes-public'
privateFileName = 'nodes-private'

if not dryRun:
    fpublic = open(publicFileName, 'w')
    fprivate = open(privateFileName, 'w')

print 'Using boto version', boto.Version
if True:
    botoVersionArr = boto.Version.split(".")
    if (botoVersionArr[0] != 2):
        botoVersionMismatch
    if (botoVersionArr[1] < 13):
        botoVersionMismatch

if (debug):
    boto.set_stream_logger('h2o-ec2')
ec2 = boto.ec2.connect_to_region(regionName, debug=debug)

print 'Launching', numInstancesToLaunch, 'instances.'

if spotBid is None:
    reservation = ec2.run_instances(
        image_id=amiID,
        min_count=numInstancesToLaunch,
        max_count=numInstancesToLaunch,
        key_name=keyName,
        instance_type=instanceType,
        security_groups=[securityGroupName],
        instance_profile_arn=iam_profile_resource_name,
        instance_profile_name=iam_profile_name,
        dry_run=dryRun
    )
else:
    spotRequests = ec2.request_spot_instances(
        price=spotBid,
        image_id=amiID,
        count=numInstancesToLaunch,
        key_name=keyName,
        instance_type=instanceType,
        security_groups=[securityGroupName],
        instance_profile_arn=iam_profile_resource_name,
        instance_profile_name=iam_profile_name,
        dry_run=dryRun,
    )

    requestIDs = [request.id for request in spotRequests]
    fulfilled = []

    while requestIDs:
        requests = ec2.get_all_spot_instance_requests(
            request_ids=requestIDs,
        )

        for request in requests:
            if request.instance_id:
                requestIDs.remove(request.id)
                fulfilled.append(request.instance_id)

            # unrecoverable error without increasing the spot bid
            elif request.status.code == u'price-too-low':
                print request.status.message
                print 'Cancelling Spot requests...'
                # get original request ids since some requests may have already been fulfilled
                ec2.cancel_spot_instance_requests(
                    request_ids=[request.id for request in spotRequests],
                )

                print 'Exiting...'
                sys.exit(1)

            print request.id, request.status.message

        print '%s/%s requests fulfilled' % (len(fulfilled), numInstancesToLaunch)

        if requestIDs:
            print 'Waiting for remaining requests to be fulfilled...'
            time.sleep(5)

    reservation = ec2.get_all_instances(
        instance_ids=fulfilled,
    )

instances = reservation[0].instances

for i, instance in enumerate(instances):
    print 'Waiting for instance', i+1, 'of', numInstancesToLaunch, '...'
    instance.update()
    while instance.state != 'running':
        print '    .'
        time.sleep(1)
        instance.update()
    print '    instance', i+1, 'of', numInstancesToLaunch, 'is up.'
    name = instanceNameRoot + str(i)
    instance.add_tag('Name', value=name)

print
print 'Creating output files: ', publicFileName, privateFileName
print

for i, instance in enumerate(instances):
    instanceName = instance.tags.get('Name', '')

    print 'Instance', i+1, 'of', numInstancesToLaunch
    print '    Name:   ', instanceName
    print '    PUBLIC: ', instance.public_dns_name
    print '    PRIVATE:', instance.private_ip_address
    print
    fpublic.write(instance.public_dns_name + '\n')
    fprivate.write(instance.private_ip_address + '\n')

fpublic.close()
fprivate.close()

print 'Sleeping for 60 seconds for ssh to be available...'
time.sleep(60)

d = os.path.dirname(os.path.realpath(__file__))

print 'Testing ssh access...'
cmd = d + '/' + 'h2o-cluster-test-ssh.sh'
rv = os.system(cmd)
if rv != 0:
    print 'Failed.'
    sys.exit(1)

print
print 'Distributing flatfile...'
cmd = d + '/' + 'h2o-cluster-distribute-flatfile.sh'
rv = os.system(cmd)
if rv != 0:
    print 'Failed.'
    sys.exit(1)
# Distribute flatfile script already prints success when it completes.
