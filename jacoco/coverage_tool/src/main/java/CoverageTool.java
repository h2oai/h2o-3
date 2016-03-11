import diff.*;
import jacoco.DiffAnalyzerTest;
import parse.*;

import java.io.File;
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
        System.out.println(dr);
        System.out.println("STEP 2");
        System.out.println("RUNNING...");

        DiffAnalyzerTest dat = new DiffAnalyzerTest(new File("/Users/nkalonia1/h2o-3/h2o-core/"), dr);
        dat.create();
        System.out.println("STEP 2 COMPLETE");
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
            CoverageTool.run("ad65abd30135e555aebe2a2b3530506d6ef411b3", "HEAD", Paths.get("/Users/nkalonia1/h2o-3/"));
        } catch (IOException ioe) {
            System.err.println("ERROR: " + ioe);
        }
    }

}