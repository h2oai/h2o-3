#!/usr/bin/python
import unittest, time, sys, datetime
sys.path.extend(['.','..','py','../h2o/py','../../h2o/py'])
import h2o2 as h2o
import h2o_hosts, h2o_args
import h2o_print as h2p

beginning = time.time()
def log(msg):
    print "\033[92m[0xdata] \033[0m", msg

CHECK_WHILE_SLEEPING = False

print "Don't start a test yet..."
class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED, localhost
        SEED = h2o.setup_random_seed()
        # localhost = h2o.decide_if_localhost()
        # always require a config file for now..don't have h2o.decide_if_localhost() here
        localhost = False
        if (localhost):
            # h2o.nodes[0].delete_keys_at_teardown should cause the testdir_release
            # tests to delete keys after each test completion (not cloud teardown, don't care then)
            # h2o.init(3, create_json=True, java_heap_GB=4, delete_keys_at_teardown=True)
            # RemoveAll.json doesn't work?
            h2o.init(3, create_json=True, java_heap_GB=4)
        else:
            # RemoveAll.json doesn't work?
            # h2o.init(create_json=True, delete_keys_at_teardown=True)
            h2o.init(create_json=True)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_build_for_clone(self):
        # python gets confused about which 'start' if I used start here
        elapsed = time.time() - beginning
        print "\n%0.2f seconds to get here from start" % elapsed

        # might as well open a browser on it? (because the ip/port will vary
        # maybe just print the ip/port for now
        ## h2b.browseTheCloud()

        maxTime = 4*3600
        totalTime = 0
        incrTime = 60
        h2p.purple_print("\nSleeping for total of", (maxTime+0.0)/3600, "hours.")
        print "Will check h2o logs every", incrTime, "seconds"
        print "Should be able to run another test using h2o-nodes.json to clone cloud"
        print "i.e. h2o.build_cloud_with_json()"
        print "Bad test if a running test shuts down the cloud. I'm supposed to!\n"

        h2p.green_print("To watch cloud in browser follow address:")
        h2p.green_print("   http://{0}:{1}/Cloud.html".format(h2o.nodes[0].http_addr, h2o.nodes[0].port))
        h2p.blue_print("You can start a test (or tests) now!") 

        h2p.blue_print("Will Check cloud status every %s secs and kill cloud if wrong or no answer" % incrTime)
        if CHECK_WHILE_SLEEPING:        
            h2p.blue_print("Will also look at redirected stdout/stderr logs in sandbox every %s secs" % incrTime)

        h2p.red_print("No checking of logs while sleeping, or check of cloud status")
        h2p.yellow_print("So if H2O stack traces, it's up to you to kill me if 4 hours is too long")
        h2p.yellow_print("ctrl-c will cause all jvms to die(thru psutil terminate, paramiko channel death or h2o shutdown...")


        while (totalTime<maxTime): # die after 4 hours
            time.sleep(incrTime)
            totalTime += incrTime
            # good to touch all the nodes to see if they're still responsive
            # give them up to 120 secs to respond (each individually)

            ### h2o.verify_cloud_size(timeoutSecs=120)
            if CHECK_WHILE_SLEEPING:        
                print "Checking sandbox log files"
                h2o.check_sandbox_for_errors(cloudShutdownIsError=True)
            else:
                print str(datetime.datetime.now()), h2o_args.python_cmd_line, "still here", totalTime, maxTime, incrTime

        # don't do this, as the cloud may be hung?
        if 1==0:
            print "Shutting down cloud, but first delete all keys"
            start = time.time()
            h2i.delete_keys_at_all_nodes()
            elapsed = time.time() - start
            print "delete_keys_at_all_nodes(): took", elapsed, "secs"

if __name__ == '__main__':
    h2o.unit_main()
