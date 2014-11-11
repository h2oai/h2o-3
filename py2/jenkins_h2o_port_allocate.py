#!/usr/bin/python

# "Avoid locker or centralized resource by hard-wiring the port mapping within range"
# "implied by max # of ports used per job, max # of executors per machine, and # of machines." 
# "Map of source determines port. in/out using env variables"
print "\njenkins_h2o_port_allocate...."

import socket, os, subprocess
USED_HOSTNAMES = [
    'mr-0xb1',
    'mr-0xb4',

    'mr-0x2',
    'mr-0x3',
    'mr-0x4',
    'mr-0x5',
    'mr-0x6',
    'mr-0x7',
    'mr-0x8',
    'mr-0x9',
    'mr-0x10',

    'mr-0xd4',
    'mr-0xd5',
    'mr-0xd6',
    'mr-0xd7',
    'mr-0xd8',
    'mr-0xd9',
    'mr-0xd10',
    'Kevin-Ubuntu3',
]

# maximum number of ports a job uses 10 = 5 jvms * 2 ports per h2o jvm (current max known)
PORTS_PER_SLOT = 10
DEFAULT_BASE_PORT = 54340
EXECUTOR_NUM = 8

def jenkins_h2o_port_allocate():
    """
    input: jenkins environment variable EXECUTOR_NUMBER
    output: creates ./BASE_PORT.sh, that you should 'source ./PORT.sh'
        (can't see the env. variables directly from python?)

    which will create os environment variables H2O_PORT and H2O_PORT_OFFSET (legacy)

        internal state for this script that can be updated: 
            USED_HOSTNAMES (list of machine names), 
            PORTS_PER_SLOT (max per any job), 
            DEFAULT_BASE_PORT
        If you modify any of the internal state, you may introduce contention between 
        new jenkins jobs and running jenkins jobs. (might not!)
        You should stop/start all jobs (or ignore failures) if you modify internal state here. 
        Hence, no parameters to avoid living dangerously!
    """
    if os.environ.has_key("EXECUTOR_NUMBER"):
        # this will fail if it's not an integer
        executor = int(os.environ["EXECUTOR_NUMBER"])
    else:
        executor = 1 # jenkins starts with 1

    print "jenkins EXECUTOR_NUMBER:", executor
    if executor<0 or executor>=EXECUTOR_NUM:
        raise Exception("executor: %s wrong? Expecting 1-8 jenkins executors on a machine (0-7 exp.)" % executor)

    h2oPort = DEFAULT_BASE_PORT
    h2oPortOffset = 0
    hostname = socket.gethostname()
    if hostname not in USED_HOSTNAMES:
        print "WARNING: this hostname: %s isn't in my list. You should add it?" % hostname
        print "Will use default base port"
    else:
        hostnameIndex = USED_HOSTNAMES.index(hostname)
        h2oPortOffset = PORTS_PER_SLOT * (executor + hostnameIndex)
        h2oPort += h2oPortOffset

    print "Possible h2o base_port range is %s to %s" % \
        (DEFAULT_BASE_PORT, DEFAULT_BASE_PORT + (PORTS_PER_SLOT * EXECUTOR_NUM * len(USED_HOSTNAMES)) - 2)
    print "Possible h2o ports used ranged is %s to %s" % \
        (DEFAULT_BASE_PORT, DEFAULT_BASE_PORT + (PORTS_PER_SLOT * EXECUTOR_NUM * len(USED_HOSTNAMES)) - 1)
    print "want to 'export H2O_PORT=%s'" % h2oPort
    print "want to 'export H2O_PORT_OFFSET=%s # legacy'" % h2oPortOffset

    f = open('H2O_BASE_PORT.sh','w')
    f.write('export H2O_PORT=%s\n' % h2oPort)

    f.write('export H2O_PORT_OFFSET=%s # legacy\n' % h2oPortOffset)
    f.close()
    print "\nNow please:\nsource ./H2O_BASE_PORT.sh"


if __name__ == "__main__":
    jenkins_h2o_port_allocate()

"""
This auto-magics the manual allocation I did when parallelized the current 8-way jenkins jobs, 
2 per machine, on the jenkins mr-0xd4 that dispatches to mr-0xd5 thru mr-0xd9

The rationale for a global allocate requires understanding what machines a jenkins master/slave can be on, 
and what machines they send h2o jars to.

at 0xdata:

A jenkins master is a member of a group of machines. Jenkins can send the python or other test to another slave machine, and then the test can dispatch h2o either locally, or to other machines in the group. 

it can target h2o.jar's anywhere in that group, or dispatch a job to a slave in that group that might do the same.

We currently have two such groups, with one jenkins master in each group (mr-0xb4 and mr-0xd4)
(update: let's just say it's all one big group. Not worth optimizing for subgroup knowlege)
So using 
    (hostname offset in the list of total hostnames)  * (EXECUTOR_NUMBER-1 * PORTS_PER_SLOT)

Will give a unique offset from the default 54340 base, for the job, regardless of which jenkins (master or slave) starts it in the group and where the h2o targest are (which is controlled by the config.json used in the job)

all cloud builds done in a job (one or more) use the same offset.

Dispatching tests from your laptop..will they collide with jenkins?
If the host machine is not in the list, like a laptop, then the offset is 0. (54340 will be used). I suppose jenkins could shift it's base_port to be at least 10 above 54340, so existing scripts that users have, that use 54340, won't be stepped on by jenkins. 54340 could be the jenkins base port.

EC2:
I suppose if the tests are used in ec2, we only do one h2o jar per machine, (or multijvm) so no conflict if 54340 is used. (or 54340). We typically want fast EC2 results, so don't overload target machines?. I suppose an EC2 machine list could be created in this script if we started overloading EC2 machines also

PORTS_PER_SLOT is 10 right now, since the most a job will do is 5 h2o jvms.

I guess to ease the transition, I could leave the H2O_PORT_OFFSET as the api to build_cloud(), and have another python script look at the current ho2 IP and EXECUTOR_NUMBER env variable from jenkins

Notes:
Right now, assuming the subnet octet range from a group is 160-180 or 181-190 works. 164 is an oddball case (out of the ten range for it's group)

I guess I could just put a list of IPs for the jenkins groups that exist, and find the group your in, and then get a "group index" from that list. That's robust and easily maintainable.

This algorithm keeps the total port range in use = (max # of executors per jenkins master or slave) * PORTS_PER_SLOT * (# of machines in a group)
Using 2 executors per machine is nice. 4 is about the max that works well with h2o. so 4 * 10 * 10 = 400 ports
that would be 54340 thru 54721

NICE POSSIBILITES: If we know that ubuntu or other services need to reserve ports that are in our range, we can put in mappings to other ports for those values, or shift the port range or whatever...i.e. we can adjust the algorithm in one place. If the 54340 base is not good, that's set in h2o.py..currently tests don't modify base_port (except for some cloud tests we don't run in jenkins, that do more than 5 jvms on a single machine)

I suppose the tool could output the exact port to use, rather than an offset to h2o.py's default.  Maybe initially will output both, so h2o.py can migrate
i.e. environment variables H2O_PORT_OFFSET and H2O_PORT (= 5321 + H2O_PORT_OFFSET)

UPDATE: To allow for dispatching h2o to any machine in any jenkins group, we can have just one group list that has all possible machines. Makes the used port range twice as big (800) but that's okay. It's like going to a 255.255.0.0 network!

Detail:
Jenkins has global environment variables

This one is useful
EXECUTOR_NUMBER    The unique number that identifies the current executor (among executors of the same machine) that's carrying out this build. This is the number you see in the "build executor status", except that the number starts from 0, not 1.

Now each slave machine can have multiple executors, in addition to the master.

So since in a grand scheme, we don't know who's creating h2o.jars on target machines, from which machine, (jenkins master or slave)...
it means we want a global h2o port allocation (assuming that scraping an h2o port from OS allocation is ugly)

I have cases on 164 jenkins that send the python job to jenkins slave 174, which dispatches h2o jars to 175-180, Or dispatch to YARN on hadoop clusters, but we don't care about ports there, we get told the ip/port by the h2odriver.

Since the pool of machines in a group is fixed, we have the EXECUTOR_NUMBER which is the parallelism per machine (jenkins master or slave), and we

Will give a unique offset to 54340
We can call it a "PORT_SLOT" and pass it as a environment variable like the current "export H2O_PORT_OFFSET=40"
that the build_cloud() uses to offset the default base_port. I suppose PORTS_PER_SLOT can be fixed in build_cloud() so it's the same for all jobs (so jobs don't step over each other.
"""



