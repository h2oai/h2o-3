#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
import logging
import os
from tempfile import TemporaryDirectory

import h2o
import h2o.utils.config
from tests import pyunit_utils as pu


def test_h2oconfig():
    """
    Test for parser of the .h2oconfig files.

    This test will create various config files in the tests/results/configtest
    folder and then parse them with the `H2OConfigReader` class.
    """
    l = logging.getLogger("h2o")
    l.setLevel(20)

    test_single_config("", {})
    test_single_config("# key = value\n\n", {})
    test_single_config("# key = value\n[init]\n", {})

    test_single_config("""
        [init]
        check_version = False
        proxy = http://127.12.34.99.10000
    """, {"init.check_version": "False", "init.proxy": "http://127.12.34.99.10000"})

    test_single_config("""
        init.check_version = anything!  # rly?
        init.cookies=A
        # more comment
    """, {"init.cookies": "A", "init.check_version": "anything!  # rly?"})

    test_single_config("hbwltqert", {}, n_errors=1)

    test_single_config("""
        init.checkversion = True
        init.clusterid = 7
        proxy = None
    """, {}, n_errors=3)

    test_single_config("""
        [something]
        init.check_version = True
    """, {}, 1)

    test_single_config("""
        init.check_version = True
        init.check_version = False
        init.check_version = Ambivolent
    """, {"init.check_version": "Ambivolent"})


def test_single_config(text, expected, n_errors=0):
    print()
    with TemporaryDirectory() as target_dir:
        with open(os.path.join(target_dir, ".h2oconfig"), "wt") as f:
            f.write(text)

        if n_errors:
            print("Expecting %d error%s..." % (n_errors, "s" if n_errors > 1 else ""))
        handler = LogErrorCounter()
        logging.getLogger("h2o").addHandler(handler)
        result = h2o.utils.config.H2OConfigReader(target_dir).read_config()
        assert result == expected, "Expected config %r but obtained %r" % (expected, result)
        assert handler.errorcount == n_errors, "Expected %d errors but obtained %d" % (n_errors, handler.errorcount)
        logging.getLogger("h2o").removeHandler(handler)


class LogErrorCounter(logging.Handler):

    def __init__(self):
        super(self.__class__, self).__init__()
        self.errorcount = 0

    def emit(self, record):
        if record.levelno >= 40:
            self.errorcount += 1


pu.run_tests([
    test_h2oconfig
])
