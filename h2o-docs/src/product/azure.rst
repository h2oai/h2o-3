Using H2O with Azure
====================

Microsoft Azure provides an important collection of cloud services such as serveless computing, virtual machines, storage options, networking and much more. Azure provides the tools for a user to create a Data Science environment with H2O. 

This section describes the two H2O options currently available on Microsoft Azure

- The H2O Artificial Intelligence VM solution
- The H2O application for HDInsight 

H2O Artificial Intelligence VM
------------------------------

The Artificial Intelligence VM automatically deploys an H2O standalone VM or an H2O cluster in Azure along with every tool you might need for your data science applications. Once the deployment finishes you can:

- Connect to Jupyter Notebook by going to https://<VM DNS Name or IP Address>:8000/
- Connect to H2O Flow by going to http://<VM DNS Name or IP Address>:54321/

The Artificial Intelligence VM uses a Linux data science virtual machine as a base. The key software included are: 

- Latest version of H2O for Python and R
- Microsoft R Open
- Anaconda Python distribution (2.7 and 3.5)
- RStudio Server 
- Azure Storage Explorer
- Azure Command-Line for managing Azure resources 
- Azure SDK 
- Libraries in R and Python for use in Azure Machine Learning 

Create the H2O Artificial Intelligence VM
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Perform the following steps to create the H2O Artificial Intelligence VM.

1. Log in to your Azure portal (`https://portal.azure.com <https://portal.azure.com>`__) and click the **New** button.

   .. figure:: images/azure_new.png
      :alt: Azure > New

2. Search in the Marketplace for H2O. The search will return all of the H2O offers in the Azure Marketplace. Select the H2O Artificial Intelligence VM, and then click the **Create** button. 

   .. figure:: images/azure_select_h2o.png
      :alt: Select H2O in the Marketplace

3. Follow the UI instructions and fill the Basic settings: 

   - Username and Password that you will be using to connect to the VM
   - Subscriptions where you want to create the VM
   - The new resource group name 
   - The Location where the VM is going to be created. 

   .. figure:: images/azure_basic_settings.png
      :alt: Azure Basic settings

4. Click **OK** and move to the next step, which is Infrastructure setting.
5. Specify the desired Infrastructure settings:

   - Select the number of VMs that you want to create. (Note that specifying more than one VM means that you will create an H2O cluster.) 
   - Specify the Storage Account. This represents the associated storage account for the VM
   - Specify the Virtual Machine Size.

   .. figure:: images/azure_infrastructure_settings.png
      :alt: Azure Infrastructure settings

6. Click **OK** to begin the final validations. When the validations pass, click **OK**.
7. Finally, review the Terms of Use and H2O privacy policy. When you are, ready click **Purchase**.

   The VM solution will be created upon completion. The expected creation time is approximately 10 minutes. 

8. Once your deployment is created, locate your node-0, and look for the DNS Name and Public IP address. This is the way to access your node.

   .. figure:: images/azure_locate_node.png
      :alt: Locate your node

You are now ready to start using your H2O Artificial Intelligence VM! Go ahead and connect to your Jupyter notebooks and look for the H2O Examples to create your first H2O Model on Azure.

H2O Artificial Intelligence VM Troubleshooting Tips
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- Be aware that Azure subscriptions by default allow only 20 cores. If you get a Validation error on template deployment, this might be the cause. To increase the cores limit, follow these steps: `https://blogs.msdn.microsoft.com/girishp/2015/09/20/increasing-core-quota-limits-in-azure/ <https://blogs.msdn.microsoft.com/girishp/2015/09/20/increasing-core-quota-limits-in-azure/>`__

- OS Disk by default is small (approx 30GB). This means that you have approximately 16GB of free space to start with. This is the same for all VM sizes. It is recommended that you add an SSD data disk to the driver node (DSVM-0). You can do this by following these instructions: `https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-classic-attach-disk/ <https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-classic-attach-disk/>`__

- Select a VM size that provides RAM of at least 4x the size of your dataset. Refer to the following for more information about Azure VM sizes: `https://azure.microsoft.com/en-us/pricing/details/virtual-machines/ <https://azure.microsoft.com/en-us/pricing/details/virtual-machines/>`__

- For H2O, Java heap size is set to the 90% of available RAM. If you need to use less heap size on the driver node (H2O-0), you must stop H2O and launch it again with less heap memory. For example:

  :: 
   
     killall -q -v java
     nohup java -Xmx[WHAT-YOU-WANT]m -jar /dsvm/tools/h2o.jar -flatfile /dsvm/tools/flatfile.txt 1> /dev/null 2> h2o.err &

  You will also need to run this command with ``sudo`` once the VM is restarted.


