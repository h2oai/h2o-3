package diff;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.NoSuchElementException;
import java.util.InputMismatchException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
/**
 * Wrapper class for Java's Scanner. It is designed to parse the output of a diff provided by git.
 **/
public class DiffScanner {
    private static final Pattern _HUNK_HEADER_REGEX =
            Pattern.compile("^@@\\s-(\\d+),\\d+\\s\\+(\\d+),\\d+\\s@@\\s.*$\\n", Pattern.MULTILINE);
    private static final Pattern _HUNK_REGEX =
            Pattern.compile("(?=^[+\\-\\s])((?:^\\s.*$\\n)*)" +
                    "((?:^-.*$\\n)*)" +
                    "((?:^\\+.*$\\n)*)", Pattern.MULTILINE);
    private static final Pattern _FILE_REGEX =
            Pattern.compile("(?:^\\\\\\sNo\\snewline\\sat\\send\\sof\\sfile$\\n)?" +
                    "^diff\\s--git\\s.*$\\n" +
                    "(?:^(?:new|deleted)\\sfile\\smode\\s\\w+$\\n)?" +
                    "^index\\s\\w+\\.\\.\\w+(?:\\s\\w+)?$\\n" +
                    "^---\\s(?:[abciow]/)?(.*)$\\n" +
                    "^\\+\\+\\+\\s(?:[abciow]/)?(.*)$\\n", Pattern.MULTILINE);

    private Scanner _sc;
    private Path _root;
    private MatchResult _next_match = null;
    private NextType _next_type = NextType.NONE;

    private enum NextType {
        FILE, HUNK_HEADER, HUNK, NONE
    }

    public DiffScanner(InputStream source, Path root) {
        _sc = new Scanner(source);
        _root = root;
    }

    public boolean hasNextFile() {
        if (_next_type == NextType.FILE) {
            return true;
        } else if (_next_type == NextType.NONE) {
            try {
                _sc.skip(_FILE_REGEX);
            } catch (NoSuchElementException nsee) {
                return false;
            }
            _next_match = _sc.match();
            _next_type = NextType.FILE;
            return true;
        } else {
            return false;
        }
    }

    public DiffFile nextFile() {
        if (_next_type == NextType.NONE) hasNextFile();
        if (_next_type == NextType.FILE) {
            Path a_path = _root.resolve(Paths.get(_next_match.group(1)));
            Path b_path = _root.resolve(Paths.get(_next_match.group(2)));
            DiffFile df = new DiffFile(a_path, b_path);
            resetNext();
            return df;
        } else {
            throw new InputMismatchException();
        }
    }

    public boolean hasNextHunkHeader() {
        if (_next_type == NextType.HUNK_HEADER) {
            return true;
        } else if (_next_type == NextType.NONE) {
            try {
                _sc.skip(_HUNK_HEADER_REGEX);
            } catch (NoSuchElementException nsee) {
                return false;
            }
            _next_match = _sc.match();
            _next_type = NextType.HUNK_HEADER;
            return true;
        } else {
            return false;
        }
    }

    public DiffHunkHeader nextHunkHeader() {
        int r_start, i_start;

        if (_next_type == NextType.NONE) hasNextHunkHeader();
        if (_next_type == NextType.HUNK_HEADER) {
            r_start = Integer.parseInt(_next_match.group(1));
            i_start = Integer.parseInt(_next_match.group(2));
            if (r_start > 0) r_start -= 1;
            if (i_start > 0) i_start -= 1;
            DiffHunkHeader dbh = new DiffHunkHeader(r_start, i_start);
            resetNext();
            return dbh;
        } else {
            throw new InputMismatchException();
        }
    }

    public boolean hasNextHunk() {
        if (_next_type == NextType.HUNK) {
            return true;
        } else if (_next_type == NextType.NONE) {
            try {
                _sc.skip(_HUNK_REGEX);
            } catch (NoSuchElementException nsee) {
                return false;
            }
            _next_match = _sc.match();
            _next_type = NextType.HUNK;
            return true;
        } else {
            return false;
        }
    }

    public DiffHunk nextHunk() {
        int blank_count, remove_count, insert_count;
        if (_next_type == NextType.NONE) hasNextHunkHeader();
        if (_next_type == NextType.HUNK) {
            blank_count = getLineCount(_next_match.group(1));
            remove_count = getLineCount(_next_match.group(2));
            insert_count = getLineCount(_next_match.group(3));
            DiffHunk db = new DiffHunk(blank_count, remove_count, blank_count, insert_count);
            resetNext();
            return db;
        } else {
            throw new InputMismatchException();
        }
    }

    public boolean hasNext() {
        return _sc.hasNext();
    }

    // Helper function to count the number of lines in a string
    // NOTE: Newlines are expected to be at the end, so the last newline will be disregarded
    private int getLineCount(String s) {
        if (s.isEmpty()) return 0;
        Matcher m = Pattern.compile("\r\n|\r|\n").matcher(s);
        int lines = 1;
        if (m.find()) lines = 1;
        while (m.find()) lines += 1;
        return lines;
    }

    // Clears the _next_match and _next_type variables
    private void resetNext() {
        _next_type = NextType.NONE;
        _next_match = null;
    }

    public void close() {
        _sc.close();
    }

}
