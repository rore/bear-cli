package com.bear.kernel.target;

import java.util.List;

final class JvmStructuralRenderUnits {
    private JvmStructuralRenderUnits() {
    }

    static String renderStructuralDirectionTest(
        String generatedHeader,
        String packageName,
        String wrapperClassName,
        String requestClassName,
        String resultClassName,
        String sharedLogicClassName,
        String sharedLogicMethodName,
        String blockKey,
        List<String> expectedOfParams,
        List<String> expectedLogicExecuteParams
    ) {
        return generatedHeader
            + "package " + packageName + ";\n\n"
            + "import java.lang.reflect.Method;\n"
            + "import java.lang.reflect.Modifier;\n"
            + "import java.util.ArrayList;\n"
            + "import java.util.Collections;\n"
            + "import java.util.List;\n"
            + "import org.junit.jupiter.api.Test;\n"
            + "import static org.junit.jupiter.api.Assertions.fail;\n\n"
            + "final class " + wrapperClassName + "StructuralDirectionTest {\n"
            + "    private static final String BLOCK_KEY = " + javaString(blockKey) + ";\n"
            + "    private static final boolean STRICT = Boolean.getBoolean(\"bear.structural.tests.strict\");\n"
            + "    private static final String TEST_NAME = \"Direction\";\n"
            + "    private static final List<String> EXPECTED_OF_PARAMS = " + javaStringListLiteral(expectedOfParams) + ";\n"
            + "    private static final List<String> EXPECTED_LOGIC_EXECUTE_PARAMS = " + javaStringListLiteral(expectedLogicExecuteParams) + ";\n\n"
            + "    @Test\n"
            + "    void structuralDirectionEvidence() {\n"
            + "        List<String> mismatches = new ArrayList<>();\n"
            + "        expectSimpleName(" + wrapperClassName + ".class, " + javaString(wrapperClassName) + ", \"WRAPPER_CLASS_MISMATCH\", mismatches);\n"
            + "        expectSimpleName(" + requestClassName + ".class, " + javaString(requestClassName) + ", \"REQUEST_CLASS_MISMATCH\", mismatches);\n"
            + "        expectSimpleName(" + resultClassName + ".class, " + javaString(resultClassName) + ", \"RESULT_CLASS_MISMATCH\", mismatches);\n"
            + "        expectSimpleName(" + sharedLogicClassName + ".class, " + javaString(sharedLogicClassName) + ", \"LOGIC_CLASS_MISMATCH\", mismatches);\n"
            + "        Method ofMethod = findStaticMethod(" + wrapperClassName + ".class, \"of\", mismatches, \"WRAPPER_OF_MISSING\");\n"
            + "        if (ofMethod != null) {\n"
            + "            List<String> actualOfParams = parameterSimpleNames(ofMethod);\n"
            + "            if (!actualOfParams.equals(EXPECTED_OF_PARAMS)) {\n"
            + "                addMismatch(\n"
            + "                    mismatches,\n"
            + "                    \"WRAPPER_OF_PARAMS_MISMATCH\",\n"
            + "                    \"expected=\" + joinCsv(EXPECTED_OF_PARAMS) + \";actual=\" + joinCsv(actualOfParams)\n"
            + "                );\n"
            + "            }\n"
            + "            String actualReturnType = ofMethod.getReturnType().getSimpleName();\n"
            + "            if (!actualReturnType.equals(" + javaString(wrapperClassName) + ")) {\n"
            + "                addMismatch(\n"
            + "                    mismatches,\n"
            + "                    \"WRAPPER_OF_RETURN_MISMATCH\",\n"
            + "                    \"expected=" + wrapperClassName + ";actual=\" + actualReturnType\n"
            + "                );\n"
            + "            }\n"
            + "        }\n"
            + "        Method logicExecute = findMethodByName("
            + sharedLogicClassName
            + ".class, "
            + javaString(sharedLogicMethodName)
            + ", mismatches, \"LOGIC_EXECUTE_MISSING\");\n"
            + "        if (logicExecute != null) {\n"
            + "            List<String> actualExecuteParams = parameterSimpleNames(logicExecute);\n"
            + "            if (!actualExecuteParams.equals(EXPECTED_LOGIC_EXECUTE_PARAMS)) {\n"
            + "                addMismatch(\n"
            + "                    mismatches,\n"
            + "                    \"LOGIC_EXECUTE_PARAMS_MISMATCH\",\n"
            + "                    \"expected=\" + joinCsv(EXPECTED_LOGIC_EXECUTE_PARAMS) + \";actual=\" + joinCsv(actualExecuteParams)\n"
            + "                );\n"
            + "            }\n"
            + "            String actualResultType = logicExecute.getReturnType().getSimpleName();\n"
            + "            if (!actualResultType.equals(" + javaString(resultClassName) + ")) {\n"
            + "                addMismatch(\n"
            + "                    mismatches,\n"
            + "                    \"LOGIC_EXECUTE_RETURN_MISMATCH\",\n"
            + "                    \"expected=" + resultClassName + ";actual=\" + actualResultType\n"
            + "                );\n"
            + "            }\n"
            + "        }\n"
            + "        emitAndMaybeFail(mismatches);\n"
            + "    }\n\n"
            + "    private static Method findStaticMethod(Class<?> type, String methodName, List<String> mismatches, String missingKind) {\n"
            + "        List<Method> candidates = new ArrayList<>();\n"
            + "        for (Method method : type.getDeclaredMethods()) {\n"
            + "            if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())) {\n"
            + "                candidates.add(method);\n"
            + "            }\n"
            + "        }\n"
            + "        if (candidates.isEmpty()) {\n"
            + "            addMismatch(mismatches, missingKind, type.getSimpleName() + \"#\" + methodName + \"()\");\n"
            + "            return null;\n"
            + "        }\n"
            + "        candidates.sort((left, right) -> methodSignature(left).compareTo(methodSignature(right)));\n"
            + "        if (candidates.size() > 1) {\n"
            + "            addMismatch(mismatches, \"WRAPPER_OF_MULTIPLE\", \"count=\" + candidates.size());\n"
            + "        }\n"
            + "        return candidates.get(0);\n"
            + "    }\n\n"
            + "    private static Method findMethodByName(Class<?> type, String methodName, List<String> mismatches, String missingKind) {\n"
            + "        List<Method> candidates = new ArrayList<>();\n"
            + "        for (Method method : type.getDeclaredMethods()) {\n"
            + "            if (method.getName().equals(methodName)) {\n"
            + "                candidates.add(method);\n"
            + "            }\n"
            + "        }\n"
            + "        if (candidates.isEmpty()) {\n"
            + "            addMismatch(mismatches, missingKind, type.getSimpleName() + \"#\" + methodName + \"()\");\n"
            + "            return null;\n"
            + "        }\n"
            + "        candidates.sort((left, right) -> methodSignature(left).compareTo(methodSignature(right)));\n"
            + "        if (candidates.size() > 1) {\n"
            + "            addMismatch(mismatches, \"LOGIC_EXECUTE_MULTIPLE\", \"count=\" + candidates.size());\n"
            + "        }\n"
            + "        return candidates.get(0);\n"
            + "    }\n\n"
            + "    private static void expectSimpleName(Class<?> type, String expectedSimpleName, String kind, List<String> mismatches) {\n"
            + "        String actualSimpleName = type.getSimpleName();\n"
            + "        if (!expectedSimpleName.equals(actualSimpleName)) {\n"
            + "            addMismatch(\n"
            + "                mismatches,\n"
            + "                kind,\n"
            + "                \"class=\" + type.getName() + \";expected=\" + expectedSimpleName + \";actual=\" + actualSimpleName\n"
            + "            );\n"
            + "        }\n"
            + "    }\n\n"
            + "    private static List<String> parameterSimpleNames(Method method) {\n"
            + "        ArrayList<String> names = new ArrayList<>();\n"
            + "        for (Class<?> parameterType : method.getParameterTypes()) {\n"
            + "            names.add(parameterType.getSimpleName());\n"
            + "        }\n"
            + "        return names;\n"
            + "    }\n\n"
            + "    private static String methodSignature(Method method) {\n"
            + "        return method.getDeclaringClass().getSimpleName()\n"
            + "            + \"#\"\n"
            + "            + method.getName()\n"
            + "            + \"(\"\n"
            + "            + joinCsv(parameterSimpleNames(method))\n"
            + "            + \")\";\n"
            + "    }\n\n"
            + "    private static void addMismatch(List<String> mismatches, String kind, String detail) {\n"
            + "        String normalizedDetail = detail == null ? \"\" : detail.replace(\"\\r\", \"\").replace(\"\\n\", \"\");\n"
            + "        mismatches.add(kind + \"|\" + normalizedDetail);\n"
            + "    }\n\n"
            + "    private static String joinCsv(List<String> values) {\n"
            + "        return String.join(\",\", values);\n"
            + "    }\n\n"
            + "    private static void emitAndMaybeFail(List<String> mismatches) {\n"
            + "        Collections.sort(mismatches);\n"
            + "        for (String mismatch : mismatches) {\n"
            + "            int sep = mismatch.indexOf('|');\n"
            + "            String kind = sep < 0 ? \"UNKNOWN\" : mismatch.substring(0, sep);\n"
            + "            String detail = sep < 0 ? \"\" : mismatch.substring(sep + 1);\n"
            + "            System.out.println(\n"
            + "                \"BEAR_STRUCTURAL_SIGNAL|blockKey=\"\n"
            + "                    + BLOCK_KEY\n"
            + "                    + \"|test=\"\n"
            + "                    + TEST_NAME\n"
            + "                    + \"|kind=\"\n"
            + "                    + kind\n"
            + "                    + \"|detail=\"\n"
            + "                    + detail\n"
            + "            );\n"
            + "        }\n"
            + "        if (STRICT && !mismatches.isEmpty()) {\n"
            + "            fail(\"BEAR_STRUCTURAL_STRICT_FAILURE|\" + TEST_NAME + \"|\" + String.join(\"\\n\", mismatches));\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    }

    static String renderStructuralReachTest(
        String generatedHeader,
        String packageName,
        String blockName,
        String blockKey,
        String expectedMapSection
    ) {
        return generatedHeader
            + "package " + packageName + ";\n\n"
            + "import java.lang.reflect.Method;\n"
            + "import java.util.ArrayList;\n"
            + "import java.util.Collections;\n"
            + "import java.util.LinkedHashMap;\n"
            + "import java.util.List;\n"
            + "import java.util.Map;\n"
            + "import org.junit.jupiter.api.Test;\n"
            + "import static org.junit.jupiter.api.Assertions.fail;\n\n"
            + "final class " + blockName + "StructuralReachTest {\n"
            + "    private static final String BLOCK_KEY = " + javaString(blockKey) + ";\n"
            + "    private static final boolean STRICT = Boolean.getBoolean(\"bear.structural.tests.strict\");\n"
            + "    private static final String TEST_NAME = \"Reach\";\n\n"
            + "    @Test\n"
            + "    void structuralReachEvidence() {\n"
            + "        List<String> mismatches = new ArrayList<>();\n"
            + "        Map<String, List<String>> expected = expectedPortMethodSignatures();\n"
            + "        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {\n"
            + "            String interfaceSimple = entry.getKey();\n"
            + "            List<String> expectedMethods = entry.getValue();\n"
            + "            Class<?> portClass;\n"
            + "            try {\n"
            + "                portClass = Class.forName(" + javaString(packageName + ".") + " + interfaceSimple);\n"
            + "            } catch (ClassNotFoundException e) {\n"
            + "                addMismatch(mismatches, \"PORT_INTERFACE_MISSING\", interfaceSimple);\n"
            + "                continue;\n"
            + "            }\n"
            + "            if (!portClass.isInterface()) {\n"
            + "                addMismatch(mismatches, \"PORT_INTERFACE_NOT_INTERFACE\", interfaceSimple);\n"
            + "                continue;\n"
            + "            }\n"
            + "            List<String> actualMethods = declaredMethodSignatures(portClass);\n"
            + "            if (!actualMethods.equals(expectedMethods)) {\n"
            + "                addMismatch(\n"
            + "                    mismatches,\n"
            + "                    \"PORT_METHOD_SET_MISMATCH\",\n"
            + "                    \"port=\" + interfaceSimple + \";expected=\" + joinCsv(expectedMethods) + \";actual=\" + joinCsv(actualMethods)\n"
            + "                );\n"
            + "            }\n"
            + "        }\n"
            + "        emitAndMaybeFail(mismatches);\n"
            + "    }\n\n"
            + "    private static Map<String, List<String>> expectedPortMethodSignatures() {\n"
            + "        LinkedHashMap<String, List<String>> expected = new LinkedHashMap<>();\n"
            + expectedMapSection
            + "        return expected;\n"
            + "    }\n\n"
            + "    private static List<String> declaredMethodSignatures(Class<?> type) {\n"
            + "        ArrayList<String> signatures = new ArrayList<>();\n"
            + "        for (Method method : type.getDeclaredMethods()) {\n"
            + "            signatures.add(methodSignature(method));\n"
            + "        }\n"
            + "        Collections.sort(signatures);\n"
            + "        return signatures;\n"
            + "    }\n\n"
            + "    private static String methodSignature(Method method) {\n"
            + "        ArrayList<String> paramSimpleNames = new ArrayList<>();\n"
            + "        for (Class<?> parameterType : method.getParameterTypes()) {\n"
            + "            paramSimpleNames.add(parameterType.getSimpleName());\n"
            + "        }\n"
            + "        return method.getDeclaringClass().getSimpleName()\n"
            + "            + \"#\"\n"
            + "            + method.getName()\n"
            + "            + \"(\"\n"
            + "            + joinCsv(paramSimpleNames)\n"
            + "            + \")\";\n"
            + "    }\n\n"
            + "    private static void addMismatch(List<String> mismatches, String kind, String detail) {\n"
            + "        String normalizedDetail = detail == null ? \"\" : detail.replace(\"\\r\", \"\").replace(\"\\n\", \"\");\n"
            + "        mismatches.add(kind + \"|\" + normalizedDetail);\n"
            + "    }\n\n"
            + "    private static String joinCsv(List<String> values) {\n"
            + "        return String.join(\",\", values);\n"
            + "    }\n\n"
            + "    private static void emitAndMaybeFail(List<String> mismatches) {\n"
            + "        Collections.sort(mismatches);\n"
            + "        for (String mismatch : mismatches) {\n"
            + "            int sep = mismatch.indexOf('|');\n"
            + "            String kind = sep < 0 ? \"UNKNOWN\" : mismatch.substring(0, sep);\n"
            + "            String detail = sep < 0 ? \"\" : mismatch.substring(sep + 1);\n"
            + "            System.out.println(\n"
            + "                \"BEAR_STRUCTURAL_SIGNAL|blockKey=\"\n"
            + "                    + BLOCK_KEY\n"
            + "                    + \"|test=\"\n"
            + "                    + TEST_NAME\n"
            + "                    + \"|kind=\"\n"
            + "                    + kind\n"
            + "                    + \"|detail=\"\n"
            + "                    + detail\n"
            + "            );\n"
            + "        }\n"
            + "        if (STRICT && !mismatches.isEmpty()) {\n"
            + "            fail(\"BEAR_STRUCTURAL_STRICT_FAILURE|\" + TEST_NAME + \"|\" + String.join(\"\\n\", mismatches));\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    }

    private static String javaStringListLiteral(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "java.util.List.of()";
        }
        StringBuilder literal = new StringBuilder("java.util.List.of(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                literal.append(", ");
            }
            literal.append(javaString(values.get(i)));
        }
        literal.append(")");
        return literal.toString();
    }

    private static String javaString(String value) {
        StringBuilder out = new StringBuilder();
        out.append("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else {
                out.append(c);
            }
        }
        out.append("\"");
        return out.toString();
    }
}
