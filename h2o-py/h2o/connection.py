"""
An H2OConnection represents the latest active handle to a cloud. No more than a single
H2OConnection object will be active at any one time.
"""
from __future__ import print_function
from __future__ import absolute_import
import requests
import math
import tempfile
import os
import re
import sys
import time
import subprocess
import atexit
import warnings
import site
from .display import H2ODisplay
from .h2o_logging import _is_logging, _log_rest
from .two_dim_table import H2OTwoDimTable
from .utils.shared_utils import quote
from six import iteritems, PY3
from string import ascii_lowercase, digits
from random import choice
warnings.simplefilter('always', UserWarning)
try:
  warnings.simplefilter('ignore', requests.packages.urllib3.exceptions.InsecureRequestWarning)
except:
  pass
__H2OCONN__ = None            # the single active connection to H2O cloud
__H2O_REST_API_VERSION__ = 3  # const for the version of the rest api


class H2OConnection(object):
  """
  H2OConnection is a class that represents a connection to the H2O cluster.
  It is specified by an IP address and a port number.

  Objects of type H2OConnection are not instantiated directly!

  This class contains static methods for performing the common REST methods
  GET, POST, and DELETE.
  """

  __ENCODING__ = "utf-8"
  __ENCODING_ERROR__ = "replace"

  def __init__(self, ip, port, start_h2o, enable_assertions, license, nthreads, max_mem_size, min_mem_size, ice_root,
               strict_version_check, proxy, https, insecure, username, password, cluster_name, max_mem_size_GB, min_mem_size_GB, proxies, size):
    """
    Instantiate the package handle to the H2O cluster.
    :param ip: An IP address, default is "localhost"
    :param port: A port, default is 54321
    :param start_h2o: A boolean dictating whether this module should start the H2O jvm.
    :param enable_assertions: If start_h2o, pass `-ea` as a VM option.
    :param license: If not None, is a path to a license file.
    :param nthreads: Number of threads in the thread pool. This relates very closely to the number of CPUs used. 
    -1 means use all CPUs on the host. A positive integer specifies the number of CPUs directly. This value is only used when Python starts H2O.
    :param max_mem_size: Maximum heap size (jvm option Xmx). String specifying the maximum size, in bytes, of the memory allocation pool to H2O.
    This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.
    :param min_mem_size: Minimum heap size (jvm option Xms). String specifying the minimum size, in bytes, of the memory allocation pool to H2O.
    This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.
    :param ice_root: A temporary directory (default location is determined by tempfile.mkdtemp()) to hold H2O log files.
    :param strict_version_check: Setting this to False is unsupported and should only be done when advised by technical support.
    :param proxy: A dictionary with keys 'ftp', 'http', 'https' and values that correspond to a proxy path.
    :param https: Set this to True to use https instead of http.
    :param insecure: Set this to True to disable SSL certificate checking.
    :param username: Username to login with.
    :param password: Password to login with. 
    :param cluster_name: Cluster name to login to.
    :param max_mem_size_GB: DEPRECATED. Use max_mem_size.
    :param min_mem_size_GB: DEPRECATED. Use min_mem_size.
    :param proxies: DEPRECATED. Use proxy.
    :param size: DEPRECATED.
    :return: None
    """

    port = as_int(port)
    if not (isinstance(port, int) and 0 <= port <= sys.maxsize): raise ValueError("Port out of range, "+port)
    
    if https != insecure: raise ValueError("`https` and `insecure` must both be True to enable HTTPS")
    
    #Deprecated params
    if max_mem_size_GB is not None:
      warnings.warn("`max_mem_size_GB` is deprecated. Use `max_mem_size` instead.", category=DeprecationWarning)
      max_mem_size = max_mem_size_GB
    if min_mem_size_GB is not None:
      warnings.warn("`min_mem_size_GB` is deprecated. Use `min_mem_size` instead.", category=DeprecationWarning)
      min_mem_size = min_mem_size_GB
    if proxies is not None:
      warnings.warn("`proxies` is deprecated. Use `proxy` instead.", category=DeprecationWarning)
      proxy = proxies
    if size is not None:
      warnings.warn("`size` is deprecated.", category=DeprecationWarning)
    
    global __H2OCONN__
    self._cld = None
    self._ip = ip
    self._port = port
    self._proxy = proxy
    self._https = https
    self._insecure = insecure
    self._username = username
    self._password = password
    self._cluster_name = cluster_name
    self._session_id = None
    self._rest_version = __H2O_REST_API_VERSION__
    self._child = getattr(__H2OCONN__, "_child") if hasattr(__H2OCONN__, "_child") else None
    __H2OCONN__ = self

    #Give user warning if proxy environment variable is found. PUBDEV-2504
    for name, value in os.environ.items():
      if name.lower()[-6:] == '_proxy' and value:
        warnings.warn("Proxy environment variable `" + name + "` with value `" + value + "` found. This may interfere with your H2O Connection.")

    jarpaths = H2OConnection.jar_paths()
    if os.path.exists(jarpaths[0]):   jar_path = jarpaths[0]
    elif os.path.exists(jarpaths[1]): jar_path = jarpaths[1]
    elif os.path.exists(jarpaths[2]): jar_path = jarpaths[2]
    elif os.path.exists(jarpaths[3]): jar_path = jarpaths[3]
    elif os.path.exists(jarpaths[4]): jar_path = jarpaths[4]
    else:                             jar_path = jarpaths[5]
    try:
      cld = self._connect()
    except:
      # try to start local jar or re-raise previous exception
      if not start_h2o: raise ValueError("Cannot connect to H2O server. Please check that H2O is running at {}".format(H2OConnection.make_url("")))
      print()
      print()
      print("No instance found at ip and port: " + ip + ":" + str(port) + ". Trying to start local jar...")
      print()
      print()
      path_to_jar = os.path.exists(jar_path)
      if path_to_jar:
        if not ice_root:
          ice_root = tempfile.mkdtemp()
        cld = self._start_local_h2o_jar(max_mem_size, min_mem_size, enable_assertions, license, ice_root, jar_path, nthreads)
      else:
        print("No jar file found. Could not start local instance.")
        print("Jar Paths searched: ")
        for jp in jarpaths:
          print("\t" + jp)
        print()
        raise
    __H2OCONN__._cld = cld

    if strict_version_check and os.environ.get('H2O_DISABLE_STRICT_VERSION_CHECK') is None:
      ver_h2o = cld['version']
      from .__init__ import __version__
      ver_pkg = "UNKNOWN" if __version__ == "SUBST_PROJECT_VERSION" else __version__
      if ver_h2o != ver_pkg:
        try:
          branch_name_h2o = cld['branch_name']
        except KeyError:
          branch_name_h2o = None
        else:
          branch_name_h2o = cld['branch_name']

        try:
          build_number_h2o = cld['build_number']
        except KeyError:
          build_number_h2o = None
        else:
          build_number_h2o = cld['build_number']

        if build_number_h2o is None:
          raise EnvironmentError("Version mismatch. H2O is version {0}, but the h2o-python package is version {1}. "
                                 "Upgrade H2O and h2o-Python to latest stable version - "
                                 "http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html"
                                 "".format(ver_h2o, str(ver_pkg)))
        elif build_number_h2o == 'unknown':
          raise EnvironmentError("Version mismatch. H2O is version {0}, but the h2o-python package is version {1}. "
                                 "Upgrade H2O and h2o-Python to latest stable version - "
                                 "http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html"
                                 "".format(ver_h2o, str(ver_pkg)))
        elif build_number_h2o == '99999':
          raise EnvironmentError("Version mismatch. H2O is version {0}, but the h2o-python package is version {1}. "
                                 "This is a developer build, please contact your developer."
                                 "".format(ver_h2o, str(ver_pkg)))
        else:
          raise EnvironmentError("Version mismatch. H2O is version {0}, but the h2o-python package is version {1}. "
                                 "Install the matching h2o-Python version from - "
                                 "http://h2o-release.s3.amazonaws.com/h2o/{2}/{3}/index.html."
                                 "".format(ver_h2o, str(ver_pkg),branch_name_h2o, build_number_h2o))

    self._session_id = H2OConnection.get_json(url_suffix="InitID")["session_key"]
    H2OConnection._cluster_info()

  @staticmethod
  def default():
    H2OConnection.__ENCODING__ = "utf-8"
    H2OConnection.__ENCODING_ERROR__ = "replace"

  @staticmethod
  def jar_paths():
    sys_prefix1 = sys_prefix2 = sys.prefix
    if sys_prefix1.startswith('/Library'): sys_prefix2 = '/System'+sys_prefix1
    elif sys_prefix1.startswith('/System'): sys_prefix2 = sys_prefix1.split('/System')[1]
    return [os.path.join(sys_prefix1, "h2o_jar", "h2o.jar"),
            os.path.join(os.path.sep,"usr","local","h2o_jar","h2o.jar"),
            os.path.join(sys_prefix1, "local", "h2o_jar", "h2o.jar"),
            os.path.join(site.USER_BASE, "h2o_jar", "h2o.jar"),
            os.path.join(sys_prefix2, "h2o_jar", "h2o.jar"),
            os.path.join(sys_prefix2, "h2o_jar", "h2o.jar"),
           ]

  @staticmethod
  def _cluster_info():
    global __H2OCONN__
    cld = __H2OCONN__._cld
    ncpus = sum([n['num_cpus'] for n in cld['nodes']])
    allowed_cpus = sum([n['cpus_allowed'] for n in cld['nodes']])
    mfree = sum([n['free_mem'] for n in cld['nodes']])
    cluster_health = all([n['healthy'] for n in cld['nodes']])
    ip = "127.0.0.1" if __H2OCONN__._ip=="localhost" else __H2OCONN__._ip
    cluster_info = [
      ["H2O cluster uptime: ", get_human_readable_time(cld["cloud_uptime_millis"])],
      ["H2O cluster version: ", cld["version"]],
      ["H2O cluster name: ", cld["cloud_name"]],
      ["H2O cluster total nodes: ", cld["cloud_size"]],
      ["H2O cluster total free memory: ", get_human_readable_size(mfree)],
      ["H2O cluster total cores: ", str(ncpus)],
      ["H2O cluster allowed cores: ", str(allowed_cpus)],
      ["H2O cluster healthy: ", str(cluster_health)],
      ["H2O Connection ip: ", ip],
      ["H2O Connection port: ", __H2OCONN__._port],
      ["H2O Connection proxy: ", __H2OCONN__._proxy],
      ["Python Version: ", sys.version.split()[0]],
      ]
    __H2OCONN__._cld = H2OConnection.get_json(url_suffix="Cloud")   # update the cached version of cld
    H2ODisplay(cluster_info)

  def _connect(self, size=1, max_retries=5, print_dots=False):
    """
    Does not actually "connect", instead simply tests that the cluster can be reached,
    is of a certain size, and is taking basic status commands.
    
    :param size: The number of H2O instances in the cloud.
    :return: The JSON response from a "stable" cluster.
    """
    retries = 0

    while True:
      retries += 1
      if print_dots:
        self._print_dots(retries)
      try:
        cld = H2OConnection.get_json(url_suffix="Cloud")
        if not cld['cloud_healthy']:
          raise ValueError("Cluster reports unhealthy status", cld)
        if cld['cloud_size'] >= size and cld['consensus']:
          if print_dots: print(" Connection successful!")
          return cld
      except EnvironmentError:
        pass
      # Cloud too small or voting in progress; sleep; try again
      time.sleep(0.1)
      if retries > max_retries:
        raise EnvironmentError("Max retries exceeded. Could not establish link to the H2O cloud @ " + str(self._ip) + ":" + str(self._port))

  def _print_dots(self, retries):
    sys.stdout.write("\rStarting H2O JVM and connecting: {}".format("." * retries))
    sys.stdout.flush()

  def _start_local_h2o_jar(self, mmax, mmin, ea, license, ice, jar_path, nthreads):
    command = H2OConnection._check_java()
    if license:
      if not os.path.exists(license):
        raise ValueError("License file not found (" + license + ")")

    if not ice:
      raise ValueError("`ice_root` must be specified")

    stdout = open(H2OConnection._tmp_file("stdout"), 'w')
    stderr = open(H2OConnection._tmp_file("stderr"), 'w')

    print("Using ice_root: " + ice)
    print()

    jver = subprocess.check_output([command, "-version"], stderr=subprocess.STDOUT)
    if PY3: jver = str(jver, H2OConnection.__ENCODING__)

    print()
    print("Java Version: " + jver)
    print()

    if "GNU libgcj" in jver:
      raise ValueError("Sorry, GNU Java is not supported for H2O.\n"+
                       "Please download the latest Java SE JDK 7 from the following URL:\n"+
                       "http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html")

    if "Client VM" in jver:
      print("WARNING: ")
      print("You have a 32-bit version of Java. H2O works best with 64-bit Java.")
      print("Please download the latest Java SE JDK 7 from the following URL:")
      print("http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html")
      print()

    vm_opts = []
    if mmin:
      if type(mmin) == int:
        warnings.warn("User specified min_mem_size should have a trailing letter indicating byte type.\n"
                      "`m` or `M` indicate megabytes & `g` or `G` indicate gigabytes.\nWill default to gigabytes as byte type.")
        vm_opts += ["-Xms{}g".format(mmin)]
      else:
        vm_opts += ["-Xms{}".format(mmin)]

    if mmax:
      if type(mmax) == int:
        warnings.warn("User specified max_mem_size should have a trailing letter indicating byte type.\n"
                      "`m` or `M` indicate megabytes & `g` or `G` indicate gigabytes.\nWill default to gigabytes as byte type.")
        vm_opts += ["-Xmx{}g".format(mmax)]
      else:
        vm_opts += ["-Xmx{}".format(mmax)]

    if ea:   vm_opts += ["-ea"]

    h2o_opts = ["-verbose:gc",
                "-XX:+PrintGCDetails",
                "-XX:+PrintGCTimeStamps",
                "-jar", jar_path,
                "-name", "H2O_started_from_python_" 
                + re.sub("[^A-Za-z0-9]", "_", 
                         (os.getenv("USERNAME") if sys.platform == "win32" else os.getenv("USER")) or "unknownUser")
                + "_" + "".join([choice(ascii_lowercase) for _ in range(3)] + [choice(digits) for _ in range(3)]),
                "-ip", "127.0.0.1",
                "-port", "54321",
                "-ice_root", ice,
                ]
    
    if nthreads > 0: h2o_opts +=  ["-nthreads", str(nthreads)]
    if license: h2o_opts += ["-license", license]

    cmd = [command] + vm_opts + h2o_opts

    cwd = os.path.abspath(os.getcwd())
    if sys.platform == "win32":
      self._child = subprocess.Popen(args=cmd,stdout=stdout,stderr=stderr,cwd=cwd,creationflags=subprocess.CREATE_NEW_PROCESS_GROUP)
    else:
      self._child = subprocess.Popen(args=cmd, stdout=stdout, stderr=stderr, cwd=cwd, preexec_fn=os.setsid)
    cld = self._connect(1, 30, True)
    return cld

  @staticmethod
  def _check_java():
    # *WARNING* some over-engineering follows... :{

    # is java in PATH?
    if H2OConnection._pwhich("java"):
      return H2OConnection._pwhich("java")

    # check if JAVA_HOME is set (for windoz)
    if os.getenv("JAVA_HOME"):
      return os.path.join(os.getenv("JAVA_HOME"), "bin", "java.exe")

    # check /Program Files/ and /Program Files (x86)/ if os is windoz
    if sys.platform == "win32":
      program_folder = os.path.join("C:", "{}", "Java")
      program_folders = [program_folder.format("Program Files"),
                         program_folder.format("Program Files (x86)")]

      # check both possible program files...
      for folder in program_folders:

        # hunt down the jdk directory
        possible_jdk_dir = [d for d in folder if 'jdk' in d]

        # if got a non-empty list of jdk directory candidates
        if len(possible_jdk_dir) != 0:

          # loop over and check if the java.exe exists
          for jdk in possible_jdk_dir:
            path = os.path.join(folder, jdk, "bin", "java.exe")
            if os.path.exists(path):
              return path

      # check for JRE and warn
      for folder in program_folders:
        path = os.path.join(folder, "jre7", "bin", "java.exe")
        if os.path.exists(path):
          raise ValueError("Found JRE at " + path + "; but H2O requires the JDK to run.")

    else:
      raise ValueError("Cannot find Java. Please install the latest JDK from\n"
        +"http://www.oracle.com/technetwork/java/javase/downloads/index.html" )

  @staticmethod
  def _pwhich(e):
    """
    POSIX style which
    """
    ok = os.X_OK
    if e:
      if os.access(e, ok):
        return e
      for path in os.getenv('PATH').split(os.pathsep):
        full_path = os.path.join(path, e)
        if os.access(full_path, ok):
          return full_path
    return None

  @staticmethod
  def _tmp_file(type):
    usr = re.sub("[^A-Za-z0-9]", "_", (os.getenv("USERNAME") if sys.platform == "win32" else os.getenv("USER")) or "unknownUser")
    if type == "stdout":
      path = os.path.join(tempfile.mkdtemp(), "h2o_{}_started_from_python.out".format(usr))
      print("JVM stdout: " + path)
      return path
    if type == "stderr":
      path = os.path.join(tempfile.mkdtemp(), "h2o_{}_started_from_python.err".format(usr))
      print("JVM stderr: " + path)
      return path
    if type == "pid":
      return os.path.join(tempfile.mkdtemp(), "h2o_{}_started_from_python.pid".format(usr))

    raise ValueError("Unkown type in H2OConnection._tmp_file call: " + type)

  @staticmethod
  def _shutdown(conn, prompt):
    """
    Shut down the specified instance. All data will be lost.
    This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O
    instance.
    :param conn: An H2OConnection object containing the IP address and port of the server running H2O.
    :param prompt: A logical value indicating whether to prompt the user before shutting down the H2O server.
    :return: None
    """
    global __H2OCONN__
    if conn is None: raise ValueError("There is no H2O instance running.")
    try:
      if not conn.cluster_is_up(conn):  raise ValueError("There is no H2O instance running at ip: {0} and port: "
                                                       "{1}".format(conn.ip(), conn.port()))
    except:
      #H2O is already shutdown on the java side
      ip = conn.ip()
      port = conn.port()
      __H2OCONN__= None
      raise ValueError("The H2O instance running at {0}:{1} has already been shutdown.".format(ip, port))
    if not isinstance(prompt, bool): raise ValueError("`prompt` must be TRUE or FALSE")
    if prompt:
      question = "Are you sure you want to shutdown the H2O instance running at {0}:{1} (Y/N)? ".format(conn.ip(), conn.port())
      response = input(question) if PY3 else raw_input(question)
    else: response = "Y"
    if response == "Y" or response == "y": 
      conn.post(url_suffix="Shutdown")
      __H2OCONN__ = None #so that the "Did you run `h2o.init()`" ValueError is triggered

  @staticmethod
  def rest_version(): return __H2OCONN__._rest_version

  @staticmethod
  def session_id(): return __H2OCONN__._session_id

  @staticmethod
  def port(): return __H2OCONN__._port

  @staticmethod
  def ip(): return __H2OCONN__._ip
  
  @staticmethod
  def https(): return  __H2OCONN__._https
  
  @staticmethod
  def username(): return __H2OCONN__._username
  
  @staticmethod
  def password(): return __H2OCONN__._password
  
  @staticmethod
  def insecure(): return __H2OCONN__._insecure

  @staticmethod
  def current_connection(): return __H2OCONN__

  @staticmethod
  def check_conn():
    if not __H2OCONN__:
      raise EnvironmentError("No active connection to an H2O cluster.  Try calling `h2o.init()`")
    return __H2OCONN__

  @staticmethod
  def cluster_is_up(conn):
    """
    Determine if an H2O cluster is up or not
    :param conn: An H2OConnection object containing the IP address and port of the server running H2O.
    :return: TRUE if the cluster is up; FALSE otherwise
    """
    if not isinstance(conn, H2OConnection): raise ValueError("`conn` must be an H2OConnection object")
    rv = conn.current_connection()._attempt_rest(url=("https" if conn.https() else "http") +"://{0}:{1}/".format(conn.ip(), conn.port()), method="GET",
                                                 post_body="", file_upload_info="")
    if rv.status_code == 401: warnings.warn("401 Unauthorized Access. Did you forget to provide a username and password?") 
    return rv.status_code == 200 or rv.status_code == 301

  """
  Below is the REST implementation layer:
      _attempt_rest -- GET, POST, DELETE

      _do_raw_rest

      get
      post
      get_json
      post_json

  All methods are static and rely on an active __H2OCONN__ object.
  """

  @staticmethod
  def make_url(url_suffix, _rest_version=None):
    scheme = "https" if H2OConnection.https() else "http" 
    _rest_version = _rest_version or H2OConnection.rest_version()
    return "{}://{}:{}/{}/{}".format(scheme,H2OConnection.ip(),H2OConnection.port(),_rest_version,url_suffix)

  @staticmethod
  def get(url_suffix, **kwargs):
    if __H2OCONN__ is None:
      raise ValueError("No h2o connection. Did you run `h2o.init()` ?")
    return __H2OCONN__._do_raw_rest(url_suffix, "GET", None, **kwargs)

  @staticmethod
  def post(url_suffix, file_upload_info=None, **kwargs):
    if __H2OCONN__ is None:
      raise ValueError("No h2o connection. Did you run `h2o.init()` ?")
    return __H2OCONN__._do_raw_rest(url_suffix, "POST", file_upload_info, **kwargs)

  @staticmethod
  def delete(url_suffix, **kwargs):
    if __H2OCONN__ is None:
      raise ValueError("No h2o connection. Did you run `h2o.init()` ?")
    return __H2OCONN__._do_raw_rest(url_suffix, "DELETE", None, **kwargs)

  @staticmethod
  def get_json(url_suffix, **kwargs):
    if __H2OCONN__ is None:
      raise ValueError("No h2o connection. Did you run `h2o.init()` ?")
    return __H2OCONN__._rest_json(url_suffix, "GET", None, **kwargs)

  @staticmethod
  def post_json(url_suffix, file_upload_info=None, **kwargs):
    if __H2OCONN__ is None:
      raise ValueError("No h2o connection. Did you run `h2o.init()` ?")
    return __H2OCONN__._rest_json(url_suffix, "POST", file_upload_info, **kwargs)

  def _rest_json(self, url_suffix, method, file_upload_info, **kwargs):
    raw_txt = self._do_raw_rest(url_suffix, method, file_upload_info, **kwargs)
    return self._process_tables(raw_txt.json())

  # Massage arguments into place, call _attempt_rest
  def _do_raw_rest(self, url_suffix, method, file_upload_info, **kwargs):
    if not url_suffix:
      raise ValueError("No url suffix supplied.")

    # allow override of REST version, currently used for Rapids which is /99
    if '_rest_version' in kwargs:
      _rest_version = kwargs['_rest_version']
      del kwargs['_rest_version']
    else:
      _rest_version = self._rest_version

    url = H2OConnection.make_url(url_suffix,_rest_version)
    
    query_string = ""
    for k,v in iteritems(kwargs):
      if v is None: continue #don't send args set to None so backend defaults take precedence
      if isinstance(v, list):
        x = '['
        for l in v:
          if isinstance(l,list):
            x += '['
            x += ','.join([str(e) if PY3 else str(e).encode(H2OConnection.__ENCODING__, errors=H2OConnection.__ENCODING_ERROR__) for e in l])
            x += ']'
          else:
            x += str(l) if PY3 else str(l).encode(H2OConnection.__ENCODING__, errors=H2OConnection.__ENCODING_ERROR__)
          x += ','
        x = x[:-1]
        x += ']'
      elif isinstance(v, dict) and "__meta" in v and v["__meta"]["schema_name"].endswith("KeyV3"):
        x = v["name"]
      else:
        x = str(v) if PY3 else str(v).encode(H2OConnection.__ENCODING__, errors=H2OConnection.__ENCODING_ERROR__)
      query_string += k+"="+quote(x)+"&"
    query_string = query_string[:-1]  # Remove trailing extra &

    post_body = ""
    if not file_upload_info:
      if method == "POST":
        post_body = query_string
      elif query_string != '':
        url = "{}?{}".format(url, query_string)
    else:
      if not method == "POST":
        raise ValueError("Received file upload info and expected method to be POST. Got: " + str(method))
      if query_string != '':
        url = "{}?{}".format(url, query_string)

    if _is_logging():
      _log_rest("------------------------------------------------------------\n")
      _log_rest("\n")
      _log_rest("Time:     {0}\n".format(time.strftime('Y-%m-%d %H:%M:%OS3')))
      _log_rest("\n")
      _log_rest("{0} {1}\n".format(method, url))
      _log_rest("postBody: {0}\n".format(post_body))

    global _rest_ctr; _rest_ctr = _rest_ctr+1
    begin_time_seconds = time.time()
    http_result = self._attempt_rest(url, method, post_body, file_upload_info)
    end_time_seconds = time.time()
    elapsed_time_seconds = end_time_seconds - begin_time_seconds
    elapsed_time_millis = elapsed_time_seconds * 1000
    if not http_result.ok:
      detailed_error_msgs = []
      try:
        result = http_result.json()
        if 'messages' in result.keys():
          detailed_error_msgs = '\n'.join([m['message'] for m in result['messages'] if m['message_type'] in ['ERRR']])
        elif 'exception_msg' in result.keys():
          detailed_error_msgs = result['exception_msg']
      except ValueError:
        pass
      raise EnvironmentError(("h2o-py got an unexpected HTTP status code:\n {} {} (method = {}; url = {}). \n"+ \
                              "detailed error messages: {}")
                              .format(http_result.status_code,http_result.reason,method,url,detailed_error_msgs))


    if _is_logging():
      _log_rest("\n")
      _log_rest("httpStatusCode:    {0}\n".format(http_result.status_code))
      _log_rest("httpStatusMessage: {0}\n".format(http_result.reason))
      _log_rest("millis:            {0}\n".format(elapsed_time_millis))
      _log_rest("\n")
      _log_rest("{0}\n".format(http_result.json()))
      _log_rest("\n")


    return http_result

  # Low level request call
  def _attempt_rest(self, url, method, post_body, file_upload_info):
    
    auth = (self._username, self._password)
    verify = not self._insecure
    cluster = self._cluster_name
    headers = {'User-Agent': 'H2O Python client/'+sys.version.replace('\n',''), 'X-Cluster': cluster}
    try:
      if method == "GET":
        return requests.get(url, headers=headers, proxies=self._proxy, auth=auth, verify=verify)
      elif file_upload_info:
        files = {file_upload_info["file"] : open(file_upload_info["file"], "rb")}
        return requests.post(url, files=files, headers=headers, proxies=self._proxy, auth=auth, verify=verify)
      elif method == "POST":
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        return requests.post(url, data=post_body, headers=headers, proxies=self._proxy, auth=auth, verify=verify)
      elif method == "DELETE":
        return requests.delete(url, headers=headers, proxies=self._proxy, auth=auth, verify=verify)
      else:
        raise ValueError("Unknown HTTP method " + method)

    except requests.ConnectionError as e:
      raise EnvironmentError("h2o-py encountered an unexpected HTTP error:\n {}".format(e))

  # TODO:
  # @staticmethod
  # def _process_matrices(x=None):
  #     if x:
  #         if isinstance(x, "dict"):
  #
  #     return x

  @staticmethod
  def _process_tables(x=None):
    if x:
      if isinstance(x, dict):

        has_meta = "__meta" in x
        has_schema_type = has_meta and "schema_type" in x["__meta"]

        have_table = has_schema_type and x["__meta"]["schema_type"] == "TwoDimTable"
        if have_table:
          col_formats = [c["format"] for c in x["columns"]]
          table_header = x["name"]
          table_descr  = x["description"]
          col_types = [c["type"] for c in x["columns"]]
          col_headers = [c["name"] for c in x["columns"]]
          row_headers = ["" for i in range(len(col_headers))]
          cell_values = x["data"]
          tbl = H2OTwoDimTable(row_header=row_headers, col_header=col_headers,
                               col_types=col_types, table_header=table_header,
                               raw_cell_values=cell_values,
                               col_formats=col_formats,table_description=table_descr)
          x = tbl
        else:
          for k in x:
            x[k] = H2OConnection._process_tables(x[k])
      if isinstance(x, list):
        for it in range(len(x)):
          x[it] = H2OConnection._process_tables(x[it])
    return x

  global _rest_ctr
  _rest_ctr = 0
  @staticmethod
  def rest_ctr(): global _rest_ctr; return _rest_ctr

# On exit, close the session to allow H2O to cleanup any temps
def end_session():
  try:
    H2OConnection.delete(url_suffix="InitID")
    print("Sucessfully closed the H2O Session.")
  except:
    pass

def get_human_readable_size(num):
  exp_str = [(0, 'B'), (10, 'KB'), (20, 'MB'), (30, 'GB'), (40, 'TB'), (50, 'PB'), ]
  i = 0
  rounded_val = 0
  while i + 1 < len(exp_str) and num >= (2 ** exp_str[i + 1][0]):
    i += 1
    rounded_val = round(float(num) / 2 ** exp_str[i][0], 2)
  return '%s %s' % (rounded_val, exp_str[i][1])


def get_human_readable_time(epochTimeMillis):
  days = epochTimeMillis/(24*60*60*1000.0)
  hours = (days-math.floor(days))*24
  minutes = (hours-math.floor(hours))*60
  seconds = (minutes-math.floor(minutes))*60
  milliseconds = (seconds-math.floor(seconds))*1000
  duration_vec = [int(math.floor(t)) for t in [days,hours,minutes,seconds,milliseconds]]
  names_duration_vec = ["days","hours","minutes","seconds","milliseconds"]

  duration_dict = dict(zip(names_duration_vec, duration_vec))

  readable_time = ""
  for name in names_duration_vec:
    if duration_dict[name] > 0:
      readable_time += str(duration_dict[name]) + " " + name + " "

  return readable_time

def is_int(possible_int):
  try:
    int(possible_int)
    return True
  except ValueError:
    return False

def as_int(the_int):
  if not is_int(the_int):
    raise ValueError("Not a valid int value: " + str(the_int))
  return int(the_int)

def _kill_jvm_fork():
  global __H2OCONN__
  if __H2OCONN__ is not None:
    if __H2OCONN__._child:
      __H2OCONN__._child.kill()
      print("Successfully stopped H2O JVM started by the h2o python module.")

atexit.register(_kill_jvm_fork)
atexit.register(end_session)
