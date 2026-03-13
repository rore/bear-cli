package com.bear.kernel.target;

import java.nio.file.Path;

public interface TargetDetector {
    DetectedTarget detect(Path projectRoot);
}
