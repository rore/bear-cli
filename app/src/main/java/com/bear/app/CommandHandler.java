package com.bear.app;

import java.io.PrintStream;

@FunctionalInterface
interface CommandHandler {
    int handle(String[] args, PrintStream out, PrintStream err);
}
