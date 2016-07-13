# -*- encoding: utf-8 -*-
"""

 :copyright: (c) 2016 H2O.ai
 :license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.compatibility import *
import requests
import tempfile
import os
import re
import sys
import time
import subprocess
import atexit
import warnings
from sysconfig import get_config_var
from random import choice
from requests.auth import AuthBase

from .utils.backward_compatibility import backwards_compatible
from .h2o_logging import is_logging, log_rest
from .two_dim_table import H2OTwoDimTable
from .utils.shared_utils import stringify_list
from .schemas.cloud import CloudV3
from .schemas.error import H2OErrorV3, H2OModelBuilderErrorV3

try:
    warnings.simplefilter("ignore", requests.packages.urllib3.exceptions.InsecureRequestWarning)
except:
    pass

__all__ = ("H2OConnection", "H2OStartupError", "H2OConnectionError", "H2OServerError", "H2OResponseError")



class H2OConnection(backwards_compatible()):
    """
    Single connection to an H₂O server.

    There are two main methods for establishing a connection:
        conn = H2OConnection.connect(...)  connect to an existing H₂O server;
        conn = H2OConnection.start(...)    launch a new local H₂O server and then connect to it.
    We will autom
    You can also use this class as a context manager:
        with H2OConnection.connect() as conn:
            conn.info().pprint()
    The connection will be automatically closed at the end of the `with ...` block.

    This class contains methods for performing the common REST methods GET, POST, and DELETE.

    TODO: Maybe move start() and 4 private methods _jar_paths, _start_h2o_server, _find_java, _tmp_file into a separate
    class H2OLocalServer. Better communicate with the subprocess. Right now if the server dies, no exception is
    raised anywhere. Also if the server decides to start listening to a port other than 54321, we have no way of
    knowing this.
    """

    @staticmethod
    @translate_args
    def connect(ip="localhost", port=54321, https=False, verify_ssl_cert=True, auth=None, proxy=None,
                cluster_name=None, verbose=True):
        """
        Connect to an existing H₂O server at address ip:port, either via http or https.

        The connection is not kept alive, so what this method actually does is it attempts to connect to the
        specified server, checks that the server is healthy and responds to REST API requests, and finally opens a new
        session on the server. The parameters of the connection are stored so that all subsequent requests do not
        need to specify them.
        If the H₂O server cannot be reached, an H2OConnectionError will be raised.

        Note: connecting to the server will effectively lock the cloud.

        :param ip: Server's IP address, default is "localhost".
        :param port: Server's port, default is 54321.
        :param https: Set this to True to use https instead of http.
        :param verify_ssl_cert: Set to False to disable SSL certificate checking; has no effect if https=False.
        :param auth: Authenticator object (from requests.auth), or a (username, password) tuple.
        :param proxy: (str) URL address of a proxy server.
        :param cluster_name: Name of the H₂O cluster to connect to. This option is used from Steam only.
        :param verbose: If True, then connection progress info will be printed to the stdout.
        :return self
        :raise H2OConnectionError if the server cannot be reached
        :raise H2OServerError if the server is in an unhealthy state (although this is a recoverable error, the client
                itself should decide whether it wants to retry or not)
        """
        assert isinstance(ip, str), "`ip` must be a string, got %r" % ip
        assert isinstance(port, (str, int)), "`port` must be an integer, got %r" % port
        port = int(port)  # We also allow `port` to be a string that is convertible into a number, for convenience
        assert 1 <= port <= 65535, "Invalid `port` number: %d" % port
        assert https is None or isinstance(https, bool), "`https` should be boolean, got %r" % https
        assert proxy is None or isinstance(proxy, str), "`proxy` must be a string, got %r" % proxy
        assert auth is None or isinstance(auth, tuple) and len(auth) == 2 or isinstance(auth, AuthBase), \
            "Invalid authentication token: %r" % auth
        assert cluster_name is None or isinstance(cluster_name, str), \
            "`cluster_name` must be a string, got %r" % cluster_name

        conn = H2OConnection()
        conn._verbose = bool(verbose)
        conn._ip = ip
        conn._port = port
        conn._https = https
        conn._scheme = "https" if https else "http"
        conn._base_url = "%s://%s:%d" % (conn._scheme, conn._ip, conn._port)
        conn._verify_ssl_cert = bool(verify_ssl_cert)
        conn._auth = auth
        conn._cluster_name = cluster_name
        conn._proxies = None
        if proxy:
            conn._proxies = {conn._scheme: proxy}
        elif proxy is not None:
            # Give user a warning if there are any "*_proxy" variables in the environment. [PUBDEV-2504]
            # To suppress the warning just pass the proxy setting explicitly.
            for name in os.environ:
                if name.lower() == conn._scheme + "_proxy":
                    warnings.warn("Proxy is defined in the environment: %s. "
                                  "This may interfere with your H2O Connection." %
                                  os.environ[name])

        try:
            # Make a fake _session_id, otherwise .api() will complain that the connection is not initialized
            conn._stage = 1
            conn._cluster_info = conn._test_connection()
            atexit.register(lambda: conn.close())
        except Exception:
            # Reset _session_id so that we know the connection was not initialized properly.
            conn._stage = 0
            raise
        return conn


    @staticmethod
    @translate_args
    def start(jar_path=None, nthreads=-1, enable_assertions=True, max_mem_size=None, min_mem_size=None,
              ice_root=None, verbose=True):
        """
        Start new H₂O server locally and then connect to to it.

        :param jar_path: Path to the h2o.jar executable. If not given, then we will search for that executable in the
                locations suggested by ._jar_paths().
        :param nthreads: Number of threads in the thread pool. This relates very closely to the number of CPUs used.
                -1 means use all CPUs on the host. A positive integer specifies the number of CPUs directly.
        :param enable_assertions: If True, pass `-ea` option to the JVM.
        :param max_mem_size: Maximum heap size (jvm option Xmx), in bytes.
        :param min_mem_size: Minimum heap size (jvm option Xms), in bytes.
        :param ice_root: A directory where H₂O stores its temporary files. Default location is determined by
                tempfile.mkdtemp().
        :param verbose: If True, then connection info will be printed to the stdout.
        :return self
        """
        assert jar_path is None or isinstance(jar_path, str), "`jar_path` should be string, got %r" % jar_path
        assert isinstance(nthreads, int), "`nthreads` should be integer, got %r" % nthreads
        assert -1 <= nthreads <= 1024, "`nthreads` is out of bounds: %d" % nthreads
        assert max_mem_size is None or isinstance(max_mem_size, int), \
            "`max_mem_size` should be integer, got %r" % max_mem_size
        assert min_mem_size is None or isinstance(min_mem_size, int), \
            "`min_mem_size` should be integer, got %r" % min_mem_size
        assert max_mem_size is None or max_mem_size >= 1 << 25, "`max_mem_size` too small: %d" % max_mem_size
        assert min_mem_size is None or max_mem_size is None or min_mem_size <= max_mem_size, \
            "`min_mem_size`=%d is larger than the `max_mem_size`=%d" % (min_mem_size, max_mem_size)
        assert ice_root is None or isinstance(ice_root, str), "`ice_root` should be string, got %r" % ice_root

        # Find location of the h2o.jar executable
        resolved_jar_path = None
        for jp in H2OConnection._jar_paths(jar_path):
            if os.path.exists(jp):
                resolved_jar_path = jp
                break
        if not resolved_jar_path:
            if verbose:
                print("No jar file found. Paths searched:")
                print("".join("    %s\n" % jp for jp in H2OConnection._jar_paths(jar_path)))
            raise H2OStartupError("Cannot start local server: h2o.jar not found.")

        # Even though this creates a new folder, we will not attempt to clear it on shutdown, so that server logs can
        # be later examined by the user.
        if not ice_root: ice_root = tempfile.mkdtemp()

        # Start local jar
        if verbose: print("Starting server from " + resolved_jar_path)
        (ip, port, child) = H2OConnection._start_h2o_server(jar_path=resolved_jar_path, nthreads=int(nthreads),
                                                            ea=enable_assertions, logs_dir=ice_root,
                                                            mmax=max_mem_size, mmin=min_mem_size, verbose=verbose)
        conn = H2OConnection.connect(ip=ip, port=port, verbose=verbose)
        conn._child = child
        return conn


    def api(self, endpoint, data=None, json=None, filename=None):
        """
        Perform a REST API request to the backend H₂O server.

        :param endpoint: (str) The endpoint's URL, for example "GET /4/schemas/KeyV4"
        :param data: data payload for POST (and sometimes GET) requests. This should be a dictionary of simple
            key/value pairs (values can also be arrays), which will be sent over in x-www-form-encoded format.
        :param json: also data payload, but it will be sent as a JSON body. Cannot be used together with `data`.
        :param filename: file to upload to the server. Cannot be used with `data` or `json`.
        :return: an H2OResponse object representing the server's response
        :raise ValueError if the endpoint's URL is invalid
        :raise H2OConnectionError if the H₂O server cannot be reached (or connection is not initialized)
        :raise H2OServerError if there was a server error (http 500), or server returned malformed JSON
        :raise H2OResponseError if the server returned an H2OErrorV3 response (e.g. if the parameters were invalid)
        """
        if self._stage == 0: raise H2OConnectionError("Connection not initialized; run .connect() first.")
        if self._stage == -1: raise H2OConnectionError("Connection was closed, and can no longer be used.")

        # Prepare URL
        if endpoint.count(" ") != 1:
            raise ValueError("Incorrect endpoint '%s': must be of the form 'METHOD URL'." % endpoint)
        method, urltail = endpoint.split(" ", 2)
        if method not in {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD"}:
            raise ValueError("Incorrect method in endpoint '%s'" % endpoint)
        if urltail[0] != "/":
            raise ValueError("Incorrect url in endpoint '%s': should start with '/'" % endpoint)
        url = self._base_url + urltail

        # Prepare data
        if bool(data) + bool(json) + bool(filename) > 1:
            raise ValueError("Only one of parameters `json`, `data`, `file` can be supplied.")
        if filename is not None and method != "POST":
            raise ValueError("File uploads can only be done via POST method, got %s" % endpoint)
        data = self._prepare_data_payload(data)
        files = self._prepare_file_payload(filename)
        params = None
        if method == "GET" and data:
            params = data
            data = None

        # Make the requst
        try:
            start_time = time.time()
            self._log_start_transaction(endpoint, data, json, files, params)
            headers = {"User-Agent": "H2O Python client/" + sys.version.replace("\n", ""),
                       "X-Cluster": self._cluster_name}
            resp = requests.request(method=method, url=url, data=data, json=json, files=files, params=params,
                                    headers=headers,
                                    auth=self._auth, verify=self._verify_ssl_cert, proxies=self._proxies)
            self._log_end_transaction(start_time, resp)
            return self._process_response(resp)

        except requests.ConnectionError as e:
            self._log_end_exception(e)
            raise H2OConnectionError("Unexpected HTTP error %s" % e)
        except H2OResponseError as e:
            err = e.args[0]
            err.endpoint = endpoint
            err.payload = (data, json, files, params)
            raise


    def info(self, refresh=False):
        if self._stage == 0: return None
        if refresh:
            self._cluster_info = self.api("GET /3/Cloud")
        self._cluster_info.connection = self
        return self._cluster_info


    def close(self):
        if self._session_id:
            try:
                self.api("DELETE /4/sessions/%s" % self._session_id)
                self._print("H2O session %s closed." % self._session_id)
            except:
                pass
            self._session_id = None
        if self._child:
            try:
                self._child.kill()
                self._print("Local H2O server stopped.")
            except:
                pass
            self._child = None
        self._stage = -1


    def session_id(self):
        if self._session_id is None:
            self._session_id = self.api("POST /4/sessions")["session_key"]
        return self._session_id

    @property
    def base_url(self):
        """Base URL of the server, for example "https://example.com:54321"."""
        return self._base_url

    @property
    def proxy(self):
        if self._proxies is None: return
        return self._proxies[self._scheme]

    @property
    def requests_count(self):
        return self._requests_counter


    def shutdown(self, prompt):
        """
        Shut down the specified server. All data will be lost.
        This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O
        instance.
        :param self: An H2OConnection object containing the IP address and port of the server running H2O.
        :param prompt: A logical value indicating whether to prompt the user before shutting down the H2O server.
        :return: None
        """
        try:
            if not self.cluster_is_up():
                raise ValueError("There is no H2O instance running at " + self._base_url)
        except:
            # H2O is already shutdown on the java side
            raise ValueError("The H2O instance running at %s has already been shutdown." % self._base_url)
        if not isinstance(prompt, bool): raise ValueError("`prompt` must be boolean")
        if prompt:
            question = "Are you sure you want to shutdown the H2O instance running at %s (Y/N)? " % self._base_url
            response = input(question)  # works in Py2 & Py3 because it's future.builtins.input
        else:
            response = "Y"
        if response == "Y" or response == "y":
            self.api("POST /3/Shutdown")
            self.close()


    def cluster_is_up(self):
        """
        Determine if an H2O cluster is up or not.

        :return: True if the cluster is up; False otherwise
        """
        rv = self.api("GET /")
        if rv.status_code == 401:
            warnings.warn("401 Unauthorized Access. Did you forget to provide a username and password?")
        return rv.status_code == 200 or rv.status_code == 301



    #-------------------------------------------------------------------------------------------------------------------
    # PRIVATE
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self):
        """[Internal] Please use H2OConnection.connect() or H2OConnection.start() to create H2OConnection objects."""
        super(H2OConnection, self).__init__()
        globals()["__H2OCONN__"] = self  # for backward-compatibility
        self._stage = 0             # 0 = not connected, 1 = connected, -1 = disconnected
        self._session_id = None     # Rapids session id. Connection is considered established when this is not null
        self._ip = None             # "host" part of the URL (why is it called ip?)
        self._port = None           # "port" part of the URL
        self._https = None          # True if using secure connection (https://), False if using http://
        self._scheme = None         # "http" or "https"
        self._base_url = None       # "{scheme}://{ip}:{port}"
        self._verify_ssl_cert = None
        self._auth = None           # Authentication token
        self._proxies = None        # `proxies` dictionary in the format required by the requests module
        self._cluster_name = None
        self._cluster_info = None   # Latest result of "GET /3/Cloud" request
        self._verbose = None
        self._child = None
        self._requests_counter = 0  # how many API requests were made


    def _test_connection(self, max_retries=5):
        """
        Test that the H2O cluster can be reached, and retrieve basic cloud status info.

        :param max_retries: Number of times to try to connect to the cloud (with 0.2s intervals)
        :return Cloud information (a CloudV3 object)
        :raise H2OConnectionError, H2OServerError
        """
        self._print("Connecting to H2O server at " + self._base_url, end="..")
        cld = None
        for _ in range(max_retries):
            self._print(".", end="", flush=True)
            if self._child and self._child.poll() is not None:
                raise H2OServerError("Local server was unable to start")
            try:
                cld = self.api("GET /3/Cloud")
                if cld.consensus and cld.cloud_healthy:
                    self._print(" successful!")
                    return cld
            except (H2OConnectionError, H2OServerError):
                pass
            # Cloud too small, or voting in progress, or server is not up yet; sleep then try again
            time.sleep(0.2)

        self._print(" failed.")
        if cld and not cld.cloud_healthy:
            raise H2OServerError("Cluster reports unhealthy status")
        if cld and not cld.consensus:
            raise H2OServerError("Cluster cannot reach consensus")
        else:
            raise H2OConnectionError("Could not establish link to the H2O cloud %s after %d retries"
                                     % (self._base_url, max_retries))


    @staticmethod
    def _jar_paths(path0=None):
        """
        Produce potential paths for an h2o.jar executable. This function knows several locations where the H2O
        library might get installed, and returns them in sequence.

        :param path0: If given, this will be the first path yielded by this function
        :return generator of potential paths for h2o.jar executable
        """
        # First yield `path0` as promised
        if path0: yield path0
        # Second, check if running from an h2o-3 src folder, in which case use the freshly-built h2o.jar
        cwd_chunks = os.path.abspath(".").split("/")
        for i in range(len(cwd_chunks) - 1, -1, -1):
            if cwd_chunks[i] == "h2o-3":
                yield "/".join(cwd_chunks[:i + 1]) + "/build/h2o.jar"
                break
        # Finally try several alternative locations where h2o.jar might be installed
        prefix1 = prefix2 = sys.prefix.replace("\\", "/")  # sys.prefix is the system path of the Python folder
        # On Unix-like systems Python typically gets installed into /Library/... or /System/Library/... If one of
        # those paths is sys.prefix, then we also build its counterpart.
        if prefix1.startswith("/Library"):
            prefix2 = "/System" + prefix1
        elif prefix1.startswith("/System"):
            prefix2 = prefix1[len("/System"):]
        yield prefix1 + "/h2o_jar/h2o.jar"
        yield "/usr/local/h2o_jar/h2o.jar"
        yield prefix1 + "/local/h2o_jar/h2o.jar"
        yield get_config_var("userbase") + "/h2o_jar/h2o.jar"
        yield prefix2 + "/h2o_jar/h2o.jar"


    @staticmethod
    def _start_h2o_server(mmax, mmin, ea, logs_dir, jar_path, nthreads, verbose):
        """
        Actually start the h2o.jar executable (helper method for H2OConnection.start()).
        :return tuple (ip, port, child), where `child` is the handle to the server's process.
        """
        # For now we hardcode the server's location. Eventually we might want to explore multiple ports  until we find
        # an unoccupied one.
        ip = "127.0.0.1"
        port = 54321

        # Find Java executable
        command = H2OConnection._find_java()
        if not command:
            raise H2OStartupError("Cannot find Java. Please install the latest JDK from\n"
                                  "http://www.oracle.com/technetwork/java/javase/downloads/index.html")

        # Detect Java version. (Note that subprocess.check_output returns the output as a bytes object, not string)
        jver_bytes = subprocess.check_output([command, "-version"], stderr=subprocess.STDOUT)
        jver = jver_bytes.decode(encoding="utf-8", errors="ignore")
        if verbose:
            print("Java Version: " + jver.strip().replace("\n", "; "))
        if "GNU libgcj" in jver:
            raise H2OStartupError("Sorry, GNU Java is not supported for H2O.\n"
                                  "Please download the latest 64-bit Java SE JDK from Oracle.")
        if "Client VM" in jver:
            warnings.warn("WARNING:\n"
                          "You have a 32-bit version of Java. H2O works best with 64-bit Java.\n"
                          "Please download the latest 64-bit Java SE JDK from Oracle.\n")

        # Construct java command to launch the process
        cmd = [command]

        # ...add JVM options
        cmd += ["-ea"] if ea else []
        for (mq, num) in [("-Xms", mmin), ("-Xmx", mmax)]:
            if num is None: continue
            numstr = "%dG" % (num >> 30) if num == (num >> 30) << 30 else \
                     "%dM" % (num >> 20) if num == (num >> 20) << 20 else \
                     str(num)
            cmd += [mq + numstr]
        cmd += ["-verbose:gc", "-XX:+PrintGCDetails", "-XX:+PrintGCTimeStamps"]
        cmd += ["-jar", jar_path]  # This should be the last JVM option

        # ...add H2O options
        cmd += ["-ip", ip]
        cmd += ["-port", str(port)]
        cmd += ["-ice_root", logs_dir]
        cmd += ["-nthreads", str(nthreads)] if nthreads > 0 else []
        cmd += ["-name", "H2O_from_python_%s" % H2OConnection._tmp_file("salt")]

        # Create stdout and stderr files
        cwd = os.path.abspath(os.getcwd())
        out = open(H2OConnection._tmp_file("stdout"), "w")
        err = open(H2OConnection._tmp_file("stderr"), "w")
        if verbose:
            print("Ice root: " + logs_dir)
            print("JVM stdout: " + out.name)
            print("JVM stderr: " + err.name)

        # Launch the process
        win32 = sys.platform == "win32"
        flags = subprocess.CREATE_NEW_PROCESS_GROUP if win32 else 0
        prex = os.setsid if not win32 else None
        try:
            child = subprocess.Popen(args=cmd, stdout=out, stderr=err, cwd=cwd, creationflags=flags, preexec_fn=prex)
        except OSError as e:
            raise H2OServerError("Cannot start server: %s" % e)
        time.sleep(0.5)  # Give the server some time to start
        if child.poll() is not None:
            raise H2OServerError("Server process terminated with error code %d" % child.poll())
        return ip, port, child


    @staticmethod
    def _find_java():
        """
        Find location of the java executable (helper for ._start_h2o_server()). This method is not particularly robust,
        and may require additional tweaking for different platforms...
        """
        # is java in PATH?
        if os.access("java", os.X_OK):
            return "java"
        for path in os.getenv("PATH").split(os.pathsep):
            full_path = os.path.join(path, "java")
            if os.access(full_path, os.X_OK):
                return full_path

        # check if JAVA_HOME is set (for Windows)
        if os.getenv("JAVA_HOME"):
            return os.path.join(os.getenv("JAVA_HOME"), "bin", "java.exe")

        # check "/Program Files" and "/Program Files (x86)" on Windows
        if sys.platform == "win32":
            program_folders = [os.path.join("C:", "Program Files", "Java"),
                               os.path.join("C:", "Program Files (x86)", "Java")]
            # Look for JDK
            for folder in program_folders:
                for jdk in os.listdir(folder):
                    if "jdk" not in jdk.lower(): continue
                    path = os.path.join(folder, jdk, "bin", "java.exe")
                    if os.path.exists(path):
                        return path
            # check for JRE and warn
            for folder in program_folders:
                path = os.path.join(folder, "jre7", "bin", "java.exe")
                if os.path.exists(path):
                    warnings.warn("Found JRE at " + path + "; but H2O requires the JDK to run.")


    @staticmethod
    def _tmp_file(kind):
        """
        Generate names for temporary files. `kind` is one of "stdout", "stderr", "pid" and "salt". The "salt" kind
        is used for process name, not for a file, so it doesn't contain a path. All generated names are based on the
        user name of the currently logged-in user. This is a helper method for ._start_h2o_server().
        """
        username = (os.getenv("USERNAME") if sys.platform == "win32" else os.getenv("USER")) or "unknownUser"
        usr = re.sub(r"\W", "_", username)
        if kind == "salt":
            return usr + "_" + "".join(choice("0123456789abcdefghijklmnopqrstuvwxyz") for _ in range(6))
        ext = "pid" if kind == "pid" else kind[3:]
        return os.path.join(tempfile.mkdtemp(), "h2o_%s_started_from_python.%s" % (usr, ext))


    @staticmethod
    def _prepare_data_payload(data):
        """
        Make a copy of the `data` object, preparing it to be sent to the server via x-www-form-urlencoded or
        multipart/form-data mechanisms. Both of them work with plain lists of key/value pairs, so this method
        converts the data into such format.
        """
        if not data: return None
        if not isinstance(data, dict): raise ValueError("Invalid `data` argument, should be a dict: %r" % data)
        res = {}
        for key, value in iteritems(data):
            if value is None: continue  # don't send args set to None so backend defaults take precedence
            if isinstance(value, list):
                res[key] = stringify_list(value)
            elif isinstance(value, dict) and "__meta" in value and value["__meta"]["schema_name"].endswith("KeyV3"):
                res[key] = value["name"]
            else:
                res[key] = str(value)
            if PY2 and hasattr(res[key], "__native__"): res[key] = res[key].__native__()
        return res


    @staticmethod
    def _prepare_file_payload(filename):
        """
        Prepare `filename` to be sent to the server. The "preparation" consists of creating a data structure suitable
        for passing to requests.request().
        """
        if not filename: return None
        if not isinstance(filename, str): raise ValueError("Parameter `filename` must be a string: %r" % filename)
        absfilename = os.path.abspath(filename)
        if not os.path.exists(absfilename):
            raise ValueError("File %s does not exist" % filename)
        return {os.path.basename(absfilename): open(absfilename, "rb")}


    def _log_start_transaction(self, endpoint, data, json, files, params):
        """Log the beginning of an API request."""
        self._requests_counter += 1
        if not is_logging(): return
        msg = "---- %d --------------------------------------------------------\n" % self._requests_counter
        msg += "[%s] %s\n" % (time.strftime("%H:%M:%S"), endpoint)
        if data is not None:   msg += "     body: {%s}\n" % ", ".join("%s:%s" % item for item in iteritems(data))
        if json is not None:   msg += "     json: %s\n" % json.dumps(json)
        if files is not None:  msg += "     file: %s\n" % ", ".join(iterkeys(files))
        if params is not None: msg += "     params: %s\n" % ", ".join(iterkeys(params))
        log_rest(msg + "\n")

    @staticmethod
    def _log_end_transaction(start_time, response):
        """Log response from an API request."""
        if not is_logging(): return
        elapsed_time = int((time.time() - start_time) * 1000)
        msg = "<<< HTTP %d %s   (%d ms)\n" % (response.status_code, response.reason, elapsed_time)
        if "Content-Type" in response.headers:
            msg += "    Content-Type: %s\n" % response.headers["Content-Type"]
        msg += response.text
        log_rest(msg + "\n\n")

    @staticmethod
    def _log_end_exception(exception):
        """Log API request that resulted in an exception."""
        if not is_logging(): return
        log_rest(">>> %s\n\n" % str(exception))


    @staticmethod
    def _process_response(response):
        """
        Given a response object, prepare it to be handed over to the external caller. Preparation steps include:
           * detect if the response has error status, and convert it to an appropriate exception;
           * detect Content-Type, and based on that either parse the response as JSON or return as plain text.
        """
        content_type = response.headers["Content-Type"] if "Content-Type" in response.headers else ""
        if ";" in content_type:
            content_type = content_type[:content_type.index(";")]
        status_code = response.status_code

        # Auto-detect response type by its content-type. Decode JSON, all other responses pass as-is.
        if content_type == "application/json":
            try:
                data = response.json(object_pairs_hook=H2OResponse)
            except JSONDecodeError as e:
                raise H2OServerError("Malformed JSON from server (%s):\n%s" % (str(e), response.text))
        else:
            data = response.text

        # Success (200 = "Ok", 201 = "Created", 202 = "Accepted", 204 = "No Content")
        if status_code in {200, 201, 202, 204}:
            return data

        # Client errors (400 = "Bad Request", 404 = "Not Found", 412 = "Precondition Failed")
        if status_code in {400, 404, 412} and isinstance(data, (H2OErrorV3, H2OModelBuilderErrorV3)):
            raise H2OResponseError(data)

        # Server errors
        raise H2OServerError("HTTP %d %s:\n%r" % (status_code, response.reason, data))



    def _print(self, *args, **kwargs):
        """Helper function to print connection status messages when in "verbose" mode."""
        if self._verbose:
            flush = False
            if "flush" in kwargs: flush = kwargs.pop("flush")
            print(*args, **kwargs)
            if flush: sys.stdout.flush()


    def __repr__(self):
        if self._stage == 0:
            return "<H2OConnection uninitialized>"
        elif self._stage == 1:
            sess = "session %s" % self._session_id if self._session_id else "no session"
            return "<H2OConnection to %s, %s>" % (self._base_url, sess)
        else:
            return "<H2OConnection closed>"

    def __enter__(self):
        # Called when an H2OConnection object is created within the `with ...` statement.
        return self

    def __exit__(self, *args):
        # Called at the end of the `with ...` block.
        self.close()
        assert len(args) == 3  # Avoid warning about unused args...
        return False  # ensure that any exception will be re-raised

    def __del__(self):
        # Called when the object is being garbage-collected, but not always...
        self.close()


    #-------------------------------------------------------------------------------------------------------------------
    # DEPRECATED
    #
    # Access to any of these vars / methods will produce deprecation warnings.
    # Consult backwards_compatible.py for the description of these vars.
    #
    # These methods are deprecated since July 2016. Please remove them if it's 2017 already...
    #-------------------------------------------------------------------------------------------------------------------

    _bcsv = {"__ENCODING__": "utf-8", "__ENCODING_ERROR__": "replace"}
    _bcsm = {
        "default": lambda: _deprecated_default(),
        "jar_paths": lambda: list(H2OConnection._jar_paths()),
        "rest_version": lambda: 3,
        "ip": lambda: getattr(__H2OCONN__, "_ip"),
        "port": lambda: getattr(__H2OCONN__, "_port"),
        "https": lambda: getattr(__H2OCONN__, "_https"),
        "username": lambda: _deprecated_username(),
        "password": lambda: _deprecated_password(),
        "insecure": lambda: not getattr(__H2OCONN__, "_verify_ssl_cert"),
        "current_connection": lambda: __H2OCONN__,
        "check_conn": lambda: _deprecated_check_conn(),
        # "cluster_is_up" used to be static (required `conn` parameter) -> now it's non-static w/o any patching needed
        "make_url": lambda url_suffix, _rest_version=3: __H2OCONN__.make_url(url_suffix, _rest_version),
        "get": lambda url_suffix, **kwargs: __H2OCONN__.get(url_suffix, **kwargs),
        "post": lambda url_suffix, file_upload_info=None, **kwa: __H2OCONN__.post(url_suffix, file_upload_info, **kwa),
        "delete": lambda url_suffix, **kwargs: __H2OCONN__.delete(url_suffix, **kwargs),
        "get_json": lambda url_suffix, **kwargs: __H2OCONN__.get_json(url_suffix, **kwargs),
        "post_json": lambda url_suffix, file_upload_info=None, **kwa:
            __H2OCONN__.post_json(url_suffix, file_upload_info, **kwa),
        "rest_ctr": lambda: __H2OCONN__.requests_count,
    }
    _bcim = {
        "make_url": lambda self, url_suffix, _rest_version=3:
            "/".join([self._base_url, str(_rest_version), url_suffix]),
        "get": lambda *args, **kwargs: _deprecated_get(*args, **kwargs),
        "post": lambda *args, **kwargs: _deprecated_post(*args, **kwargs),
        "delete": lambda *args, **kwargs: _deprecated_delete(*args, **kwargs),
        "get_json": lambda *args, **kwargs: _deprecated_get(*args, **kwargs),
        "post_json": lambda *args, **kwargs: _deprecated_post(*args, **kwargs),
    }



class H2OStartupError(Exception):
    """
    Raised when the class fails to launch a local H₂O server.
    """
    pass

class H2OConnectionError(Exception):
    """
    Raised when connection to an H₂O server cannot be established. This can be raised if the connection was not
    initialized; or the server cannot be reached at the specified address; or there is an authentication error; or
    the request times out; etc.
    """
    pass

class H2OServerError(Exception):
    """
    This exception is raised when any kind of server error is encountered. This includes: server returning http
    status 500; or server sending malformed JSON; or server returning an unexpected response (e.g. lacking a
    "__schema" field); or server indicating that it is in an unhealthy state.
    """
    pass


# This should have been extending from Exception as well; however in old code version all exceptions were
# EnvironmentError's, so for old code to work we extend H2OResponseError from EnvironmentError.
class H2OResponseError(EnvironmentError):
    """
    This exception is raised when the server encounters a user error and sends back an H2OErrorV3 response.
    """
    pass




class H2OResponse(dict):

    def __new__(cls, keyvals):
        # This method is called by the simplejson.json(object_pairs_hook=<this>)
        # `keyvals` is a list of (key,value) tuples. For example:
        #    [("schema_version", 3), ("schema_name", "InitIDV3"), ("schema_type", "Iced")]
        schema = None
        for k, v in keyvals:
            if k == "__meta" and isinstance(v, dict):
                schema = v["schema_name"]
                break
            if k == "__schema" and isinstance(v, str):
                schema = v
                break
        if schema == "CloudV3": return CloudV3(keyvals)
        if schema == "H2OErrorV3": return H2OErrorV3(keyvals)
        if schema == "H2OModelBuilderErrorV3": return H2OModelBuilderErrorV3(keyvals)
        if schema == "TwoDimTableV3": return H2OTwoDimTable.make(keyvals)
        return super(H2OResponse, cls).__new__(cls, keyvals)

    # def __getattr__(self, key):
    #     """This gets invoked for any attribute "key" that is NOT yet defined on the object."""
    #     if key in self:
    #         return self[key]
    #     return None


# Find the exception that occurs on invalid JSON input
JSONDecodeError, _r = None, None
try:
    _r = requests.Response()
    _r._content = b"haha"
    _r.json()
except Exception as exc:
    JSONDecodeError = type(exc)
    del _r


#-----------------------------------------------------------------------------------------------------------------------
# Deprecated method implementations
#-----------------------------------------------------------------------------------------------------------------------

__H2OCONN__ = None            # Latest instantiated H2OConnection object. Do not use in any new code!
__H2O_REST_API_VERSION__ = 3  # Has no actual meaning

def _deprecated_default():
    H2OConnection.__ENCODING__ = "utf-8"
    H2OConnection.__ENCODING_ERROR__ = "replace"

def _deprecated_username():
    auth = getattr(__H2OCONN__, "_auth")
    if isinstance(auth, tuple) and len(auth) == 2:
        return auth[0]

def _deprecated_password():
    auth = getattr(__H2OCONN__, "_auth")
    if isinstance(auth, tuple) and len(auth) == 2:
        return auth[1]

def _deprecated_check_conn():
    if not __H2OCONN__:
        raise H2OConnectionError("No active connection to an H2O cluster. Try calling `h2o.connect()`")
    return __H2OCONN__

def _deprecated_get(self, url_suffix, **kwargs):
    restver = kwargs.pop("_rest_version") if "_rest_version" in kwargs else 3
    endpoint = "GET /%d/%s" % (restver, url_suffix)
    return self.api(endpoint, data=kwargs)

def _deprecated_post(self, url_suffix, **kwargs):
    restver = kwargs.pop("_rest_version") if "_rest_version" in kwargs else 3
    endpoint = "POST /%d/%s" % (restver, url_suffix)
    filename = None
    if "file_upload_info" in kwargs:
        filename = kwargs.pop("file_upload_info").values()[0]
    return self.api(endpoint, data=kwargs, filename=filename)

def _deprecated_delete(self, url_suffix, **kwargs):
    restver = kwargs.pop("_rest_version") if "_rest_version" in kwargs else 3
    endpoint = "DELETE /%d/%s" % (restver, url_suffix)
    return self.api(endpoint, data=kwargs)


def end_session():
    print("Warning: end_session() is deprecated")
    __H2OCONN__.close()
