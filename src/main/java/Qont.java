import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;

public class Qont {

    @Suspendable
    public static <R> R reset(SuspendableBlock<ResetToken<R>, R> block) throws SuspendExecution {
        return ShiftResetKt.reset_(block);
    }

    @Suspendable
    public static <A, R> A shift(ResetToken<R> token, SuspendableBlock<SuspendableBlock<A, R>, R> block) throws SuspendExecution {
        return ShiftResetKt.shift_(token, block);
    }

}
