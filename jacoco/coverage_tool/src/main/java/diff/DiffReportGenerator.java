package diff;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.lang.Runtime;
import java.lang.Process;
import java.lang.ProcessBuilder;

public class DiffReportGenerator {

    // TODO: Add functionality for comparing INDEX and HEAD through abstraction.
    public static DiffReport run(String commit1, String commit2, Path root_dir) throws IOException {
        String[] cmd = {"git", "diff", commit1, commit2, "--", "*.java"};
        return exec(cmd, root_dir);
    }

    public static DiffReport run(String commit1, String commit2) throws IOException {
        return run(commit1, commit2, Paths.get(System.getProperty("user.dir")));
    }

    private static DiffReport exec(String[] cmd, Path root_dir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(root_dir.toFile());
        Process p = pb.start();
        String readLine;
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        while (((readLine = br.readLine()) != null)) {
            System.out.println(readLine);
        }
        DiffReport dr = generateReport(p.getInputStream(), root_dir);
        return dr;
    }

    private static DiffReport generateReport(InputStream is, Path root) {
        DiffScanner ds = new DiffScanner(is, root);
        DiffReport dr = new DiffReport();
        DiffFile next_df = null;
        DiffHunkHeader dbh;
        while (ds.hasNext()) {
            if (ds.hasNextFile()) {
                next_df = ds.nextFile();
                dr.pushDiffFile(next_df);
            } else if (ds.hasNextHunkHeader()) {
                if (next_df == null) {
                    System.err.println("ERROR: Hunk header found before file");
                    break;
                }
                dbh = ds.nextHunkHeader();
                while(ds.hasNext()) {
                    if (ds.hasNextHunk()) {
                        DiffHunk db = ds.nextHunk();
                        if (db.isEmpty()) break;
                        next_df.pushDiff(new DiffHunk(dbh.getRemoveStart() + db.getRemoveStart(),
                                db.getRemoveLength(),
                                dbh.getInsertStart() + db.getInsertStart(),
                                db.getInsertLength()));
                        dbh.pushRemove(db.getRemoveEnd());
                        dbh.pushInsert(db.getInsertEnd());
                    } else {
                        break;
                    }
                }
            } else {
                // Log some error message...
                System.err.println("ERROR: Couldn't find next");
                break;
            }
        }
        ds.close();
        return dr;
    }

    public static void main(String[] args) {
        try {
            DiffReport dr = DiffReportGenerator.run("30734d925274dbd868efd9d001f04183bc07f883", "HEAD", Paths.get("/Users/nkalonia1/h2o-3/"));
            Iterator i = dr.iterator();
            while (i.hasNext()) {
                System.out.println(i.next());
            }
        } catch (IOException ioe) {
            System.err.println("ERROR: " + ioe);
        }
    }
}

