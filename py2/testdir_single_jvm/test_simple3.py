import unittest, sys, time
sys.path.extend(['.','..','../..','py'])

import h2o, h2o_cmd, h2o_import as h2i, h2o_browse as h2b

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.init()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_a_simple3(self):
        a = h2o.n0.endpoints()
        print h2o.dump_json(a)
        print "There are %s endpoints" % len(a['routes'])
        for l in a['routes']:
            print l['url_pattern']

    def print_params(self, paramResult):
        model_builders = paramResult['model_builders']
        for algo,v in model_builders.iteritems():
            print "\n", algo, "parameters:"
            parameters = v['parameters']
            for p in parameters:
                name = p['name']
                stype = p['type']
                required = p['required']
                actual_value = p['actual_value']
                values = p['values']
                print name, stype, required, actual_value, values
            print

    
    def test_b_algo_parameters(self):
        for algo in ['kmeans', 'gbm', 'deeplearning', 'glm', 'word2vec', 'example']:
            paramResult = h2o.n0.model_builders(algo=algo)
            self.print_params(paramResult)


if __name__ == '__main__':
    h2o.unit_main()
