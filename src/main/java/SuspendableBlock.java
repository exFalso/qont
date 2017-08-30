import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;

@FunctionalInterface
public interface SuspendableBlock<A, R> extends java.io.Serializable {
    @Suspendable
    R execute(A arg0) throws SuspendExecution, InterruptedException;
}
