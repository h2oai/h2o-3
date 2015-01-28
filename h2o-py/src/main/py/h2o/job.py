"""
A job is an object with states: CREATED, RUNNING, DONE, FAILED, CANCELLED
A job can be polled for completion and reports the progress so far if it is still RUNNING.
"""

from connection import H2OConnectionBase as h2oConn
import time


class H2OJob(object):
    """
    A class representing an H2O Job.
    """
    def __init__(self, jobs):
        if "jobs" in jobs:
            job = jobs["jobs"][0]
        else:
            job = jobs["job"]

        self.jobs = jobs
        self.job = job
        self.status = job["status"]
        self.job_key = job['key']['name']
        self.destination_key = job['dest']['name']
        self.progress = 0

    def poll(self):
        sleep = 0.1
        running = True
        while running:
            H2OJob._update_progress(self.progress)
            time.sleep(sleep)
            if sleep < 1.0: sleep += 0.1
            self._refresh_job_view()
            running = self._is_running()
        H2OJob._update_progress(self.progress)
        print

        # check if failed... and politely print relevant message
        if self._is_failed():
            if self.status == "CANCELLED":
                raise EnvironmentError("Job with key {} was cancelled by the user."
                                       .format(self.job_key))
            if self.status == "FAILED":
                raise EnvironmentError("Job with key {} failed with an exception."
                                       .format(self.job_key))
        return self

    def _refresh_job_view(self):
        jobs = h2oConn.do_safe_get_json(url_suffix="Jobs/" + self.job_key)
        self.job = jobs["jobs"][0] if "jobs" in jobs else jobs["job"][0]
        self.status = self.job["status"]
        self.progress += self.job["progress"]

    def _is_failed(self):
        return self.status == "FAILED" or self.status == "CANCELLED"

    def _is_running(self):
        return self.status == "RUNNING" or self.status == "CREATED"

    @staticmethod
    def _update_progress(progress):
        progress = min(progress, 1)
        print '\r[{0}] {1}%'.format('#'*int(progress*100), progress*100)