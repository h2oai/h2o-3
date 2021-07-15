## Google Cloud Launcher Deployment Scripts

Contents:

  * rc.local
  * startup.sh
  * vm-build-script.sh
  * vm-runtime.sh

rc.local - needs to be copied to /etc/rc.local. Manages startup of the vm + cluster.

startup.sh - handles starting H2O. copy to /opt/h2oai/Scripts

vm-build-script.sh - configures vm with all settings necessary for deployment

   * installs java
   * installs python and R dependencies
   * configures nginx

vm-runtime.sh - handles creating credentials and necessary files prior to launch.

   * creates nginx certificates
   * creates vm specific username and password based off vm metadata
   * creates flatfile require for h2o-3 vm clustering.
