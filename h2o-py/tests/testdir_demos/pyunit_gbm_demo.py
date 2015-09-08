import sys
sys.path.insert(1, "../../")
import h2o, tests

def demo_gbm():

    h2o.demo(func="gbm", interactive=False, test=True)

if __name__ == "__main__":
    tests.run_test(sys.argv, demo_gbm)
