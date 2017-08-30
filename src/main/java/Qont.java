import co.paralleluniverse.fibers.Suspendable;

public class Qont {

    @Suspendable
    public static <R> R reset(SuspendableBlock<ResetToken<R>, R> block) {
        return ShiftResetKt.reset_(block);
    }

    @Suspendable
    public static <A, R> A shift(ResetToken<R> token, SuspendableBlock<SuspendableBlock<A, R>, R> block) {
        return ShiftResetKt.shift_(token, block);
    }

}
