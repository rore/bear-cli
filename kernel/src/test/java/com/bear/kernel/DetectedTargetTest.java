package com.bear.kernel;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.TargetId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DetectedTargetTest {

    @Test
    void supportedFactory() {
        DetectedTarget result = DetectedTarget.supported(TargetId.JVM, "found build file");

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.JVM, result.targetId());
        assertEquals("found build file", result.reason());
    }

    @Test
    void unsupportedFactory() {
        DetectedTarget result = DetectedTarget.unsupported(TargetId.NODE, "not supported yet");

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.NODE, result.targetId());
        assertEquals("not supported yet", result.reason());
    }

    @Test
    void noneFactory() {
        DetectedTarget result = DetectedTarget.none();

        assertEquals(DetectionStatus.NONE, result.status());
        assertNull(result.targetId());
    }
}
