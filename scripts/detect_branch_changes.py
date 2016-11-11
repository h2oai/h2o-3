#!/usr/bin/env python
"""
This is a Jenkins script that is supposed to be run after the
GithubPullRequestBuilder plugin. It will determine what files has changed in
the PR being tested, and set up several environmental variables accordingly.

In particular, the following variables will be emitted:
    H2O_RUN_PY_TESTS
    H2O_RUN_R_TESTS
    H2O_RUN_JAVA_TESTS
    H2O_RUN_JS_TESTS
Each of these will have a string value of either "true" or "false". These
variables determine which test suites should be then started by the Jenkins:
if the PR affects only Python files, then only the `H2O_RUN_PY_TESTS` flag
will be set, etc.

We may extend the set of environment variables in the future.

Since Python is unable to set environment variables on its own, it rather
prints to stdout commands like `EXPORT H2O_RUN_PY_TESTS="true"`. Thus, the
caller script should take the output of this python script and execute it
using the `source` command.
"""
import os
import subprocess

environment = {"py": False, "r": False, "java": False, "js": False}

def mark_all_flags_true():
    for k in environment.keys():
        environment[k] = True

def mark_flag_true(flag):
    environment[flag] = True

def error(msg):
    print("ECHO")
    print("ECHO '%s'" % msg)
    print("ECHO")
    mark_all_flags_true()

def get_list_of_modified_files(source_branch, target_branch):
    out = subprocess.check_output(["get", "diff", "--name-only", source_branch, target_branch])
    return out.split("\n")


def run():
    source_branch = os.environ.get("ghprbSourceBranch")
    if not source_branch:
        return error("Environment variable ghprbSourceBranch not set")

    target_branch = os.environ.get("ghprbTargetBranch")
    if not target_branch:
        return error("Environment variable ghprbTargetBranch not set")

    try:
        files_changed = get_list_of_modified_files(source_branch, target_branch)
    except Exception as e:
        return error("%r when trying to retrieve the list of changed files" % e)

    for fname in files_changed:
        if fname.startswith("h2o-py/"):
            mark_flag_true("py")
        elif fname.startswith("h2o-r/"):
            mark_flag_true("r")
        else:
            mark_all_flags_true()
            break


if __name__ == "__main__":
    run()
    for key, value in environment.items():
        print("EXPORT H2O_RUN_%s_TESTS=\"%s\"" % (key.upper(), str(value).lower()))
