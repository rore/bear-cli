package com.bear.kernel.target;

import java.util.List;

public record BlockPortBinding(
    String port,
    String targetBlock,
    List<String> targetOps,
    String portInterfaceFqcn,
    String expectedClientImplFqcn
) {
}

