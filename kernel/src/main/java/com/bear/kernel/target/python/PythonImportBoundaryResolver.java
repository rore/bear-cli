package com.bear.kernel.target.python;

import com.bear.kernel.target.node.BoundaryDecision;

import java.nio.file.Path;
import java.util.Set;

/**
 * Resolves Python import statements and determines if they violate block boundaries.
 * 
 * Resolution algorithm:
 * 1. Relative imports → resolve lexically from importing file
 *    - BEAR-generated → ALLOWED
 *    - Same block root → ALLOWED
 *    - _shared → ALLOWED (unless _shared imports block → FAIL(SHARED_IMPORTS_BLOCK))
 *    - All other → FAIL(BOUNDARY_BYPASS)
 * 
 * 2. Absolute imports → check module type
 *    - Python stdlib → ALLOWED (Phase P: allow stdlib)
 *    - BEAR-generated → ALLOWED
 *    - Third-party → FAIL(THIRD_PARTY_IMPORT)
 */
public class PythonImportBoundaryResolver {

    // Python 3.12+ standard library modules (common subset)
    // Full list: https://docs.python.org/3/py-modindex.html
    private static final Set<String> STDLIB_MODULES = Set.of(
        // Built-in modules
        "abc", "aifc", "argparse", "array", "ast", "asynchat", "asyncio", "asyncore",
        "atexit", "audioop", "base64", "bdb", "binascii", "bisect", "builtins",
        "bz2", "calendar", "cgi", "cgitb", "chunk", "cmath", "cmd", "code", "codecs",
        "codeop", "collections", "colorsys", "compileall", "concurrent", "configparser",
        "contextlib", "contextvars", "copy", "copyreg", "crypt", "csv", "ctypes",
        "curses", "dataclasses", "datetime", "dbm", "decimal", "difflib", "dis",
        "distutils", "doctest", "email", "encodings", "enum", "errno", "faulthandler",
        "fcntl", "filecmp", "fileinput", "fnmatch", "fractions", "ftplib", "functools",
        "gc", "getopt", "getpass", "gettext", "glob", "graphlib", "grp", "gzip",
        "hashlib", "heapq", "hmac", "html", "http", "idlelib", "imaplib", "imghdr",
        "imp", "importlib", "inspect", "io", "ipaddress", "itertools", "json",
        "keyword", "lib2to3", "linecache", "locale", "logging", "lzma", "mailbox",
        "mailcap", "marshal", "math", "mimetypes", "mmap", "modulefinder", "msilib",
        "msvcrt", "multiprocessing", "netrc", "nis", "nntplib", "numbers", "operator",
        "optparse", "os", "ossaudiodev", "pathlib", "pdb", "pickle", "pickletools",
        "pipes", "pkgutil", "platform", "plistlib", "poplib", "posix", "posixpath",
        "pprint", "profile", "pstats", "pty", "pwd", "py_compile", "pyclbr", "pydoc",
        "queue", "quopri", "random", "re", "readline", "reprlib", "resource", "rlcompleter",
        "runpy", "sched", "secrets", "select", "selectors", "shelve", "shlex", "shutil",
        "signal", "site", "smtpd", "smtplib", "sndhdr", "socket", "socketserver",
        "spwd", "sqlite3", "ssl", "stat", "statistics", "string", "stringprep",
        "struct", "subprocess", "sunau", "symtable", "sys", "sysconfig", "syslog",
        "tabnanny", "tarfile", "telnetlib", "tempfile", "termios", "test", "textwrap",
        "threading", "time", "timeit", "tkinter", "token", "tokenize", "tomllib",
        "trace", "traceback", "tracemalloc", "tty", "turtle", "turtledemo", "types",
        "typing", "typing_extensions", "unicodedata", "unittest", "urllib", "uu",
        "uuid", "venv", "warnings", "wave", "weakref", "webbrowser", "winreg",
        "winsound", "wsgiref", "xdrlib", "xml", "xmlrpc", "zipapp", "zipfile",
        "zipimport", "zlib", "zoneinfo"
    );

    /**
     * Resolves an import statement and determines if it violates block boundaries.
     * 
     * @param importingFile The Python file containing the import
     * @param moduleName The module being imported (e.g., "os", ".submodule", "..parent")
     * @param isRelative True if this is a relative import (starts with . or ..)
     * @param governedRoots Set of governed root directories
     * @param projectRoot The project root directory
     * @return BoundaryDecision indicating if the import is allowed or should fail
     */
    public BoundaryDecision resolve(Path importingFile, String moduleName, boolean isRelative,
                                     Set<Path> governedRoots, Path projectRoot) {
        if (isRelative) {
            return resolveRelativeImport(importingFile, moduleName, governedRoots, projectRoot);
        } else {
            return resolveAbsoluteImport(moduleName, projectRoot);
        }
    }

    /**
     * Resolves a relative import (starts with . or ..).
     */
    private BoundaryDecision resolveRelativeImport(Path importingFile, String moduleName,
                                                     Set<Path> governedRoots, Path projectRoot) {
        // Resolve the import lexically from the importing file's directory
        Path resolved = resolvePythonRelativeImport(importingFile, moduleName);

        // Check if resolved path is within BEAR-generated directory
        Path generatedDir = projectRoot.resolve("build/generated/bear");
        if (resolved.startsWith(generatedDir)) {
            return BoundaryDecision.allowed();
        }

        // Find the governed root of the importing file
        Path importingRoot = findGovernedRoot(importingFile, governedRoots);
        if (importingRoot == null) {
            // Importing file is not in a governed root (shouldn't happen in normal flow)
            return BoundaryDecision.fail("BOUNDARY_BYPASS");
        }

        // Check if resolved path is within the same governed root
        if (resolved.startsWith(importingRoot)) {
            return BoundaryDecision.allowed();
        }

        // Check _shared boundary rules
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        
        // If importing from _shared, it must not import block roots
        if (importingFile.startsWith(sharedRoot)) {
            if (!resolved.startsWith(sharedRoot) && !resolved.startsWith(generatedDir)) {
                return BoundaryDecision.fail("SHARED_IMPORTS_BLOCK");
            }
            return BoundaryDecision.allowed();
        }

        // Block importing _shared is allowed
        if (resolved.startsWith(sharedRoot)) {
            return BoundaryDecision.allowed();
        }

        // All other cases are boundary bypass
        return BoundaryDecision.fail("BOUNDARY_BYPASS");
    }

    /**
     * Resolves an absolute import (no leading dots).
     */
    private BoundaryDecision resolveAbsoluteImport(String moduleName, Path projectRoot) {
        // Check if it's a Python standard library module
        if (isStdlibModule(moduleName)) {
            return BoundaryDecision.allowed();
        }

        // Check if it's a BEAR-generated module
        if (isBearGeneratedModule(moduleName, projectRoot)) {
            return BoundaryDecision.allowed();
        }

        // All other absolute imports are third-party packages (forbidden in inner profile)
        return BoundaryDecision.fail("THIRD_PARTY_IMPORT");
    }

    /**
     * Resolves a Python relative import lexically.
     * 
     * Python relative import rules:
     * - "." means current package
     * - ".." means parent package
     * - "...module" means grandparent.module
     * 
     * @param importingFile The file containing the import
     * @param moduleName The relative module name (e.g., ".", "..", ".submodule", "..parent")
     * @return The resolved absolute path
     */
    private Path resolvePythonRelativeImport(Path importingFile, String moduleName) {
        // Start from the importing file's directory
        Path current = importingFile.getParent();
        if (current == null) {
            current = Path.of(".");
        }

        // Count leading dots to determine how many levels to go up
        int dotCount = 0;
        while (dotCount < moduleName.length() && moduleName.charAt(dotCount) == '.') {
            dotCount++;
        }

        // Go up (dotCount - 1) levels (one dot means current package, two dots means parent, etc.)
        for (int i = 1; i < dotCount; i++) {
            current = current.getParent();
            if (current == null) {
                current = Path.of(".");
                break;
            }
        }

        // Extract the module path after the dots
        String remainingPath = moduleName.substring(dotCount);
        if (!remainingPath.isEmpty()) {
            // Convert Python module notation to file path (e.g., "submodule.utils" → "submodule/utils")
            String filePath = remainingPath.replace('.', '/');
            current = current.resolve(filePath);
        }

        return current.normalize();
    }

    /**
     * Checks if a module name is a Python standard library module.
     */
    private boolean isStdlibModule(String moduleName) {
        // Extract the top-level module name (e.g., "os.path" → "os")
        String topLevel = moduleName.split("\\.")[0];
        return STDLIB_MODULES.contains(topLevel);
    }

    /**
     * Checks if a module name refers to a BEAR-generated module.
     * BEAR-generated modules are under "build.generated.bear.*"
     */
    private boolean isBearGeneratedModule(String moduleName, Path projectRoot) {
        // BEAR-generated modules start with "build.generated.bear"
        return moduleName.startsWith("build.generated.bear");
    }

    /**
     * Finds the governed root that contains the given file.
     */
    private Path findGovernedRoot(Path file, Set<Path> governedRoots) {
        for (Path root : governedRoots) {
            if (file.startsWith(root)) {
                return root;
            }
        }
        return null;
    }
}
