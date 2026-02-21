package com.bear.kernel.target;

import com.bear.kernel.ir.BearIr;

import java.io.IOException;
import java.nio.file.Path;

public interface Target {
    void compile(BearIr ir, Path projectRoot, String blockKey) throws IOException;
}
