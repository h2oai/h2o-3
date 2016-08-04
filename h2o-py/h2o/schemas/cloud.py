# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals
from h2o.utils.compatibility import *  # NOQA

import sys
from ..utils.shared_utils import get_human_readable_bytes, get_human_readable_time
from ..display import H2ODisplay


class H2OCluster(object):

    def __init__(self, keyvals):
        self._props = {}
        self._connection = None
        for k, v in keyvals:
            if k == "__meta" or k == "_exclude_fields" or k == "__schema": continue
            if k in _cloud_v3_valid_keys:
                self._props[k] = v
            else:
                raise AttributeError("Attribute %s cannot be set on H2OCluster (= %r)" % (k, v))

    @property
    def skip_ticks(self):
        return self._props["skip_ticks"]

    @property
    def bad_nodes(self):
        return self._props["bad_nodes"]

    @property
    def branch_name(self):
        return self._props["branch_name"]

    @property
    def build_number(self):
        return self._props["build_number"]

    @property
    def build_age(self):
        return self._props["build_age"]

    @property
    def build_too_old(self):
        return self._props["build_too_old"]

    @property
    def cloud_healthy(self):
        return self._props["cloud_healthy"]

    @property
    def cloud_name(self):
        return self._props["cloud_name"]

    @property
    def cloud_size(self):
        return self._props["cloud_size"]

    @property
    def cloud_uptime_millis(self):
        return self._props["cloud_uptime_millis"]

    @property
    def consensus(self):
        return self._props["consensus"]

    @property
    def is_client(self):
        return self._props["is_client"]

    @property
    def locked(self):
        return self._props["locked"]

    @property
    def node_idx(self):
        return self._props["node_idx"]

    @property
    def nodes(self):
        return self._props["nodes"]

    @property
    def skip_ticks(self):
        return self._props["skip_ticks"]

    @property
    def version(self):
        return self._props["version"]


    @property
    def connection(self):
        if self._connection is None:
            from h2o.backend.connection import H2OConnection
            self._connection = H2OConnection()
        return self._connection

    @connection.setter
    def connection(self, value):
        self._connection = value

    def pprint(self):
        ncpus = sum(node["num_cpus"] for node in self.nodes)
        allowed_cpus = sum(node["cpus_allowed"] for node in self.nodes)
        free_mem = sum(node["free_mem"] for node in self.nodes)
        cluster_health = all(node["healthy"] for node in self.nodes)
        H2ODisplay([
            ["H2O cluster uptime:",        get_human_readable_time(self.cloud_uptime_millis)],
            ["H2O cluster version:",       self.version],
            ["H2O cluster version age:",   "{} {}".format(self.build_age, ("!!!" if self.build_too_old else ""))],
            ["H2O cluster name:",          self.cloud_name],
            ["H2O cluster total nodes:",   self.cloud_size],
            ["H2O cluster free memory:",   get_human_readable_bytes(free_mem)],
            ["H2O cluster total cores:",   str(ncpus)],
            ["H2O cluster allowed cores:", str(allowed_cpus)],
            ["H2O cluster is healthy:",    str(cluster_health)],
            ["H2O cluster is locked:",     self.locked],
            ["H2O connection url:",        self.connection.base_url],
            ["H2O connection proxy:",      self.connection.proxy],
            ["Python version:",            "%d.%d.%d %s" % tuple(sys.version_info[:4])],
        ])



_cloud_v3_valid_keys = {"is_client", "build_number", "cloud_name", "locked", "node_idx", "consensus", "branch_name",
                        "version", "cloud_uptime_millis", "cloud_healthy", "bad_nodes", "cloud_size", "skip_ticks",
                        "nodes", "build_age", "build_too_old"}
