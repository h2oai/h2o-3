from tests import pyunit_utils
import sys
sys.path.insert(1, "../../../")
import h2o

def demo_gbm():
    # Connect to a pre-existing cluster
    

    # Execute gbm demo
    h2o.demo(func="gbm", interactive=False, test=True)

if __name__ == "__main__":
	pyunit_utils.standalone_test(demo_gbm)
else:
	demo_gbm()
