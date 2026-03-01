package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidator;

import java.io.IOException;
import java.nio.file.Path;

final class DefaultIrPipeline implements IrPipeline {
    @Override
    public BearIr parseValidate(Path path) throws IOException {
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIr ir = parser.parse(path);
        validator.validate(ir);
        return ir;
    }

    @Override
    public BearIr parseValidateNormalize(Path path) throws IOException {
        BearIrNormalizer normalizer = new BearIrNormalizer();
        return normalizer.normalize(parseValidate(path));
    }
}
