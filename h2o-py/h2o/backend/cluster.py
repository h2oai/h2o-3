# -*- encoding: utf-8 -*-
"""Information about the backend H2O cluster."""
from __future__ import division, print_function, absolute_import, unicode_literals

import sys
import time
import json

import h2o
from h2o.exceptions import H2OConnectionError, H2OServerError
from h2o.display import H2ODisplay
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.typechecks import assert_is_type
from h2o.utils.shared_utils import get_human_readable_bytes, get_human_readable_time
from h2o.two_dim_table import H2OTwoDimTable


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
        keys = _cluster_status_info_keys
        values = self._get_cluster_status_info_values()
        table = [[k+":", values[i]] for i, k in enumerate(keys)]
        H2ODisplay(table)

        if detailed:
            keys = _cluster_status_detailed_info_keys
            header = ["Nodes info:"] + ["Node %d" % (i + 1) for i in range(len(self.nodes))]
            table = [[k]+[node[k] for node in self.nodes] for k in keys]
            H2ODisplay(table=table, header=header)

    def get_status(self):
        """
        Returns H2OTwoDimTable with current cluster status information.
        """
        keys = _cluster_status_info_keys
        values = self._get_cluster_status_info_values()
        table = H2OTwoDimTable(cell_values=[values], col_header=keys)
        return table

    def get_status_details(self):
        """
        Returns H2OTwoDimTable with detailed current status information about each node.
        """
        if self._retrieved_at + self.REFRESH_INTERVAL < time.time():
            # Info is stale, need to refresh
            new_info = h2o.api("GET /3/Cloud")
            self._fill_from_h2ocluster(new_info)
        keys = _cluster_status_detailed_info_keys[:]
        node_table = [["Node %d" % (j + 1)] + [node[k] for k in keys] for j, node in enumerate(self.nodes)]
        keys.insert(0, "node")
        table = H2OTwoDimTable(cell_values=node_table, col_header=keys, row_header=keys)
        return table

    def network_test(self):
        """Test network connectivity."""
        res = h2o.api("GET /3/NetworkTest")
        res["table"].show()

    def list_jobs(self):
        """List all jobs performed by the cluster."""
        res = h2o.api("GET /3/Jobs")
        table = [["type"], ["dest"], ["description"], ["status"]]
        for job in res["jobs"]:
            job_dest = job["dest"]
            table[0].append(self._translate_job_type(job_dest["type"]))
            table[1].append(job_dest["name"])
            table[2].append(job["description"])
            table[3].append(job["status"])
        return table

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

    def _translate_job_type(self, type):
        if type is None:
            return ('Removed')
        type_mapping = dict([
            ('Key<Frame>', 'Frame'),
            ('Key<Model>', 'Model'),
            ('Key<Grid>', 'Grid'),
            ('Key<PartialDependence>', 'PartialDependence'),
            ('Key<AutoML>', 'Auto Model'),
            ('Key<ScalaCodeResult>', 'Scala Code Execution'),
            ('Key<KeyedVoid>', 'Void')
        ])
        if type in type_mapping:
            return type_mapping[type]
        else:
            return 'Unknown'

   

    def _get_cluster_status_info_values(self):
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
        values = [get_human_readable_time(self.cloud_uptime_millis),
                  self.cloud_internal_timezone,
                  self.datafile_parser_timezone,
                  self.version,
                  "{} {}".format(self.build_age, ("!!!" if self.build_too_old else "")),
                  self.cloud_name,
                  self.cloud_size,
                  get_human_readable_bytes(free_mem),
                  ncpus,
                  allowed_cpus,
                  status,
                  h2o.connection().base_url,
                  json.dumps(h2o.connection().proxy),
                  self.internal_security_enabled,
                  ', '.join(api_extensions),
                  "%d.%d.%d %s" % tuple(sys.version_info[:4])]
        return values


_cluster_status_info_keys = ["H2O_cluster_uptime", "H2O_cluster_timezone", "H2O_data_parsing_timezone",
            "H2O_cluster_version", "H2O_cluster_version_age", "H2O_cluster_name", "H2O_cluster_total_nodes",
            "H2O_cluster_free_memory", "H2O_cluster_total_cores", "H2O_cluster_allowed_cores", "H2O_cluster_status",
            "H2O_connection_url", "H2O_connection_proxy", "H2O_internal_security", "H2O_API_Extensions", "Python_version"]
    
_cluster_status_detailed_info_keys = ["h2o", "healthy", "last_ping", "num_cpus", "sys_load", "mem_value_size",
                                      "free_mem", "pojo_mem", "swap_mem", "free_disk", "max_disk", "pid", "num_keys",
                                      "tcps_active", "open_fds", "rpcs_active"]

_cloud_v3_valid_keys = {"is_client", "build_number", "cloud_name", "locked", "node_idx", "consensus", "branch_name",
                        "version", "last_commit_hash", "describe", "compiled_by", "compiled_on", "cloud_uptime_millis",
                        "cloud_internal_timezone", "datafile_parser_timezone", "cloud_healthy", "bad_nodes", "cloud_size", "skip_ticks",
                        "nodes", "build_age", "build_too_old", "internal_security_enabled", "leader_idx"}
