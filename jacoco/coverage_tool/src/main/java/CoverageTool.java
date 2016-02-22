import diff.*;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

public class CoverageTool {


    public void run() {
        // STEP 1: Get a DiffReport via DiffReportGenerator
        // STEP 2: Iterate through the files listed in the DiffReport and generate a list of all relevant class names.
        // STEP 3: Use this list of class names to specify what to be included when running jacoco
        // STEP 4: Run jacoco (should we serialize the DiffReport for future use?)
        // STEP 5: After the tests are run, find coverage results of all DiffHunks from DiffReport. Report findings.
    }

    public static void main(String[] args) {
    }

}