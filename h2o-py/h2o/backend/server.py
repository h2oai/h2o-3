# -*- encoding: utf-8 -*-
"""
Local server.

`H2OLocalServer` allows to start H2O servers on your local machine:
    hs = H2OLocalServer.start() : start a new local server
    hs.is_running() : check if the server is running
    hs.shutdown() : shut down the server

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

import atexit
import os
import subprocess
import sys
import tempfile
import time
import re
from random import choice
from sysconfig import get_config_var
from warnings import warn

from h2o.exceptions import H2OServerError, H2OStartupError, H2OValueError
from h2o.utils.typechecks import assert_is_type, assert_satisfies, BoundInt, I, is_type

__all__ = ("H2OLocalServer", )


class H2OLocalServer(object):
    """
    Handle to an H2O server launched locally.

    Public interface::

        hs = H2OLocalServer.start(...)  # launch a new local H2O server
        hs.is_running()                 # check if the server is running
        hs.shutdown()                   # shut down the server
        hs.scheme                       # either "http" or "https"
        hs.ip                           # ip address of the server, typically "127.0.0.1"
        hs.port                         # port on which the server is listening

    Once started, the server will run until the script terminates, or until you call ``.shutdown()`` on it. Moreover,
    if the server terminates with an exception, then the server will not stop and will continue to run even after
    Python process exits. This runaway process may end up being in a bad shape (e.g. frozen), then the only way to
    terminate it is to end the Java process from the terminal.

    Alternatively, it is possible to start the server as a context manager, in which case it will be automatically
    shut down even if an exception occurs in Python (but not if the Python process is killed)::

        with H2OLocalServer.start() as hs:
            # do something with the server -- probably connect to it
    """

    # (Avkash) Changing the maximum time to wait as 60 seconds to match with the same time in to R API. ##
    _TIME_TO_START = 60  # Maximum time we wait for the server to start up (in seconds)
    _TIME_TO_KILL = 3    # Maximum time we wait for the server to shut down until we kill it (in seconds)

    _BAD_JAVA_VERSION_RETURN_CODE_ = 3

    @staticmethod
    def start(jar_path=None, nthreads=-1, enable_assertions=True, max_mem_size=None, min_mem_size=None,
              ice_root=None, log_dir=None, log_level=None, max_log_file_size=None, port="54321+", name=None, extra_classpath=None,
              verbose=True, jvm_custom_args=None, bind_to_localhost=True):
        """
        Start new H2O server on the local machine.

        :param jar_path: Path to the h2o.jar executable. If not given, then we will search for h2o.jar in the
            locations returned by ``._jar_paths()``.
        :param nthreads: Number of threads in the thread pool. This should be related to the number of CPUs used.
            -1 means use all CPUs on the host. A positive integer specifies the number of CPUs directly.
        :param enable_assertions: If True, pass ``-ea`` option to the JVM.
        :param max_mem_size: Maximum heap size (jvm option Xmx), in bytes.
        :param min_mem_size: Minimum heap size (jvm option Xms), in bytes.
        :param log_dir: Directory for H2O logs to be stored if a new instance is started. Default directory is determined
            by H2O internally.
        :param log_level: The logger level for H2O if a new instance is started.
        :param max_log_file_size: Maximum size of INFO and DEBUG log files. The file is rolled over after a specified 
            size has been reached. (The default is 3MB. Minimum is 1MB and maximum is 99999MB)
        :param ice_root: A directory where H2O stores its temporary files. Default location is determined by
            ``tempfile.mkdtemp()``.
        :param port: Port where to start the new server. This could be either an integer, or a string of the form
            "DDDDD+", indicating that the server should start looking for an open port starting from DDDDD and up.
        :param name: name of the h2o cluster to be started.
        :param extra_classpath: List of paths to libraries that should be included on the Java classpath.
        :param verbose: If True, then connection info will be printed to the stdout.
        :param jvm_custom_args: Custom, user-defined arguments for the JVM H2O is instantiated in.
        :param bind_to_localhost: A flag indicating whether access to the H2O instance should be restricted to the local
            machine (default) or if it can be reached from other computers on the network.
            Only applicable when H2O is started from the Python client.

        :returns: a new H2OLocalServer instance.
        """
        assert_is_type(jar_path, None, str)
        assert_is_type(port, None, int, str)
        assert_is_type(name, None, str)
        assert_is_type(nthreads, -1, BoundInt(1, 4096))
        assert_is_type(enable_assertions, bool)
        assert_is_type(min_mem_size, None, int)
        assert_is_type(max_mem_size, None, BoundInt(1 << 25))
        assert_is_type(log_dir, str, None)
        assert_is_type(log_level, str, None)
        assert_satisfies(log_level, log_level in [None, "TRACE", "DEBUG", "INFO", "WARN", "ERRR", "FATA"])
        assert_is_type(max_log_file_size, str, None)
        assert_is_type(ice_root, None, I(str, os.path.isdir))
        assert_is_type(extra_classpath, None, [str])
        assert_is_type(jvm_custom_args, list, None)
        assert_is_type(bind_to_localhost, bool)
        if jar_path:
            assert_satisfies(jar_path, jar_path.endswith("h2o.jar"))

        if min_mem_size is not None and max_mem_size is not None and min_mem_size > max_mem_size:
            raise H2OValueError("`min_mem_size`=%d is larger than the `max_mem_size`=%d" % (min_mem_size, max_mem_size))
        if port is None: port = "54321+"
        baseport = None
        # TODO: get rid of this port gimmick and have 2 separate parameters.
        if is_type(port, str):
            if port.isdigit():
                port = int(port)
            else:
                if not(port[-1] == "+" and port[:-1].isdigit()):
                    raise H2OValueError("`port` should be of the form 'DDDD+', where D is a digit. Got: %s" % port)
                baseport = int(port[:-1])
                port = 0

        hs = H2OLocalServer()
        hs._verbose = bool(verbose)
        hs._jar_path = hs._find_jar(jar_path)
        hs._extra_classpath = extra_classpath
        hs._ice_root = ice_root
        hs._name = name
        if not ice_root:
            hs._ice_root = tempfile.mkdtemp()
            hs._tempdir = hs._ice_root

        if verbose: print("Attempting to start a local H2O server...")
        hs._launch_server(port=port, baseport=baseport, nthreads=int(nthreads), ea=enable_assertions,
                          mmax=max_mem_size, mmin=min_mem_size, jvm_custom_args=jvm_custom_args,
                          bind_to_localhost=bind_to_localhost, log_dir=log_dir, log_level=log_level, max_log_file_size=max_log_file_size)
        if verbose: print("  Server is running at %s://%s:%d" % (hs.scheme, hs.ip, hs.port))
        atexit.register(lambda: hs.shutdown())
        return hs

    def is_running(self):
        """Return True if the server process is still running, False otherwise."""
        return self._process is not None and self._process.poll() is None

    def shutdown(self):
        """
        Shut down the server by trying to terminate/kill its process.

        First we attempt to terminate the server process gracefully (sending SIGTERM signal). However after
        _TIME_TO_KILL seconds if the process didn't shutdown, we forcefully kill it with a SIGKILL signal.
        """
        if not self._process: return
        try:
            kill_time = time.time() + self._TIME_TO_KILL
            while self._process.poll() is None and time.time() < kill_time:
                self._process.terminate()
                time.sleep(0.2)
            if self._process().poll() is None:
                self._process.kill()
                time.sleep(0.2)
            if self._verbose:
                print("Local H2O server %s:%s stopped." % (self.ip, self.port))
        except:
            pass
        self._process = None

    @property
    def scheme(self):
        """Connection scheme, 'http' or 'https'."""
        return self._scheme

    @property
    def ip(self):
        """IP address of the server."""
        return self._ip

    @property
    def port(self):
        """Port that the server is listening to."""
        return self._port

    @property
    def name(self):
        """H2O cluster name."""
        return self._name

    # ------------------------------------------------------------------------------------------------------------------
    # Private
    # ------------------------------------------------------------------------------------------------------------------

    def __init__(self):
        """[Internal] please use H2OLocalServer.start() to launch a new server."""
        self._scheme = None   # "http" or "https"
        self._ip = None
        self._port = None
        self._name = None
        self._process = None
        self._verbose = None
        self._jar_path = None
        self._extra_classpath = None
        self._ice_root = None
        self._stdout = None
        self._stderr = None
        self._tempdir = None

    def _find_jar(self, path0=None):
        """
        Return the location of an h2o.jar executable.

        :param path0: Explicitly given h2o.jar path. If provided, then we will simply check whether the file is there,
            otherwise we will search for an executable in locations returned by ._jar_paths().

        :raises H2OStartupError: if no h2o.jar executable can be found.
        """
        jar_paths = [path0] if path0 else self._jar_paths()
        searched_paths = []
        for jp in jar_paths:
            searched_paths.append(jp)
            if os.path.exists(jp):
                return jp
        raise H2OStartupError("Cannot start local server: h2o.jar not found. Paths searched:\n" +
                              "".join("    %s\n" % s for s in searched_paths))

    @staticmethod
    def _jar_paths():
        """Produce potential paths for an h2o.jar executable."""

        # PUBDEV-3534 hook to use arbitrary h2o.jar
        own_jar = os.getenv("H2O_JAR_PATH", "")
        if own_jar != "":
            if not os.path.isfile(own_jar):
                raise H2OStartupError("Environment variable H2O_JAR_PATH is set to '%d' but file does not exists, "
                                      "unset environment variable or provide valid path to h2o.jar file." % own_jar)
            yield own_jar

        # Check if running from an h2o-3 src folder (or any subfolder), in which case use the freshly-built h2o.jar
        cwd_chunks = os.path.abspath(".").split(os.path.sep)
        for i in range(len(cwd_chunks), 0, -1):
            if cwd_chunks[i - 1] == "h2o-3":
                yield os.path.sep.join(cwd_chunks[:i] + ["build", "h2o.jar"])
        # Then check the backend/bin folder:
        # (the following works assuming this code is located in h2o/backend/server.py file)
        backend_dir = os.path.split(os.path.realpath(__file__))[0]
        yield os.path.join(backend_dir, "bin", "h2o.jar")

        # Then try several old locations where h2o.jar might have been installed
        prefix1 = prefix2 = sys.prefix
        # On Unix-like systems Python typically gets installed into /Library/... or /System/Library/... If one of
        # those paths is sys.prefix, then we also build its counterpart.
        if prefix1.startswith(os.path.sep + "Library"):
            prefix2 = os.path.join("", "System", prefix1)
        elif prefix1.startswith(os.path.sep + "System"):
            prefix2 = prefix1[len(os.path.join("", "System")):]
        yield os.path.join(prefix1, "h2o_jar", "h2o.jar")
        yield os.path.join(os.path.abspath(os.sep), "usr", "local", "h2o_jar", "h2o.jar")
        yield os.path.join(prefix1, "local", "h2o_jar", "h2o.jar")
        yield os.path.join(get_config_var("userbase"), "h2o_jar", "h2o.jar")
        yield os.path.join(prefix2, "h2o_jar", "h2o.jar")

    def _launch_server(self, port, baseport, mmax, mmin, ea, nthreads, jvm_custom_args, bind_to_localhost, 
                       log_dir=None, log_level=None, max_log_file_size=None):
        """Actually start the h2o.jar executable (helper method for `.start()`)."""
        self._ip = "127.0.0.1"

        # Find Java and check version. (Note that subprocess.check_output returns the output as a bytes object)
        java = self._find_java()
        self._check_java(java, self._verbose)

        if self._verbose:
            print("  Starting server from " + self._jar_path)
            print("  Ice root: " + self._ice_root)

        # Construct java command to launch the process
        cmd = [java]

        # ...add JVM options
        cmd += ["-ea"] if ea else []
        for (mq, num) in [("-Xms", mmin), ("-Xmx", mmax)]:
            if num is None: continue
            numstr = "%dG" % (num >> 30) if num == (num >> 30) << 30 else \
                     "%dM" % (num >> 20) if num == (num >> 20) << 20 else \
                     str(num)
            cmd += [mq + numstr]
        if jvm_custom_args is not None:
            for arg in jvm_custom_args:
                assert type(arg) is str
                cmd += [arg]

        # This should be the last JVM option
        if self._extra_classpath is None:
            # Use jar file directly
            cmd += ["-jar", self._jar_path]
        else:
            # Combine jar path with the optional extra classpath
            classpath = [self._jar_path] + self._extra_classpath
            cmd += ["-cp", os.pathsep.join(classpath), "water.H2OApp"]

        # ...add H2O options
        cmd += ["-ip", self._ip]
        if bind_to_localhost:
            cmd += ["-web_ip", self._ip]
        cmd += ["-port", str(port)] if port else []
        cmd += ["-baseport", str(baseport)] if baseport else []
        cmd += ["-ice_root", self._ice_root]
        cmd += ["-nthreads", str(nthreads)] if nthreads > 0 else []

        if log_dir:
            cmd += ["-log_dir", log_dir]

        if log_level:
            cmd += ["-log_level", log_level]
            
        if max_log_file_size:
            cmd += ["-max_log_file_size", max_log_file_size]

        if not self._name:
            self._name = "H2O_from_python_%s" % self._tmp_file("salt")
        cmd += ["-name", self._name]

        # Warning: do not change to any higher log-level, otherwise we won't be able to know which port the
        # server is listening to.
        cmd += ["-log_level", "INFO"]
        cmd += ["-allow_unsupported_java"]

        # Create stdout and stderr files
        self._stdout = self._tmp_file("stdout")
        self._stderr = self._tmp_file("stderr")
        cwd = os.path.abspath(os.getcwd())
        out = open(self._stdout, "w", encoding='utf-8')
        err = open(self._stderr, "w", encoding='utf-8')
        if self._verbose:
            print("  JVM stdout: " + out.name)
            print("  JVM stderr: " + err.name)

        # Launch the process
        win32 = sys.platform == "win32"
        flags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) if win32 else 0
        prex = os.setsid if not win32 else None
        try:
            proc = subprocess.Popen(args=cmd, stdout=out, stderr=err, cwd=cwd, creationflags=flags, preexec_fn=prex)
        except OSError as e:
            traceback = getattr(e, "child_traceback", None)
            raise H2OServerError("Unable to start server: %s" % e, traceback)

        # Wait until the server is up-and-running
        giveup_time = time.time() + self._TIME_TO_START
        while True:
            if proc.poll() is not None:
                if proc.returncode == self._BAD_JAVA_VERSION_RETURN_CODE_:
                    error_message = "Server process terminated because of unsupported Java version" 
                else:
                    error_message = "Server process terminated with error code %d" % proc.returncode 
                if os.stat(self._stderr).st_size > 0:
                    error_message += ": %s" % open(self._stderr, encoding='utf-8').read()
                else:
                    error_message += "."
                raise H2OServerError(error_message)
            ret = self._get_server_info_from_logs()
            if ret:
                self._scheme = ret[0]
                self._ip = ret[1]
                self._port = ret[2]
                self._process = proc
                break
            if time.time() > giveup_time:
                elapsed_time = time.time() - (giveup_time - self._TIME_TO_START)
                raise H2OServerError("Server wasn't able to start in %f seconds." % elapsed_time)
            time.sleep(0.2)

    @staticmethod
    def _check_java(java, verbose):
        jver_bytes = subprocess.check_output([java, "-version"], stderr=subprocess.STDOUT)
        jver = jver_bytes.decode(encoding="utf-8", errors="ignore")
        if verbose:
            print("  Java Version: " + jver.strip().replace("\n", "; "))
        if "GNU libgcj" in jver:
            raise H2OStartupError("Sorry, GNU Java is not supported for H2O.\n"
                                  "Please download the latest 64-bit Java SE JDK from Oracle.")
        if "Client VM" in jver:
            warn("  You have a 32-bit version of Java. H2O works best with 64-bit Java.\n"
                 "  Please download the latest 64-bit Java SE JDK from Oracle.\n") 
        H2OLocalServer._has_compatible_version(jver)

    @staticmethod
    def _has_compatible_version(java_version):
        pattern = re.compile("1\\.[1-7]\\.")
        if pattern.search(java_version):
            raise H2OStartupError("Your java is not supported: " + java_version.strip().replace("\n", "; "))

    @staticmethod
    def _find_java():
        """
        Find location of the java executable (helper for `._launch_server()`).

        This method is not particularly robust, and may require additional tweaking for different platforms...
        :return: Path to the java executable.
        :raises H2OStartupError: if java cannot be found.
        """
        java = "java.exe" if sys.platform == "win32" else "java"

        h2o_java = os.getenv("H2O_JAVA_HOME")
        if h2o_java:
            full_path = os.path.join(h2o_java, "bin", java)
            if not os.path.exists(full_path):
                raise H2OStartupError("Environment variable H2O_JAVA_HOME is set to '%d' "
                                      "but this location doesn't appear to be a valid Java Home directory, "
                                      "unset environment variable or provide valid path to Java Home." % h2o_java)
            return full_path

        # is java callable directly (doesn't work on windows it seems)?
        if os.access(java, os.X_OK):
            return java

        # Can Java be found on the PATH?
        for path in os.getenv("PATH").split(os.pathsep):  # not same as os.path.sep!
            full_path = os.path.join(path, java)
            if os.access(full_path, os.X_OK):
                return full_path

        # check if JAVA_HOME is set (for Windows)
        if os.getenv("JAVA_HOME"):
            full_path = os.path.join(os.getenv("JAVA_HOME"), "bin", java)
            if os.path.exists(full_path):
                return full_path

        # check "/Program Files" and "/Program Files (x86)" on Windows
        if sys.platform == "win32":
            # On Windows, backslash on the drive letter is necessary, otherwise os.path.join produces an invalid path
            program_folders = [os.path.join("C:\\", "Program Files", "Java"),
                               os.path.join("C:\\", "Program Files (x86)", "Java"),
                               os.path.join("C:\\", "ProgramData", "Oracle", "Java")]
            for folder in program_folders:
                for dirpath, dirnames, filenames in os.walk(folder):
                    if java in filenames:
                        return os.path.join(dirpath, java)

        # not found...
        raise H2OStartupError("Cannot find Java. Please install the latest JRE from\n"
                              "http://docs.h2o.ai/h2o/latest-stable/h2o-docs/welcome.html#java-requirements")

    def _tmp_file(self, kind):
        """
        Generate names for temporary files (helper method for `._launch_server()`).

        :param kind: one of "stdout", "stderr" or "salt". The "salt" kind is used for process name, not for a
            file, so it doesn't contain a path. All generated names are based on the user name of the currently
            logged-in user.
        """
        if sys.platform == "win32":
            username = os.getenv("USERNAME")
        else:
            username = os.getenv("USER")
        if not username:
            username = "unknownUser"
        usr = "".join(ch if ch.isalnum() else "_" for ch in username)

        if kind == "salt":
            return usr + "_" + "".join(choice("0123456789abcdefghijklmnopqrstuvwxyz") for _ in range(6))
        else:
            if not self._tempdir:
                self._tempdir = tempfile.mkdtemp()
            return os.path.join(self._tempdir, "h2o_%s_started_from_python.%s" % (usr, kind[3:]))

    def _get_server_info_from_logs(self):
        """
        Check server's output log, and determine its scheme / IP / port (helper method for `._launch_server()`).

        This method is polled during process startup. It looks at the server output log and checks for a presence of
        a particular string ("INFO: Open H2O Flow in your web browser:") which indicates that the server is
        up-and-running. If the method detects this string, it extracts the server's scheme, ip and port and returns
        them; otherwise it returns None.

        :returns: (scheme, ip, port) tuple if the server has already started, None otherwise.
        """
        searchstr = ": Open H2O Flow in your web browser:"
        with open(self._stdout, "rt", encoding='utf-8') as f:
            for line in f:
                if searchstr in line:
                    url = line[line.index(searchstr) + len(searchstr):].strip().rstrip("/")
                    parts = url.split(":")
                    assert len(parts) == 3 and (parts[0] == "http" or parts[1] == "https") and parts[2].isdigit(), \
                        "Unexpected URL: %s" % url
                    return parts[0], parts[1][2:], int(parts[2])
        return None

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.shutdown()
        assert len(args) == 3  # Avoid warning about unused args...
        return False  # ensure that any exception will be re-raised

    # Do not stop child process when the object is garbage collected!
    # This ensures that simple code such as
    #     for _ in range(5):
    #         h2o.H2OConnection.start()
    # will launch 5 servers, and they will not be closed down immediately (only when the program exits).
