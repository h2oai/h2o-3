import h2o_args

# start with just the stuff tests use, from here? so they don't get hardwired to another module that might change?
from h2o_args import unit_main
from h2o_test import setup_random_seed, make_syn_dir, verboseprint, check_sandbox_for_errors, dump_json
from h2o_bc import init as init2, tear_down_cloud as tear_down_cloud2

nodes = []

def cloud_name():
    if not nodes:
        raise Exception("You probably didn't get a cloud built somehow, yet %", nodes)
    return nodes[0].cloud_name

def init(*args, **kwargs):
    global nodes
    nodes = init2(*args, **kwargs)

def tear_down_cloud(*args, **kwargs):
    tear_down_cloud2(*args, **kwargs)
    global nodes
    nodes = []

