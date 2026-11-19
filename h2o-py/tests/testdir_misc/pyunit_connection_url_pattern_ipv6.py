import sys
sys.path.insert(1, "../../")

import re

from h2o.backend.connection import H2OConnection
from tests import pyunit_utils


def test_connection_url_pattern_accepts_ipv4_hostnames_and_bracketed_ipv6():
    pattern = H2OConnection.url_pattern

    def parse(url):
        match = re.match(pattern, url)
        assert match is not None, "URL should match: %s" % url
        return match.group(1), match.group(2), int(match.group(3)), match.group(4)

    # existing behaviour is unchanged
    assert parse("http://localhost:54321") == ("http", "localhost", 54321, None)
    assert parse("http://10.0.0.1:54321") == ("http", "10.0.0.1", 54321, None)
    assert parse("https://node-1.example.com:54321/context") == ("https", "node-1.example.com", 54321, "/context")

    # IPv6 literals in brackets; the brackets are kept so the base URL stays valid
    assert parse("http://[2001:db8::1]:54321") == ("http", "[2001:db8::1]", 54321, None)
    assert parse("https://[2001:db8:0:1::42]:54321/context") == ("https", "[2001:db8:0:1::42]", 54321, "/context")
    assert parse("http://[::1]:54321") == ("http", "[::1]", 54321, None)
    assert parse("http://[::ffff:10.0.0.1]:54321") == ("http", "[::ffff:10.0.0.1]", 54321, None)

    # still rejected
    for url in ["http://2001:db8::1:54321", "http://[2001:db8::1]", "http://[not-an-ip]:54321", "ftp://10.0.0.1:54321"]:
        assert re.match(pattern, url) is None, "URL should not match: %s" % url


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_connection_url_pattern_accepts_ipv4_hostnames_and_bracketed_ipv6)
else:
    test_connection_url_pattern_accepts_ipv4_hostnames_and_bracketed_ipv6()
