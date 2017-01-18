#!/usr/bin/env python
# -*- encoding: utf-8 -*-

from h2o.backend import H2OConnectionConf
from h2o.exceptions import H2OTypeError

def test_h2oconnectionconf():
    """Test H2OConnectionConf functionality."""
    # Verify construction of config from dictionary
    conf1 = { 'ip' : '1.1.1.1', 'port' : 80 }
    cconf1 = H2OConnectionConf.create(conf1)
    assert cconf1.ip == '1.1.1.1'
    assert cconf1.port == 80

    # Verify error handling
    conf2 = {  'ip' : 80, 'port': 80}
    try:
        cconf2 = H2OConnectionConf.create(conf2)
    except H2OTypeError:
        pass




# This test doesn't really need a connection to H2O cluster.
test_h2oconnectionconf()
