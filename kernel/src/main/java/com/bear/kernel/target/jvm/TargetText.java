package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
final class TargetText {
    private TargetText() {
    }

    static String normalizeLf(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }
}

