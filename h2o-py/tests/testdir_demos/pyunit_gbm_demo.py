import sys
sys.path.insert(1, "../../")
import h2o


def demo_gbm():
    h2o.demo(funcname="gbm", interactive=False, test=True)


if __name__ == "__main__":
    from tests import pyunit_utils
    pyunit_utils.standalone_test(demo_gbm)
else:
    demo_gbm()
