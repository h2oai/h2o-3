# -*- encoding: utf-8 -*-
"""Information about the backend H2O cluster."""
from __future__ import division, print_function, absolute_import, unicode_literals

import sys
import time

import h2o
from h2o.exceptions import H2OConnectionError, H2OServerError
from h2o.display import H2ODisplay
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type
from h2o.utils.shared_utils import get_human_readable_bytes, get_human_readable_time



class H2OCluster(object):
    """
    Information about the backend H2O cluster.

    This object is available from ``h2o.cluster()`` or ``h2o.connection().cluster``, and its purpose is to provide
    basic information / manipulation methods for the underlying cluster.
    """

    # If information is this many seconds old, it will be refreshed next time you call :meth:`status`.
    REFRESH_INTERVAL = 1.0

    def __init__(self):
        """Initialize new H2OCluster instance."""
        self._props = {}
        self._retrieved_at = None

    @staticmethod
    def from_kvs(keyvals):
        """
        Create H2OCluster object from a list of key-value pairs.

        TODO: This method should be moved into the base H2OResponse class.
        """
        obj = H2OCluster()
        obj._retrieved_at = time.time()
        for k, v in keyvals:
            if k in {"__meta", "_exclude_fields", "__schema"}: continue
            if k in _cloud_v3_valid_keys:
                obj._props[k] = v
            else:
                raise AttributeError("Attribute %s cannot be set on H2OCluster (= %r)" % (k, v))
        return obj



    @property
    def skip_ticks(self):
        return self._props.get("skip_ticks", None)

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
        # If the build age is unknown, then the cluster is so old it comes from "prehistoric" times when no
        # build number reporting was done...
        return self._props.get("build_age", "PREHISTORIC")

    @property
    def build_too_old(self):
        # If the prop "build_too_old" wasn't reported by the server, then it's definitely too old :)
        return self._props.get("build_too_old", True)

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
    def cloud_internal_timezone(self):
        return self._props["cloud_internal_timezone"]

    @property
    def datafile_parser_timezone(self):
        return self._props["datafile_parser_timezone"]

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
    def version(self):
        return self._props["version"]

    @property
    def internal_security_enabled(self):
        return self._props["internal_security_enabled"]


    def node(self,node_idx):
        """
        Get information about a particular node in an H2O cluster (node index is 0 based)

        Information includes the following:

        nthreads: Number of threads
        pid: PID of current H2O process
        mem_value_size: Data on Node memory
        max_disk: Max disk
        free_disk: Free disk
        open_fds: Open File Descripters
        swap_mem: Size of data on node's disk
        tcps_active: Open TCP connections
        num_cpus: Number of cpus
        cpus_allowed: CPU's allowed
        gflops: Linpack GFlops
        fjthrds: F/J Thread count, by priority
        mem_bw: Memory bandwith
        fjqueue: F/J Task count, by priority
        my_cpu_pct: System CPU percentage used by this H2O process in last interval
        pojo_mem: Temp (non Data) memory
        num_keys: Number of local keys
        ip_port: IP address and port in the form a.b.c.d:e
        last_ping: Time (in msec) of last ping
        rpcs_active: Active Remote Procedure Calls
        max_mem: Maximum memory size for node
        healthy: (now-last_ping)<HeartbeatThread.TIMEOUT
        sys_load: System load; average #runnables/#cores
        sys_cpu_pct: System CPU percentage used by everything in last interval
        free_mem: Free heap
        h2o: IP

        :param node_idx: An int value indicating which node to extract information from
        :returns: Dictionary containing node info

        :examples:
          >>>import h2o
          >>>h2o.init()
          >>>node_one = h2o.cluster().node(0)
          >>>node_one["pid"] #Get PID for first node in H2O Cluster
        """
        return self.nodes[node_idx]

    def shutdown(self, prompt=False):
        """
        Shut down the server.

        This method checks if the H2O cluster is still running, and if it does shuts it down (via a REST API call).

        :param prompt: A logical value indicating whether to prompt the user before shutting down the H2O server.
        """
        if not self.is_running(): return
        assert_is_type(prompt, bool)
        if prompt:
            question = "Are you sure you want to shutdown the H2O instance running at %s (Y/N)? " \
                       % h2o.connection().base_url
            response = input(question)  # works in Py2 & Py3 because redefined in h2o.utils.compatibility module
        else:
            response = "Y"
        if response.lower() in {"y", "yes"}:
            h2o.api("POST /3/Shutdown")
            h2o.connection().close()


    def is_running(self):
        """
        Determine if the H2O cluster is running or not.

        :returns: True if the cluster is up; False otherwise
        """
        try:
            if h2o.connection().local_server and not h2o.connection().local_server.is_running(): return False
            h2o.api("GET /")
            return True
        except (H2OConnectionError, H2OServerError):
            return False


    def show_status(self, detailed=False):
        """
        Print current cluster status information.

        :param detailed: if True, then also print detailed information about each node.
        """
        if self._retrieved_at + self.REFRESH_INTERVAL < time.time():
            # Info is stale, need to refresh
            new_info = h2o.api("GET /3/Cloud")
            self._fill_from_h2ocluster(new_info)
        ncpus = sum(node["num_cpus"] for node in self.nodes)
        allowed_cpus = sum(node["cpus_allowed"] for node in self.nodes)
        free_mem = sum(node["free_mem"] for node in self.nodes)
        unhealthy_nodes = sum(not node["healthy"] for node in self.nodes)
        status = "locked" if self.locked else "accepting new members"
        if unhealthy_nodes == 0:
            status += ", healthy"
        else:
            status += ", %d nodes are not healthy" % unhealthy_nodes
        api_extensions = self.list_api_extensions()
        H2ODisplay([
            ["H2O cluster uptime:",        get_human_readable_time(self.cloud_uptime_millis)],
            ["H2O cluster timezone:",      self.cloud_internal_timezone],
            ["H2O data parsing timezone:", self.datafile_parser_timezone],
            ["H2O cluster version:",       self.version],
            ["H2O cluster version age:",   "{} {}".format(self.build_age, ("!!!" if self.build_too_old else ""))],
            ["H2O cluster name:",          self.cloud_name],
            ["H2O cluster total nodes:",   self.cloud_size],
            ["H2O cluster free memory:",   get_human_readable_bytes(free_mem)],
            ["H2O cluster total cores:",   str(ncpus)],
            ["H2O cluster allowed cores:", str(allowed_cpus)],
            ["H2O cluster status:",        status],
            ["H2O connection url:",        h2o.connection().base_url],
            ["H2O connection proxy:",      h2o.connection().proxy],
            ["H2O internal security:",     self.internal_security_enabled],
            ["H2O API Extensions:",        ', '.join(api_extensions)],
            ["Python version:",            "%d.%d.%d %s" % tuple(sys.version_info[:4])],
        ])

        if detailed:
            keys = ["h2o", "healthy", "last_ping", "num_cpus", "sys_load", "mem_value_size", "free_mem", "pojo_mem",
                    "swap_mem", "free_disk", "max_disk", "pid", "num_keys", "tcps_active", "open_fds", "rpcs_active"]
            header = ["Nodes info:"] + ["Node %d" % (i + 1) for i in range(len(self.nodes))]
            table = [[k] for k in keys]
            for node in self.nodes:
                for i, k in enumerate(keys):
                    table[i].append(node[k])
            H2ODisplay(table=table, header=header)


    def network_test(self):
        """Test network connectivity."""
        res = h2o.api("GET /3/NetworkTest")
        res["table"].show()



    def list_all_extensions(self):
        """List all available extensions on the h2o backend"""
        return self._list_extensions("Capabilities")


    def list_core_extensions(self):
        """List available core extensions on the h2o backend"""
        return self._list_extensions("Capabilities/Core")


    def list_api_extensions(self):
        """List available API extensions on the h2o backend"""
        return self._list_extensions("Capabilities/API")

    @property
    def timezone(self):
        """Current timezone of the H2O cluster."""
        return h2o.rapids("(getTimeZone)")["string"]

    @timezone.setter
    def timezone(self, tz):
        assert_is_type(tz, str)
        h2o.rapids('(setTimeZone "%s")' % tz)


    def list_timezones(self):
        """Return the list of all known timezones."""
        from h2o.expr import ExprNode
        return h2o.H2OFrame._expr(expr=ExprNode("listTimeZones"))._frame()


    #-------------------------------------------------------------------------------------------------------------------
    # Private
    #-------------------------------------------------------------------------------------------------------------------

    def _fill_from_h2ocluster(self, other):
        """
        Update information in this object from another H2OCluster instance.

        :param H2OCluster other: source of the new information for this object.
        """
        self._props = other._props
        self._retrieved_at = other._retrieved_at
        other._props = {}
        other._retrieved_at = None

    def _list_extensions(self, endpoint):
        res = h2o.api("GET /3/" + endpoint)["capabilities"]
        return [x["name"] for x in res]


_cloud_v3_valid_keys = {"is_client", "build_number", "cloud_name", "locked", "node_idx", "consensus", "branch_name",
                        "version", "cloud_uptime_millis", "cloud_internal_timezone", "datafile_parser_timezone", "cloud_healthy", "bad_nodes", "cloud_size", "skip_ticks",
                        "nodes", "build_age", "build_too_old", "internal_security_enabled"}
