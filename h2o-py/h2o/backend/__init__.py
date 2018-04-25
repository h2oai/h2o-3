# -*- encoding: utf-8 -*-
# Copyright: (c) 2016 H2O.ai
# License:   Apache License Version 2.0 (see LICENSE for details)
"""
Classes for communication with backend H2O servers.

:class:`H2OConnection`
    Connect to an existing H2O server and send requests to it.

:class:`H2OLocalServer`
    Start an H2O server on your local machine.

:class:`H2OCluster`
    Handle to the remote H2O cluster -- used mainly to retrieve information about it.

The :mod:`h2o` module has convenience functions for accessing these classes, and those are the ones that are
recommended for everyday use. The following are the common use cases:

(1) Connect to an existing remote H2O server::

        h2o.connect(url="...")

(2) Connect to a local server, or if there isn't one start it and then connect::

        h2o.init()

(3) Start multiple H2O servers locally (forming a cluster), and then connect to one of them::

        from h2o.backend import H2OLocalServer
        for _ in range(5):
            hs = H2OLocalServer.start()
        h2o.connect(server=hs)

Functions :func:`h2o.connect` and :func:`h2o.init` take many parameters that allow you to fine-tune the connection
settings. When used, they will create a new :class:`H2OConnection` object and store it in a global variable -- this
connection will be used by all subsequent calls to ``h2o.`` functions. At this moment there is no effective way to
have multiple connections to separate H2O servers open at the same time. Such facility may be added in the future.
"""
from __future__ import absolute_import, division, print_function, unicode_literals


from .cluster import H2OCluster
from .server import H2OLocalServer
from .connection import H2OConnection
from .connection import H2OConnectionConf

import colorama
from distutils.version import StrictVersion
import sys

if not hasattr(colorama, '__version__'):
    print("[WARNING] H2O requires colorama module of version 0.3.8 or newer. You have older version .\n"
          "You can upgrade to the newest version of the module running from the command line\n"
          "    $ pip%s install --upgrade colorama" % (sys.version_info[0]))
    sys.exit(1)
elif StrictVersion(colorama.__version__) < StrictVersion("0.3.8"):
    print("[WARNING] H2O requires colorama module of version 0.3.8 or newer. You have version %s.\n"
          "You can upgrade to the newest version of the module running from the command line\n"
          "    $ pip%s install --upgrade colorama" % (colorama.__version__, sys.version_info[0]))
    sys.exit(1)
    
__all__ = ("H2OCluster", "H2OConnection", "H2OLocalServer", "H2OConnectionConf")
