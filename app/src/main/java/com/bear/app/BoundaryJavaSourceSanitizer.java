package com.bear.app;

final class BoundaryJavaSourceSanitizer {
    private BoundaryJavaSourceSanitizer() {
    }

    static String stripJavaCommentsStringsAndChars(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                    out.append(c);
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    out.append(' ');
                    out.append(' ');
                    i++;
                } else if (c == '\n' || c == '\r') {
                    out.append(c);
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (inString) {
                if (!escaped && c == '\"') {
                    inString = false;
                }
                if (!escaped && c == '\\') {
                    escaped = true;
                } else {
                    escaped = false;
                }
                out.append(c == '\n' || c == '\r' ? c : ' ');
                continue;
            }
            if (inChar) {
                if (!escaped && c == '\'') {
                    inChar = false;
                }
                if (!escaped && c == '\\') {
                    escaped = true;
                } else {
                    escaped = false;
                }
                out.append(c == '\n' || c == '\r' ? c : ' ');
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                out.append(' ');
                out.append(' ');
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                out.append(' ');
                out.append(' ');
                i++;
                continue;
            }
            if (c == '\"') {
                inString = true;
                escaped = false;
                out.append(' ');
                continue;
            }
            if (c == '\'') {
                inChar = true;
                escaped = false;
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    static String stripJavaCommentsPreserveStringsAndChars(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                    out.append(c);
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    out.append(' ');
                    out.append(' ');
                    i++;
                } else if (c == '\n' || c == '\r') {
                    out.append(c);
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (!escaped && c == '\"') {
                    inString = false;
                }
                if (!escaped && c == '\\') {
                    escaped = true;
                } else {
                    escaped = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (!escaped && c == '\'') {
                    inChar = false;
                }
                if (!escaped && c == '\\') {
                    escaped = true;
                } else {
                    escaped = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                out.append(' ');
                out.append(' ');
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                out.append(' ');
                out.append(' ');
                i++;
                continue;
            }
            if (c == '\"') {
                inString = true;
                escaped = false;
                out.append(c);
                continue;
            }
            if (c == '\'') {
                inChar = true;
                escaped = false;
                out.append(c);
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
