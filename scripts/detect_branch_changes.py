#!/usr/bin/env python
"""
This is a Jenkins script that is supposed to be run after the
GithubPullRequestBuilder plugin. It will determine what files has changed in
the PR being tested, and set up several environmental variables accordingly.

In particular, the following files may be created in the `target_folder`:
    H2O_RUN_PY_TESTS
    H2O_RUN_R_TESTS
    H2O_RUN_JAVA_TESTS
    H2O_RUN_JS_TESTS
Presence / absence of each of these files indicates whether the corresponding
group of tests should be run.

We may extend the set of these flags in the future.


"""
from __future__ import print_function
import os
import subprocess

target_folder = "../h2o-py/build/tmp"
environment = {"py": False, "r": False, "java": False, "js": False}



def mark_all_flags_true():
    for k in environment:
        environment[k] = True


def mark_flag_true(flag):
    environment[flag] = True


def error(msg):
    print()
    print("WARNING: %s" % msg)
    print("         All tests will be scheduled to run.")
    print()
    mark_all_flags_true()


def get_list_of_modified_files(remote, source_branch, target_branch):
    print("> git merge-base %s/%s %s/%s" % (remote, source_branch, remote, target_branch))
    out1 = subprocess.check_output(["git", "merge-base", "%s/%s" % (remote, source_branch), "%s/%s" % (remote, target_branch)]).decode().rstrip()
    print("> git diff --name-only %s/%s %s" % (remote, source_branch, out1))
    out2 = subprocess.check_output(["git", "diff", "--name-only", "%s/%s" % (remote, source_branch), out1]).decode().rstrip()
    return out2.split("\n")


def run():
    source_branch = os.environ.get("ghprbSourceBranch")
    if not source_branch:
        return error("Environment variable ghprbSourceBranch not set")

    target_branch = os.environ.get("ghprbTargetBranch")
    if not target_branch:
        return error("Environment variable ghprbTargetBranch not set")

    remote = "origin"
    try:
        files_changed = get_list_of_modified_files(remote, source_branch, target_branch)
    except Exception as e:
        return error("%r when trying to retrieve the list of changed files" % e)

    for fname in files_changed:
        if fname.startswith("h2o-py/") or fname == "h2o-bindings/bin/gen_python.py":
            mark_flag_true("py")
        elif fname.startswith("h2o-r/") or fname == "h2o-bindings/bin/gen_R.py":
            mark_flag_true("r")
        elif fname.endswith(".md"):
            pass  # Changes in .md files don't require any jobs to be run
        else:
            mark_all_flags_true()
            break


def create_files(folder):
    abs_folder = os.path.join(os.path.dirname(__file__), folder)
    if not os.path.exists(abs_folder):
        os.makedirs(abs_folder)

    print()
    for key, value in environment.items():
        target_file = os.path.join(abs_folder, "H2O_RUN_%s_TESTS" % key.upper())
        if value:
            with open(target_file, "w"): pass
        else:
            if os.path.exists(target_file):
                os.remove(target_file)
        print("H2O_RUN_%s_TESTS = %s" % (key.upper(), str(value).lower()))
    print()


if __name__ == "__main__":
    run()
    create_files(target_folder)
