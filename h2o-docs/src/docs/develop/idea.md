# For IDEA Users (Github)

**1.** Create a [git clone of the H2O repository](build).

**2.** Open IDEA.

**3.** Click Import Project.

![Image](idea/02ImportProject.png)

**4.** Choose the H2O directory and click OK.

![Image](idea/03ChooseH2ODir.png)

**5.** Choose Import project from external model.  Choose Eclipse.  Click Next.

![Image](idea/04ChooseEclipse.png)

**6.** ENABLE LINK CREATED INTELLIJ IDEA MODULES TO ECLIPSE PROJECT FILES (this is not selected by default).  Click Next.

![Image](idea/05ConfigureImport.png)

**7.** H2O should be selected by default.  Keep it selected.  If the "experiments" module is selected uncheck it.  Click Next.

![Image](idea/06H2OSelected.png)

**8.** SDK 1.6 or 1.7 should selected by default.  If so click Finish.  If you don't have an SDK on your system you will need to install one first.

![Image](idea/07SelectJavaSK.png)

**9.** (Import from Eclipse) If prompted for Python configuration stuff just click Cancel.

![Image](idea/08CancelPython.png)

**10.** If prompted to Add Files to Git just click Cancel.

![Image](idea/09CancelAddProjectFilesToGit.png)

**11.** In IntelliJ IDEA / Preferences (CMD-,) set the project bytecode version to 1.6:

![Image](idea/11SetProjectBytecodeVersion.png)

**12.** Select a sample Java Application and right click on it.  Choose Run.

![Image](idea/12SelectJavaApplicationToRun.png)

**13.** In certain versions of IntelliJ you may need to set the Java heap size and re-run:

![Image](idea/13SetJavaHeapSize.png)

**14.** See the output of a successful run.

![Image](idea/14SuccessfulRunOutput.png)

**15.** You may connect to http://127.0.0.1:54321/ to use H2O interactively.

