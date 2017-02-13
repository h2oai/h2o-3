#!/usr/bin/env python
# -*- encoding: utf-8 -*-

from h2o.backend import H2OConnectionConf, H2OConnection
from h2o.exceptions import H2OTypeError, H2OValueError
from h2o.utils.typechecks import assert_matches

def assert_url_pattern(url, schema, ip, port, context_path):
    match = assert_matches(url, H2OConnection.url_pattern)
    assert match.group(1) == schema
    assert match.group(2) == ip
    assert match.group(3) == port
    assert match.group(4) == context_path

def test_h2oconnectionconf():
    """Test H2OConnectionConf functionality."""

    # Verify construction of config from dictionary
    conf1 = { 'ip' : '1.1.1.1', 'port' : 80 }
    cconf1 = H2OConnectionConf(conf1)
    assert cconf1.ip == '1.1.1.1'
    assert cconf1.port == 80
    assert cconf1.url == 'http://1.1.1.1:80/'

    # Verify error handling
    conf2 = {  'ip' : 80, 'port': 80}
    try:
        cconf2 = H2OConnectionConf(conf2)
    except H2OTypeError:
        pass

    conf3 = { 'dummy': 'variable', 'ip': 'localhost'}
    try:
        cconf3 = H2OConnectionConf(conf3)
    except H2OValueError:
        pass

    conf4 = { 'ip': 'localhost', 'port': 54321, 'context_path': 'cluster_4', 'https': True}
    cconf4 = H2OConnectionConf(conf4)
    assert cconf4.url == 'https://localhost:54321/cluster_4'

    # Verify URL pattern
    assert_url_pattern("http://localhost:54321", "http", "localhost", "54321", None)
    assert_url_pattern("http://localhost:54322/", "http", "localhost", "54322", None)
    assert_url_pattern("http://localhost:54323/cluster_X", "http", "localhost", "54323", "/cluster_X")
    assert_url_pattern("http://localhost:54324/a/b/c/d", "http", "localhost", "54324", "/a/b/c/d")
    

# This test doesn't really need a connection to H2O cluster.
test_h2oconnectionconf()
