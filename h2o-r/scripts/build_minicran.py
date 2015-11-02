#!/usr/bin/python

import os
import sys
import urllib2
import tempfile
import atexit
import shutil
import re


def download_file(url, download_path):
    print "URL: " + url

    u = urllib2.urlopen(url)
    f = open(download_path, 'wb')
    meta = u.info()
    file_size = int(meta.getheaders("Content-Length")[0])
    print "Downloading: %s (%s bytes)" % (download_path, file_size)

    file_size_dl = 0
    block_sz = 8192
    while True:
        buf = u.read(block_sz)
        if not buf:
            break

        file_size_dl += len(buf)
        f.write(buf)
#        status = r"%10d  [%3.2f%%]" % (file_size_dl, file_size_dl * 100. / file_size)
#        status = status + chr(8)*(len(status)+1)
#        print status,

    f.close()
    print "Download complete."


class CranPackage:
    def __init__(self, package_name):
        self.package_name = package_name
        self.package_dependencies = []
        self.package_version = None

    def set_version(self, package_version):
        self.package_version = package_version

    def add_dependencies(self, dependencies):
        stripped = dependencies.strip()
        arr = stripped.split(',')
        for dep in arr:
            stripped_dep = dep.strip()
            if (len(stripped_dep) == 0):
                continue
            m = re.match(r'\(.*', stripped_dep)
            if (m is not None):
                continue
            m = re.match(r'([a-zA-Z0-9\.]*)[ (]?.*', stripped_dep)
            if (m is None):
                print("")
                print("ERROR: Match failed for " + self.package_name + " (" + stripped_dep + ")")
                print("")
                sys.exit(1)
            dependency_name = m.group(1)
            self.package_dependencies.append(dependency_name)

    def get_name(self):
        return self.package_name

    def get_version(self):
        return self.package_version

    def emit(self):
        print("PACKAGE: " + self.package_name)
        for dep in self.package_dependencies:
            print("    " + dep)


class CranDirectory:
    def __init__(self, packages_file):
        self.packages_file = packages_file
        self.packages_map = {}
        self._parse()

    def has_package(self, package_name):
        if (package_name not in self.packages_map):
            return False
        return True

    def get_dependencies_for_package(self, package_name):
        if (not self.has_package(package_name)):
            raise Exception
        p = self.packages_map[package_name]
        return p.package_dependencies

    def get_package(self, package_name):
        if (not self.has_package(package_name)):
            raise Exception
        p = self.packages_map[package_name]
        return p

    def emit(self):
        package_names = self.packages_map.keys()
        for package_name in package_names:
            p = self.get_package(package_name)
            p.emit()

    def _parse(self):
        f = open(self.packages_file, "r")
        STATE_NONE = 1
        STATE_IN_DEP = 2

        state = STATE_NONE
        p = None
        for line in f:
            if (len(line) == 0):
                f.close()
                return
            line = line.rstrip('\r\n')
            m = re.match(r'Package: (\S+)', line)
            if (m is not None):
                package_name = m.group(1)
                p = CranPackage(package_name)
                self._add_package(p)
                state = STATE_NONE
                continue
            m = re.match(r'Version: (\S+)', line)
            if (m is not None):
                package_version = m.group(1)
                p.set_version(package_version)
                state = STATE_NONE
                continue
            m = re.match(r'Depends: (.*)', line)
            if (m is not None):
                s = m.group(1)
                p.add_dependencies(s)
                state = STATE_IN_DEP
                continue
            if (':' in line):
                state = STATE_NONE
                continue
            if (state == STATE_IN_DEP):
                if (p is not None):
                    p.add_dependencies(line)

        f.close()

    def _add_package(self, package):
        self.packages_map[package.get_name()] = package


class MinicranBuilder:
    def __init__(self, print_only, output_dir, tmp_dir, platform, rversion, branch, buildnum):
        self.print_only = print_only
        self.output_dir = output_dir
        self.tmp_dir = tmp_dir
        self.platform = platform
        self.rversion = rversion
        self.branch = branch
        self.buildnum = buildnum

        self.s3_url_prefix = "https://h2o-release.s3.amazonaws.com/h2o/"
        self.repo_subdir = None
        self.extension = None
        self.project_version = None

        self.h2o_cran = None
        self.full_cran = None

    def create_output_dir(self):
        if (os.path.exists(self.output_dir)):
            print("")
            print("ERROR: Output directory already exists: " + self.output_dir)
            print("")
            sys.exit(1)

        try:
            os.makedirs(self.output_dir)
        except OSError as e:
            print("")
            print("mkdir failed (errno {0}): {1}".format(e.errno, e.strerror))
            print("    " + self.output_dir)
            print("")
            sys.exit(1)

    def create_cran_layout(self):
        if (self.platform == "windows"):
            self.repo_subdir = "bin/" + self.platform + "/contrib/" + self.rversion
            self.extension = ".zip"
        elif (self.platform == "macosx"):
            self.repo_subdir = "bin/" + self.platform + "/contrib/" + self.rversion
            self.extension = ".tgz"
        else:
            self.repo_subdir = "src/contrib"
            self.extension = ".tar.gz"

        os.makedirs(os.path.join(self.output_dir, self.repo_subdir))

    def download_h2o(self):
        if (self.buildnum == "latest"):
            latest_url = self.s3_url_prefix + self.branch + "/latest"
            latest_path = os.path.join(self.tmp_dir, "latest")
            download_file(latest_url, latest_path)
            f = open(latest_path, "r")
            line = f.readline()
            self.buildnum = line.strip()
            f.close()

        print("H2O branch: " + self.branch)
        print("H2O build number: " + self.buildnum)

        project_version_url = self.s3_url_prefix + self.branch + "/" + str(self.buildnum) + "/project_version"
        project_version_path = os.path.join(self.tmp_dir, "project_version")
        download_file(project_version_url, project_version_path)
        if True:
            f = open(project_version_path, "r")
            line = f.readline()
            self.project_version = line.strip()
            f.close()

        print("H2O project version: " + self.project_version)

        # Need to unzip the jar file and unpack the R component.

        desc_file_name = "PACKAGES"
        desc_url = self.s3_url_prefix + self.branch + "/" + str(self.buildnum) + "/R/src/contrib/" + desc_file_name
        desc_path = os.path.join(self.tmp_dir, desc_file_name)
        download_file(desc_url, desc_path)

        self.h2o_cran = CranDirectory(desc_path)
        self.h2o_cran.emit()

        # r_source_file_name = "h2o_" + self.project_version + ".tar.gz"
        # r_source_url = self.s3_url_prefix + self.branch + "/" + str(self.buildnum) + "/R/src/contrib/" + r_source_file_name
        # r_source_path = os.path.join(self.tmp_dir, r_source_file_name)
        # download_file(r_source_url, r_source_path)

        # h2o_jar_file_name = "h2o-" + self.project_version + ".zip"
        # h2o_jar_url = self.s3_url_prefix + self.branch + "/" + str(self.buildnum) + "/" + h2o_jar_file_name
        # h2o_jar_path = os.path.join(self.tmp_dir, h2o_jar_file_name)
        # download_file(h2o_jar_url, h2o_jar_path)

    def extract_h2o_dependencies(self):
        pass

    def download_h2o_dependencies(self):
        pass

    def build(self):
        self.create_output_dir()
        self.create_cran_layout()
        self.download_h2o()
        self.extract_h2o_dependencies()
        self.download_h2o_dependencies()


#--------------------------------------------------------------------
# Main program
#--------------------------------------------------------------------

g_default_rversion = "3.0"
g_default_branch = "master"
g_default_buildnum = "latest"

# Global variables that can be set by the user.
g_script_name = ""
g_platform = None
g_output_dir = None
g_tmp_dir = None
g_rversion = g_default_rversion
g_branch = g_default_branch
g_buildnum = g_default_buildnum
g_print_only = False


def usage():
    print("")
    print("Build a minimal self-contained CRAN-line repo containing the H2O package")
    print("and its dependencies.  The intent is that the output of this tool can be")
    print("put on a USB stick and installed on a computer with no network connection.")
    print("")
    print("Usage:  " + g_script_name +
          " --platform <platform_name>"
          " --outputdir <dir>"
          " [--rversion <version_number>]"
          " [--branch <branch_name>]"
          " [--build <build_number>]"
          " [-n]")
    print("")
    print("    --platform    OS that R runs on.  (e.g. linux, windows, macosx)")
    print("")
    print("    --outputdir   Directory to be created.  It must not already exist.")
    print("")
    print("    --rversion    Version of R.  (e.g. 2.14, 2.15, 3.0)")
    print("                  (Default is: "+g_default_rversion+")")
    print("")
    print("    --branch      H2O git branch.  (e.g. master, rel-jacobi)")
    print("                  (Default is: "+g_default_branch+")")
    print("")
    print("    --buildnum    H2O build number.  (e.g. 1175, latest)")
    print("                  (Default is: "+g_default_buildnum+")")
    print("")
    print("    -n            Print what would be done, but don't actually do it.")
    print("")
    print("Examples:")
    print("")
    print("    Just accept the defaults and go:")
    print("        "+g_script_name)
    print("")
    print("    Build for R version 3.0.x on Windows; use the h2o jacobi release latest:")
    print("        "+g_script_name+" --platform windows --rversion 3.0 --branch rel-jacobi --build latest "
          "--outputdir h2o_minicran_windows_3.0_rel-jacobi")
    print("")
    sys.exit(1)


def unknown_arg(s):
    print("")
    print("ERROR: Unknown argument: " + s)
    print("")
    usage()


def bad_arg(s):
    print("")
    print("ERROR: Illegal use of (otherwise valid) argument: " + s)
    print("")
    usage()


def unspecified_arg(s):
    print("")
    print("ERROR: Argument must be specified: " + s)
    print("")
    usage()


def parse_args(argv):
    global g_platform
    global g_output_dir
    global g_rversion
    global g_branch
    global g_buildnum
    global g_print_only

    i = 1
    while (i < len(argv)):
        s = argv[i]

        if (s == "--platform"):
            i += 1
            if (i > len(argv)):
                usage()
            g_platform = argv[i]
            if (g_platform not in ["linux", "windows", "macosx"]):
                bad_arg(s)
        elif (s == "--rversion"):
            i += 1
            if (i > len(argv)):
                usage()
            g_rversion = argv[i]
            if (g_rversion not in ["2.14", "2.15", "3.0"]):
                bad_arg(s)
        elif (s == "--outputdir"):
            i += 1
            if (i > len(argv)):
                usage()
            g_output_dir = argv[i]
        elif (s == "--branch"):
            i += 1
            if (i > len(argv)):
                usage()
            g_branch = argv[i]
        elif (s == "--buildnum"):
            i += 1
            if (i > len(argv)):
                usage()
            g_buildnum = argv[i]
        elif (s == "-n"):
            g_print_only = True
        elif (s == "-h" or s == "--h" or s == "-help" or s == "--help"):
            usage()
        else:
            unknown_arg(s)

        i += 1

    if (g_platform is None):
        unspecified_arg("platform")

    if (g_output_dir is None):
        unspecified_arg("output directory")


def remove_tmp_dir():
    if (os.path.exists(g_tmp_dir)):
        shutil.rmtree(g_tmp_dir)
        print "Removed tmp directory: " + g_tmp_dir


def main(argv):
    """
    Main program.

    @return: none
    """
    global g_script_name
    global g_tmp_dir

    g_script_name = os.path.basename(argv[0])

    # Override any defaults with the user's choices.
    parse_args(argv)

    # Create tmp dir and clean up on exit with a callback.
    g_tmp_dir = tempfile.mkdtemp(suffix=".tmp_minicran")
    print "Created tmp directory: " + g_tmp_dir
    atexit.register(remove_tmp_dir)

    # Do the work.
    try:
        b = MinicranBuilder(g_print_only, g_output_dir, g_tmp_dir, g_platform, g_rversion, g_branch, g_buildnum)
        b.build()
    except KeyboardInterrupt:
        print("")
        pass


# if __name__ == "__main__":
#     main(sys.argv)


if __name__ == "__main__":
    b = CranDirectory("PACKAGES")
    b.emit()
