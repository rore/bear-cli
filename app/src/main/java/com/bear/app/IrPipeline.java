package com.bear.app;

import com.bear.kernel.ir.BearIr;

import java.io.IOException;
import java.nio.file.Path;

interface IrPipeline {
    BearIr parseValidate(Path path) throws IOException;

    BearIr parseValidateNormalize(Path path) throws IOException;
}
