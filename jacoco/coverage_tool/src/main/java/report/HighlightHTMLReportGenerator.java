package report;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import diff.DiffReport;
import jacoco.report.html.HighlightHTMLFormatter;
import jacoco.report.internal.html.wrapper.CoverageWrapper;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.*;
import org.jacoco.report.html.HTMLFormatter;

/**
 * This example creates a HTML report for eclipse like projects based on a
 * single execution data store called jacoco.exec. The report contains no
 * grouping information.
 *
 * The class files under test must be compiled with debug information, otherwise
 * source highlighting will not work.
 */
public class HighlightHTMLReportGenerator {

    private final String title;

    private final File[] executionDataFiles;
    private final File[] classesDirectories;
    private final File[] sourceDirectories;
    private final File reportDirectory;
    private final File parseParamDirectory;

    private ExecFileLoader execFileLoader;

    /**
     * Create a new generator based for the given project.
     *
     */
    public HighlightHTMLReportGenerator(HighlightHTMLReportParams params) {
        this.title = params.title;
        executionDataFiles = params.executionDataFiles.toArray(new File[params.executionDataFiles.size()]);
        classesDirectories = params.classesDirectories.toArray(new File[params.classesDirectories.size()]);
        sourceDirectories = params.sourceDirectories.toArray(new File[params.sourceDirectories.size()]);
        reportDirectory = params.reportDirectory;
        parseParamDirectory = params.parseParamsFile;
    }

    /**
     * Create the report.
     *
     * @throws IOException
     */
    public void create() throws IOException {

        // Read the jacoco.exec file. Multiple data files could be merged
        // at this point
        loadExecutionData();

        // Run the structure analyzer on a single class folder to build up
        // the coverage model. The process would be similar if your classes
        // were in a jar file. Typically you would create a bundle for each
        // class folder and each jar you want in your report. If you have
        // more than one bundle you will need to add a grouping node to your
        // report
        final IBundleCoverage bundleCoverage = analyzeStructure();

        createReport(bundleCoverage);

    }

    private void createReport(final IBundleCoverage bundleCoverage)
            throws IOException {

        // Create a concrete report visitor based on some supplied
        // configuration. In this case we use the defaults
        final HTMLFormatter htmlFormatter = new HighlightHTMLFormatter();
        final IReportVisitor visitor = htmlFormatter
                .createVisitor(new FileMultiReportOutput(reportDirectory));

        // Initialize the report with all of the execution and session
        // information. At this point the report doesn't know about the
        // structure of the report being created
        visitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(),
                execFileLoader.getExecutionDataStore().getContents());

        // Populate the report structure with the bundle coverage information.
        // Call visitGroup if you need groups in your report.
        IBundleCoverage b = CoverageWrapper.parseBundle(CoverageWrapper.wrapBundle(bundleCoverage), parseParamDirectory);
        MultiSourceFileLocator src_dir = new MultiSourceFileLocator(4);
        for(File dir : sourceDirectories) {
            src_dir.add(new DirectorySourceFileLocator(
                    dir, "utf-8", 4));
        }
        visitor.visitBundle(b, src_dir);

        // Signal end of structure information to allow report to write all
        // information out
        visitor.visitEnd();

    }

    private void loadExecutionData() throws IOException {
        execFileLoader = new ExecFileLoader();
        for (File exec : executionDataFiles) {
            execFileLoader.load(exec);
        }
    }

    private IBundleCoverage analyzeStructure() throws IOException {
        final CoverageBuilder coverageBuilder = new CoverageBuilder();
        final Analyzer analyzer = new Analyzer(
                execFileLoader.getExecutionDataStore(), coverageBuilder);

        for(File dir : classesDirectories) {
            analyzer.analyzeAll(dir);
        }

        return coverageBuilder.getBundle(title);
    }

    private static HighlightHTMLReportParams parseArgs(final String[] args) {
        int i = 0;
        HighlightHTMLReportParams params = new HighlightHTMLReportParams();
        Path root = Paths.get(".");
        while (i < args.length) {
            switch(args[i]) {
                case "-o":
                    if (++i >= args.length) usage();
                    params.reportDirectory = root.resolve(args[i]).toFile();
                    break;
                case "-c":
                    if (++i >= args.length) usage();
                    PathMatcher cpm = FileSystems.getDefault().getPathMatcher("glob:" + args[i]);
                    findFiles(root, cpm, params.classesDirectories);
                    break;
                case "-s":
                    if (++i >= args.length) usage();
                    PathMatcher spm = FileSystems.getDefault().getPathMatcher("glob:" + args[i]);
                    findFiles(root, spm, params.sourceDirectories);
                    break;
                case "-e":
                    if (++i >= args.length) usage();
                    PathMatcher epm = FileSystems.getDefault().getPathMatcher("glob:" + args[i]);
                    findFiles(root, epm, params.executionDataFiles);
                    break;
                case "-t":
                    if (++i >= args.length) usage();
                    params.title = args[i];
                    break;
                case "-p":
                    if (++i >= args.length) usage();
                    params.parseParamsFile = root.resolve(args[i]).toFile();
                    break;
                default:
                    root = Paths.get(args[i]);
            }
            ++i;
        }
        if (params.title.isEmpty()) params.title = root.toAbsolutePath().normalize().getFileName().toString();
        return params;
    }

    private static void findFiles(Path root, final PathMatcher pm, final List<File> files) {
        SimpleFileVisitor<Path> v = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attrs) {
                if (file != null && pm.matches(file)) {
                    files.add(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                                                     BasicFileAttributes attrs) {
                if (dir != null && pm.matches(dir)) {
                    files.add(dir.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
        };
        try {
            Files.walkFileTree(root, v);
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
    }

    private static void usage() {
        System.exit(1);
    }

    /**
     * Starts the report generation process
     *
     * @param args
     *            Arguments to the application. This will be the location of the
     *            eclipse projects that will be used to generate reports for
     * @throws IOException
     */
    public static void main(final String[] args) throws IOException {
        System.out.println("PARSING...");
            final HighlightHTMLReportGenerator generator = new HighlightHTMLReportGenerator(parseArgs(args));
        System.out.println("CREATING...");
            generator.create();
    }

}