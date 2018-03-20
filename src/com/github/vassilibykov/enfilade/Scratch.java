package com.github.vassilibykov.enfilade;

public class Scratch {
    public static void main(String[] args) {
        int n = 35;
        for (int i = 0; i < 20; i++) fib(n);
        long start = System.nanoTime();
        int result = fib(n);
        long elapsed = System.nanoTime() - start;
        System.out.format("fibonacci(%s) = %s in %s ms", n, result, elapsed / 1_000_000L);

    }

    private static int fib(int n) {
        if (n < 2) {
            return 1;
        } else {
            return fib(n - 1) + fib(n - 2);
        }
    }
}
