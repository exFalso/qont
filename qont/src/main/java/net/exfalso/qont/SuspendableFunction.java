package net.exfalso.qont;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;

@FunctionalInterface
public interface SuspendableFunction<A, R> extends java.io.Serializable {
    @Suspendable
    R invoke(A argument) throws SuspendExecution, InterruptedException;
}
