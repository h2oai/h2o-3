.. _install-on-google-cloud:

Install H2O on the Google Cloud Platform Offering
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This section describes how to install and start H2O Flow (H2O-3 web offering) in a Google Compute environment using the available Cloud Launcher offering.

1. In your browser, log in to the Google Compute Engine Console at https://console.cloud.google.com/. 

2. In the left navigation panel, select **Cloud Launcher**.

  .. image:: ../images/google_cloud_launcher.png
     :align: center
     :height: 266
     :width: 355

3. On the Cloud Launcher page, search for **H2O** and select the H2O-3 offering. 

  .. image:: ../images/google_h2o_offering.png
     :align: center

4. Click **Launch on Compute Engine**.

 - Specify a name for this deployment.
 - Select a zone for the deployment.
 - Select or customize a machine type and memory amount.
 - Specify the number of nodes for the virtual machine.
 - Specify the boot disk type and size (in GB).
 - Specify the network and subnetwork names. 

 Click **Deploy** when you are done. H2O-3 will begin deploying. Note that this can take several minutes. 

 .. image:: ../images/google_deploy_compute_engine.png
  :align: center

5. A summary page displays when the compute engine is successfully deployed. This page includes the instance ID and the username (always **h2oai**) and password that will be required when starting H2O-3. Click on the Instance link to retrieve the external IP address for starting H2O-3.

  .. image:: ../images/google_deploy_summary.png
     :align: center

6. Start H2O-3 using one of the following methods:

  **Flow**: In your browser, go to http://[External_IP]:440 or https://[External_IP]:80 to start Flow. Enter your username and password when prompted.

  **Python**: Run ``h2o.connect(address, port=443, username, password)``

  **R**: Run ``h2o.connect(address, port=443, username, password)``
