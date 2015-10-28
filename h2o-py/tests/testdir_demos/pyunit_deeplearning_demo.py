import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def demo_deeplearning():

    h2o.demo(func="deeplearning", interactive=False, test=True)



if __name__ == "__main__":
    pyunit_utils.standalone_test(demo_deeplearning)
else:
    demo_deeplearning()
