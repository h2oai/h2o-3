package jacoco.report.internal.html.wrapper;

import org.jacoco.core.analysis.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by nkalonia1 on 3/17/16.
 */
public class CoverageWrapper {
    private static final ICoverageNode.CounterEntity[] _default = {ICoverageNode.CounterEntity.INSTRUCTION,
            ICoverageNode.CounterEntity.BRANCH,
        ICoverageNode.CounterEntity.COMPLEXITY,
        ICoverageNode.CounterEntity.LINE,
        ICoverageNode.CounterEntity.METHOD};

    public static BundleCoverageHighlight wrapBundle(IBundleCoverage b) {
        List<IPackageCoverage> packages = new LinkedList<IPackageCoverage>();
        for (IPackageCoverage p : b.getPackages()) {
            packages.add(wrapPackage(p));
        }
        BundleCoverageHighlight bch = new BundleCoverageHighlight(b.getName(), packages);
        return bch;
    }

    public static PackageCoverageHighlight wrapPackage(IPackageCoverage p) {
        List<IClassCoverage> classes = new LinkedList<IClassCoverage>();
        for (IClassCoverage c : p.getClasses()) {
            classes.add(wrapClass(c));
        }
        PackageCoverageHighlight pch = new PackageCoverageHighlight(p.getName(), classes, p.getSourceFiles());

        return pch;
    }

    public static ClassCoverageHighlight wrapClass(IClassCoverage c) {
        ClassCoverageHighlight cch = new ClassCoverageHighlight(c.getName(), c.getId(), c.isNoMatch());
        for (IMethodCoverage m : c.getMethods()) {
            cch.addMethod(wrapMethod(m));
        }
        cch.setSourceFileName(c.getSourceFileName());
        return cch;
    }

    public static MethodCoverageHighlight wrapMethod(IMethodCoverage m) {
        MethodCoverageHighlight mch = new MethodCoverageHighlight(m.getName(), m.getDesc(), m.getSignature());
        mch.increment(m);
        return mch;
    }

    public static BundleCoverageHighlight parseBundle(BundleCoverageHighlight b, String path_to_dsv) throws IOException {
        FileReader fileReader = new FileReader(path_to_dsv);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        Scanner sc;
        ICoverageNode.CounterEntity[] headers = _default;
        while ((line = bufferedReader.readLine()) != null) {
            String name;
            Map<ICoverageNode.CounterEntity, Double> values = new HashMap<ICoverageNode.CounterEntity, Double>();
            boolean propagate = false;
            sc = new Scanner(line);
            sc.useDelimiter(":");
            name = sc.next();
            for (ICoverageNode.CounterEntity head : headers) {
                values.put(head, sc.nextDouble());
            }
            if (sc.hasNextBoolean()) {
                propagate = sc.nextBoolean();
            }
            applyToNode(b, name, values, propagate);
            sc.close();
        }
        return b;
    }

    private static boolean applyToNode(BundleCoverageHighlight root, String full_name, Map<ICoverageNode.CounterEntity, Double> values, boolean propagate) {
        DeconstructedName name = deconstruct(full_name);
        return false;
    }

    private static DeconstructedName deconstruct(String name) {
        name = name.trim();
        Pattern package_pattern = Pattern.compile("([a-zA-Z_](?:[a-zA-Z0-9._]*(?:/|$))+)");
        Pattern class_pattern = Pattern.compile("([a-zA-Z_](?:[a-zA-Z0-9_]*(?:\\.|$))+)");
        Pattern method_pattern = Pattern.compile(".*");
        DeconstructedName d_name = new DeconstructedName();
        Matcher m = package_pattern.matcher(name);
        if (m.lookingAt()) {
            d_name.packageName = m.group();
            if (d_name.packageName.endsWith("/")) d_name.packageName = d_name.packageName.substring(0, d_name.packageName.length() - 1);
            d_name.packageName = d_name.packageName.replaceAll("/", ".");
            name = name.substring(m.end());
            m = class_pattern.matcher(name);
            if (m.lookingAt()) {
                d_name.className = m.group();
                if (d_name.className.endsWith(".")) d_name.className = d_name.className.substring(0, d_name.className.length() - 1);
                name = name.substring(m.end());
                m = method_pattern.matcher(name);
                if (m.lookingAt()) {
                    d_name.methodName = m.group();
                }
            }
        }
        return d_name;
    }

    private static class DeconstructedName {
        String packageName;
        String className;
        String methodName;

        public DeconstructedName() {
            packageName = className = methodName = "";
        }
    }
}
