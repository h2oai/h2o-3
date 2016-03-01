import diff.*;
import parse.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CoverageTool {

    public static void run(String commit_from, String commit_to, Path root) throws IOException {
        // STEP 1: Get a DiffReport via DiffReportGenerator
        System.out.println("STEP 1");
        System.out.println("RUNNING...");
        DiffReport dr = DiffReportGenerator.run(commit_from, commit_to, root);
        System.out.println("STEP 1 COMPLETE");

        // STEP 2: Iterate through the files listed in the DiffReport and generate a list of all relevant class names.
        System.out.println("STEP 2");
        System.out.println("RUNNING...");
        Set<String> class_names = new HashSet<String>();
        Iterator<DiffFile> i = dr.iterator();
        while (i.hasNext()) {
            DiffFile df = i.next();
            class_names.addAll(getClassNames(df, ClassParser.parse(df.getPathB())));
        }
        System.out.println("STEP 1 COMPLETE");
        for(String s : class_names) {
            System.out.println(s);
        }
        // STEP 3: Use this list of class names to specify what to be included when running jacoco
        // STEP 4: Run jacoco (should we serialize the DiffReport for future use?)
        // STEP 5: After the tests are run, find coverage results of all DiffHunks from DiffReport. Report findings.
    }

    private static Set<String> getClassNames(DiffFile df, List<ClassHunk> c) {
        df.sortByRemove();
        Iterator<DiffHunk> di = df.iterator();
        Set<String> names = new HashSet<String>();
        while (di.hasNext()) {
            DiffHunk dh = di.next();
            for (ClassHunk ch : c) {
                if (dh.getRemoveStart() >= ch.getStart() && dh.getRemoveEnd() <= ch.getEnd()) {
                    names.add(ch.getName());
                    break;
                } else if ((dh.getRemoveStart() >= ch.getStart() && dh.getRemoveStart() <= ch.getEnd()) ||
                            (dh.getRemoveEnd() <= ch.getEnd() && dh.getRemoveEnd() >= ch.getStart())) {
                    names.add(ch.getName());
                }
            }
        }
        return names;

    }

    public static void main(String[] args) {
        try {
            CoverageTool.run("30734d925274dbd868efd9d001f04183bc07f883", "HEAD", Paths.get("/Users/nkalonia1/h2o-3/"));
        } catch (IOException ioe) {
            System.err.println("ERROR: " + ioe);
        }
    }

}