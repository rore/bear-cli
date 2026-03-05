package com.bear.app;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AgentCommandContextTestSupport {
    private AgentCommandContextTestSupport() {
    }

    static AgentCommandContext parseCommandContext(String commandLine) {
        String[] args = tokenize(commandLine);
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        AgentCommandContext context = BearCliCommandHandlers.parseAgentCommandContext(
            args,
            new PrintStream(stderrBytes)
        );
        if (context == null) {
            throw new IllegalStateException("Failed to parse command: " + commandLine + "\n" + stderrBytes.toString(StandardCharsets.UTF_8));
        }
        return context;
    }

    static String[] tokenize(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return new String[0];
        }
        ArrayList<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens.toArray(String[]::new);
    }

    static void assertEquivalent(AgentCommandContext expected, AgentCommandContext actual) {
        AgentCommandContextEquivalence.ComparisonResult comparison = AgentCommandContextEquivalence.compare(expected, actual);
        org.junit.jupiter.api.Assertions.assertTrue(comparison.equivalent(), comparison.summary());
    }

    static String firstCommand(String json) {
        int commandsIndex = json.indexOf("\"commands\":[");
        if (commandsIndex < 0) {
            return null;
        }
        int firstQuote = json.indexOf('"', commandsIndex + "\"commands\":[".length());
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return json.substring(firstQuote + 1, secondQuote);
    }

    static String firstRerunCommand(String json) {
        List<String> commands = extractCommands(json);
        for (String command : commands) {
            if (command.startsWith("bear check") || command.startsWith("bear pr-check")) {
                return command;
            }
        }
        return commands.isEmpty() ? null : commands.get(0);
    }

    private static List<String> extractCommands(String json) {
        Matcher matcher = Pattern.compile("\"commands\":\\[(.*?)\\]").matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return List.of();
        }
        String body = matcher.group(1);
        ArrayList<String> commands = new ArrayList<>();
        Matcher commandMatcher = Pattern.compile("\"((?:\\\\\"|[^\"])*)\"").matcher(body);
        while (commandMatcher.find()) {
            commands.add(commandMatcher.group(1).replace("\\\"", "\""));
        }
        return List.copyOf(commands);
    }
}
