import requests

import h2o
import h2o_test_utils

def test(a_node, pp):
    ####################################
    # test HTML pages GET
    url_prefix = 'http://' + a_node.http_addr + ':' + str(a_node.port)
    
    urls = {
        '': 'Analytics',
        '/': 'Analytics',
        '/index.html': 'Analytics',
        '/flow/index.html': 'modal',
        '/LATEST/Cloud.html': 'Ready',
       }
    
    for (suffix, expected_word) in urls.iteritems():
        url = url_prefix + suffix
        h2o.H2O.verboseprint('Testing ' + url + '. . .')
        r = requests.get(url)
        assert r.text.find(expected_word), "FAIL: didn't find '" + expected_word + "' in: " + url
