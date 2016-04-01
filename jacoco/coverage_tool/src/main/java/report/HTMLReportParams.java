package report;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by nkalonia1 on 3/31/16.
 */
public class HTMLReportParams {
    public List<File> executionDataFiles;
    public List<File> classesDirectories;
    public List<File> sourceDirectories;
    public File reportDirectory;
    public String title;

    public HTMLReportParams() {
        title = "";
        executionDataFiles = new ArrayList<File>();
        classesDirectories = new ArrayList<File>();
        sourceDirectories = new ArrayList<File>();
        reportDirectory = new File("html");
    }
}
