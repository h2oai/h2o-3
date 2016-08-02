# -*- encoding: utf-8 -*-
"""
Handler to an asynchronous task executed on the remote server.

A job is an object with states: CREATED, RUNNING, DONE, FAILED, CANCELLED
A job can be polled for completion and reports the progress so far if it is still RUNNING.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import division, print_function, absolute_import, unicode_literals

import warnings

import h2o
from h2o.utils.progressbar import ProgressBar


class H2OJob(object):
    """A class representing an H2O Job."""

    __PROGRESS_BAR__ = True  # display & update progress bar while polling

    def __init__(self, jobs, job_type):
        """Initialize new H2OJob object."""
        if "jobs" in jobs:
            job = jobs["jobs"][0]
        elif "job" in jobs:
            job = jobs["job"]
        else:
            job = jobs

        self.job = job
        self.status = job["status"]
        self.job_key = job["key"]["name"]
        self.dest_key = job["dest"]["name"]
        self.warnings = None
        self.progress = 0
        self.exception = job["exception"] if "exception" in job else None
        self._job_type = job_type
        self._polling = False
        self._poll_count = 10**10


    def poll(self):
        """
        Wait until the job finishes.

        This method will continuously query the server about the status of the job, until the job reaches a
        completion. During this time we will display (in stdout) a progress bar with % completion status.
        """
        try:
            pb = ProgressBar(self._job_type + " progress")
            pb.execute(self._refresh_job_status)
        except StopIteration as e:
            if str(e) == "cancelled":
                self.status = "CANCELLED"
                h2o.api("POST /3/Jobs/%s/cancel" % self.job_key)
                print("Job {} was cancelled.".format(self.job_key))
            # Potentially we may want to re-raise the exception here

        if self.warnings:
            for w in self.warnings:
                warnings.warn(w)
        # TODO: this needs to br thought through more carefully
        # check if failed... and politely print relevant message
        if self.status == "CANCELLED":
            raise EnvironmentError("Job with key {} was cancelled by the user.".format(self.job_key))
        if self.status == "FAILED":
            if (isinstance(self.job, dict)) and ("stacktrace" in list(self.job)):
                raise EnvironmentError("Job with key {} failed with an exception: {}\nstacktrace: "
                                       "\n{}".format(self.job_key, self.exception, self.job["stacktrace"]))
            else:
                raise EnvironmentError("Job with key %s failed with an exception: %s" % (self.job_key, self.exception))

        return self

    def poll_once(self):
        """Query the job status and show the progress bar, but then cancel immediately."""
        self._poll_count = 1
        self.poll()
        self._poll_count = 10**10
        return self

    def _refresh_job_status(self):
        if self._poll_count <= 0: raise StopIteration("")
        jobs = h2o.api("GET /3/Jobs/%s" % self.job_key)
        self.job = jobs["jobs"][0] if "jobs" in jobs else jobs["job"][0]
        self.status = self.job["status"]
        self.progress = min(self.job["progress"], 1)
        self.exception = self.job["exception"]
        self.warnings = self.job["warnings"] if "warnings" in self.job else None
        self._poll_count -= 1
        return self.progress

    def __repr__(self):
        if self.status in {"CREATED", "RUNNING"}:
            desc = "at %d%%" % int(self.progress * 100 + 0.5)
        else:
            desc = self.status.lower()
        return "<H2OJob id=%s %s>" % (self.job_key, desc)
