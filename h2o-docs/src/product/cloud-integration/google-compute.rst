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

 <need new screenshot>

4. Click **Launch on Compute Engine**.

 - Select a zone that has p100s or k80s (such as us-east1).
 - Optionally change the number of cores and amount of memory. (This defaults to 32 cpus and 120 GB ram.)
 - Specify a GPU type. (This defaults to a p100 GPU.)
 - Optionally change the number of GPUs. (default is 2.) 
 - Specify the boot disk type and size.
 - Optionally change the network name and subnetwork names. Be sure that whichever network you specify has port 54321 exposed.
 - Click **Deploy** when you are done. H2O-3 will begin deploying. Note that this can take several minutes. 

 <need new screenshot>

5. A summary page displays when the compute engine is successfully deployed. Click on the Instance link to retrieve the external IP address for starting H2O-3.

 <need new screenshot>
 
6. In your browser, go to https://[External_IP]:54321 to start Flow. 


