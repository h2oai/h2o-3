Use H2O with Microsoft Azure Linux Data Science VM
==================================================

The Data Science Virtual Machine (DSVM) for Linux is an Ubuntu-based virtual machine image that makes it easy to get started with deep learning on Azure. CNTK, TensorFlow, MXNet, Caffe, Caffe2, DIGITS, H2O, Keras, Theano, and Torch are built, installed, and configured, so they are ready to run immediately. The NVIDIA driver, CUDA, and cuDNN are also included. All frameworks are the GPU versions but work on the CPU as well. Many sample Jupyter notebooks are included.

You can `view a full list of installed tools for the Linux edition here <https://docs.microsoft.com/en-us/azure/machine-learning/machine-learning-data-science-virtual-machine-overview>`__.

Create the H2O Artificial Intelligence VM
-----------------------------------------

Follow these steps to create the H2O Artificial Intelligence VM.

1. Log in to your `Azure portal <https://portal.azure.com>`__, and click **New**.  

  .. figure:: ../images/azurelin_new.png
     :alt: Create a new VM page on Microsoft Azure.

2. Search in the Marketplace for “H2O”. The result will show all of the H2O offerings in the Azure Marketplace. Select the Data Science Virtual Machine for Linux (Ubuntu), and click **Create**.

  .. figure:: ../images/azurelin_h2o_dsvm.png
     :alt: Data Science VM for Linux panel outlining how DSVM for Linux works and which tools are contained in it.

3. Follow the UI instructions and fill the Basic settings: 
   
   - Username and Password that you will be using to connect to the VM.
   - Subscriptions where you want to create the VM.
   - The new resource group name.
   - The location where the VM is going to be created.
   - Size of the VM.
   - (Optional for VMs with GPU) Select the HDD disk option, and search for the N-Series VM. See more on `GPU optimized virtual machine sizes <http://gpu.azure.com/>`__.

4. Wait for the VM Solution to be created. The expected creation time is around 10 minutes.

5. Locate your network security group after your deployment is created. Your Network Security Group is named by the default as **<your VM name>-nsg**. After you locate the security group, look at the inbound security rules.  

  .. figure:: ../images/azurelin_inbound_secrules.png
     :alt: Inbound and outbound security rules overview page with three inbound security rules.

6. Click **Add** to add a new Inbound Security Rule, and create a TCP Rule with Port Range 54321. Click **OK** when you are done.

  .. figure:: ../images/azurelin_add_inbound_secrule.png
     :alt: Add inbound security rule panel where the new TCP rule with a port range of 54321 has been added.

7. After the new Inbound Security Rule is added, you are ready to start using your Linux DSVM with H2O. Connect to your Jupyter notebooks and look at the examples in the **h2o > python** folder to create your first H2O Model on Azure. 

   1. Connect to Jupyter Notebook by going to **https://<<VM DNS Name or IP Address>>:8000/**.
   2. Connect to H2O Flow by going to **http://<<VM DNS Name or IP Address>>:54321/**.

Tips for troubleshooting
------------------------

- Be aware that Azure subscriptions only allow 20 cores by default. If you get a Validation error on template deployment, this might be the cause. Follow these `steps to increase your core quota limits <https://blogs.msdn.microsoft.com/girishp/2015/09/20/increasing-core-quota-limits-in-azure/>`__.
- OS Disk by default is small (approx 30GB). In this case, you will have approximately 16GB of free space to start with. This is the same for all VM sizes. Follow these `steps to add an SSD data disk <https://learn.microsoft.com/en-us/azure/devtest-labs/devtest-lab-attach-detach-data-disk>`__.
- Pick a VM size that provides RAM of at least 4x the size of your dataset. See more about `Azure VM sizes <https://azure.microsoft.com/en-us/pricing/details/virtual-machines/>`__.
- The previous examples use a ``locate`` function for importing data. This function is not available to end users. For example:

  :: 

    air_path = [_locate("smalldata/airlines/allyears2k_headers.zip")]

  To retrieve the datasets, use one of the following methods instead:

    - Replace this path with a pointer to an Amazon S3 file at **http://s3.amazonaws.com/h2o-public-test-data/..**. For example: 

    ::

      air_path="http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip"

    - Use ``wget`` to retrieve the files.

  A list of the datasets used in the examples along with their locate path is available in the **h2o > python > README** file.