package net.exfalso.monad.list;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.Lists;
import net.exfalso.qont.Qont;
import net.exfalso.qont.SuspendableFunction;

import java.util.ArrayList;
import java.util.List;

public class Amb<A> {

    @Suspendable
    public static <A> List<A> _do(SuspendableFunction<Amb<A>, List<A>> block) throws InterruptedException, SuspendExecution {
        return Qont.reset(() -> block.invoke(new Amb<>()));
    }

    @Suspendable
    public <B> B bind(List<B> list) throws InterruptedException, SuspendExecution {
        return Qont.<B, List<A>>shift((continuation) -> {
            ArrayList<A> result = new ArrayList<>();
            for (B element : list) {
                result.addAll(continuation.invoke(element));
            }
            return result;
        });
    }

    private static List<Void> singletonList = Lists.newArrayList((Void) null);
    private static List<Void> emptyList = Lists.newArrayList();
    @Suspendable
    public void guard(boolean condition) throws SuspendExecution, InterruptedException {
        bind(condition ? singletonList : emptyList);
    }
}
