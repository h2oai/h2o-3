package jacoco.report.internal.html.wrapper;

import jacoco.report.internal.html.parse.DSVParser;
import jacoco.report.internal.html.parse.ParseItem;
import org.jacoco.core.analysis.*;

import java.io.*;
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
            ICoverageNode.CounterEntity.METHOD,
            ICoverageNode.CounterEntity.CLASS};

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
        cch.setSignature(c.getSignature());
        cch.setSuperName(c.getSuperName());
        cch.setInterfaces(c.getInterfaceNames());
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

    public static List<ParseItem> parse(File path) {
        return (new DSVParser(System.out, System.err)).parse(path);
    }

    public static BundleCoverageHighlight parseBundle(BundleCoverageHighlight b, File path) {
        for (ParseItem p : parse(path)) {
            apply(b, p);
        }
        updateDisplay(b);
        return b;
    }

    private static void apply(IBundleCoverage b, ParseItem pi) {
        apply(b, pi, false);
    }

    private static void apply(IPackageCoverage p, ParseItem pi) {
        apply(p, pi, false);
    }

    private static void apply(IClassCoverage c, ParseItem pi) {
        apply(c, pi, false);
    }

    private static void apply(IMethodCoverage m, ParseItem pi) {
        apply(m, pi, false);
    }

    private static void apply(IBundleCoverage b, ParseItem pi, boolean propagate) {
        if (propagate) {
            for (IPackageCoverage p : b.getPackages()) {
                apply(p, pi, true);
            }
            applyValues(b, pi);
        } else if (pi.hasPackageName()) {
            boolean found = false;
            for (IPackageCoverage p : b.getPackages()) {
                if (pi.matches(p)) {
                    found = true;
                    apply(p, pi);
                }
            }
            if (!found) {
                err("Could not find package: " + pi.getPackageName());
            }
        } else {
            applyValues(b, pi);
            if (pi.propagate()) apply(b, pi, true);
        }
    }

    private static void apply(IPackageCoverage p, ParseItem pi, boolean propagate) {
        if (propagate) {
            for (IClassCoverage c : p.getClasses()) {
                apply(c, pi, true);
            }
            applyValues(p, pi);
        } else if (pi.hasClassName()){
            boolean found = false;
            for (IClassCoverage c : p.getClasses()) {
                if (pi.matches(c)) {
                    found = true;
                    apply(c, pi);
                }
            }
            if (!found) {
                err("Could not find class: " + pi.getClassName());
            }
        } else {
            applyValues(p, pi);
            if (pi.propagate()) apply(p, pi, true);
        }
    }

    private static void apply(IClassCoverage c, ParseItem pi, boolean propagate) {
        if (propagate) {
            for (IMethodCoverage m : c.getMethods()) {
                apply(m, pi, true);
            }
            applyValues(c, pi);
        } else if (pi.hasMethodName()) {
            boolean found = false;
            for (IMethodCoverage m : c.getMethods()) {
                if (pi.matches(m)) {
                    found = true;
                    apply(m, pi);
                }
            }
            if (!found) {
                err("Could not find method: " + pi.getMethodName());
            }
        } else {
            applyValues(c, pi);
            if (pi.propagate()) apply(c, pi, true);
        }
    }

    private static void apply(IMethodCoverage m, ParseItem pi, boolean propagate) {
        applyValues(m, pi);
    }

    private static void applyValues(ICoverageNode h, ParseItem pi) {
        if (h instanceof IHighlightNode) {
            NodeHighlightResults nhr = ((IHighlightNode) h).getHighlightResults();
            for (ICoverageNode.CounterEntity ce : pi.getHeaders()) {
                nhr.entity_total_results.put(ce, !(h.getCounter(ce).getCoveredRatio() < pi.getValue(ce) / 100));
            }
        }
    }

    private static void err(String s) {
        System.err.println("ERR: " + s);
    }

    /*public static BundleCoverageHighlight parseBundle(BundleCoverageHighlight b, File path_to_dsv) throws IOException {
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
            sc.useDelimiter("\\s*:\\s*");
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
        updateDisplay(b);
        return b;
    }*/

    private static void updateDisplay(IBundleCoverage b) {
        for (IPackageCoverage p : b.getPackages()) {
            updateDisplay(p);
        }
    }

    private static NodeHighlightResults updateDisplay(IPackageCoverage p) {
        NodeHighlightResults p_nhr;
        if (p instanceof IHighlightNode) {
            p_nhr = ((IHighlightNode) p).getHighlightResults();
            p_nhr.mergeTotaltoBody();
        } else {
            p_nhr = new NodeHighlightResults();
        }
        for (IClassCoverage c : p.getClasses()) {
            p_nhr.mergeBodyResults(updateDisplay(c));
        }
        return p_nhr;
    }

    private static NodeHighlightResults updateDisplay(IClassCoverage c) {
        NodeHighlightResults c_nhr;
        if (c instanceof IHighlightNode) {
            c_nhr = ((IHighlightNode) c).getHighlightResults();
            c_nhr.mergeTotaltoBody();
        } else {
            c_nhr = new NodeHighlightResults();
        }
        for (IMethodCoverage m : c.getMethods()) {
            c_nhr.mergeBodyResults(updateDisplay(m));
        }
        return c_nhr;
    }

    private static NodeHighlightResults updateDisplay(IMethodCoverage m) {
        NodeHighlightResults m_nhr;
        if (m instanceof IHighlightNode) {
            m_nhr = ((IHighlightNode) m).getHighlightResults();
            m_nhr.mergeTotaltoBody();
        } else {
            m_nhr = new NodeHighlightResults();
        }
        return m_nhr;
    }

    /*private static boolean applyToNode(IBundleCoverage root, String full_name, Map<ICoverageNode.CounterEntity, Double> values, boolean propagate) {
        DeconstructedName name = deconstruct(full_name);
        for (IPackageCoverage p : root.getPackages()) {
            System.out.println(name.packageName + " " + name.className + " " + name.methodName);
            System.out.println(p.getName());
            if (p.getName().matches(name.packageName)) {
                if (!applyToPackage(p, name, values, propagate)) {
                    apply(p, values, propagate);
                }
                return true;
            }
        }
        return false;
    }*/

    private static void apply(IPackageCoverage p, Map<ICoverageNode.CounterEntity, Double> values, boolean propagate) {
        if (p instanceof IHighlightNode) {
            for (ICoverageNode.CounterEntity ce : values.keySet()) {
                ((IHighlightNode) p).getHighlightResults().entity_total_results.put(ce, !(p.getCounter(ce).getCoveredRatio() < values.get(ce) / 100));
            }
        }
        if (propagate) {
            for (IClassCoverage c : p.getClasses()) {
                apply(c, values, propagate);
            }
        }
    }

    private static boolean applyToPackage(IPackageCoverage root, DeconstructedName name, Map<ICoverageNode.CounterEntity, Double> values, boolean propagate) {
        for (IClassCoverage c : root.getClasses()) {
            if (c.getName().equals(name.className)) {
                if (!applyToClass(c, name, values, propagate)) {
                    apply(c, values, propagate);
                }
                return true;
            }
        }
        return false;
    }

    private static void apply(IClassCoverage c, Map<ICoverageNode.CounterEntity, Double> values, boolean propagate) {
        if (c instanceof IHighlightNode) {
            for (ICoverageNode.CounterEntity ce : values.keySet()) {
                ((IHighlightNode) c).getHighlightResults().entity_total_results.put(ce, !(c.getCounter(ce).getCoveredRatio() < values.get(ce) / 100));
            }
        }
        if (propagate) {
            for (IMethodCoverage m : c.getMethods()) {
                apply(m, values, propagate);
            }
        }
    }

    private static boolean applyToClass(IClassCoverage root, DeconstructedName name, Map<ICoverageNode.CounterEntity, Double> values, boolean propagate) {
        for (IMethodCoverage m : root.getMethods()) {
            if (m.getName().equals(name.methodName)) {
                apply(m, values, propagate);
                return true;
            }
        }
        return false;
    }

    private static void apply(IMethodCoverage m, Map<ICoverageNode.CounterEntity, Double> values, boolean propagate) {
        if (m instanceof IHighlightNode) {
            for (ICoverageNode.CounterEntity ce : values.keySet()) {
                ((IHighlightNode) m).getHighlightResults().entity_total_results.put(ce, !(m.getCounter(ce).getCoveredRatio() < values.get(ce) / 100));
            }
        }
    }

    /*private static DeconstructedName deconstruct(String name) {
        name = name.trim();
        Pattern package_pattern = Pattern.compile("([a-zA-Z_](?:[a-zA-Z0-9._]*(?:/|$))+)");
        Pattern class_pattern = Pattern.compile("([a-zA-Z_](?:[a-zA-Z0-9_]*(?:\\.|$))+)");
        Pattern method_pattern = Pattern.compile(".*");
        DeconstructedName d_name = new DeconstructedName();
        Matcher m = package_pattern.matcher(name);
        if (m.lookingAt()) {
            d_name.packageName = m.group();
            if (d_name.packageName.endsWith("/")) d_name.packageName = d_name.packageName.substring(0, d_name.packageName.length() - 1);
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
    }*/

    private static class DeconstructedName {
        String packageName;
        String className;
        String methodName;

        public DeconstructedName() {
            packageName = className = methodName = "";
        }
    }
}
