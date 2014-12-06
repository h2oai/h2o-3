import h2o_args, h2o_nodes

# tests refer to h2o.unit_main
from h2o_args import unit_main
# start with just the stuff tests use, from here? 
# so they don't get hardwired to another module that might change?
from h2o_test import setup_random_seed, make_syn_dir, verboseprint, check_sandbox_for_errors, dump_json
import h2o_bc # init, tear_down_cloud

nodes = []
n0 = None
def cloud_name():
    if not nodes:
        raise Exception("You probably didn't get a cloud built somehow, yet %", nodes)
    return nodes[0].cloud_name

def init(*args, **kwargs):
    # will get rid of this
    if False:
        unit_main()
    # should be okay to have late args parsing, as long as none are used before h2o.init() work?
    # one problem:
    # The code that forces a random seed runs before this though...so use of debuggers means they won't be
    # able to force a seed? This will at least gets the other args right for use in debugger (like --usecloud)
    if not h2o_args.g_unit_main_already_called:
        h2o_args.parse_our_args()

    global nodes, n0
    # ugly, but we have 3 places that are kept in sync..check them all 
    def checkIsNone(thingName, thing):
        if not (thing is None or len(thing)==0):
            print "WARNING: %s is not empty before h2o.init()..Unlikely that makes sense? %s" % (thingName, thing)

    checkIsNone("nodes", nodes)
    checkIsNone("n0", n0)
    checkIsNone("h2o_nodes.nodes", h2o_nodes.nodes)
    nodes = h2o_bc.init(*args, **kwargs)
    n0 = nodes[0] # use to save typing?..i.e. h2o.n0

def tear_down_cloud(*args, **kwargs):
    h2o_bc.tear_down_cloud(*args, **kwargs)
    global nodes, n0
    nodes = []
    n0 = None

