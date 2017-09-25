package net.exfalso.qont;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.Lists;
import kotlin.ranges.IntRange;
import kotlin.streams.StreamsKt;
import org.junit.Test;

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyList;
import static net.exfalso.monad.list.ListMonad.amb;
import static net.exfalso.monad.list.ListMonad.guard;
import static net.exfalso.qont.Qont.*;

public class JavaApiTest {
    @Test
    @Suspendable
    public void shiftResetWorks() throws SuspendExecution, InterruptedException {
        localFiber(() -> {


            /**
             *
             * reset0
             * shift0
             * YIELD
             * reset1
             * .. create continuation ..
             * .. start iteration ..
             *  cont0
             *  YIELD
             *  (cont1)
             *  (reset0) -> exec stack: reset, cont, reset
             *  shift1
             *  .. first item returns ..
             *  .. call guard ..
             *  shift0
             *  YIELD
             *  reset1
             *  .. create continuation (second) ..
             *  .. start iteration ..
             *   .. no elements ..
             *  .. end iteration ..
             *  reset1 returns with []
             *  cont1 returns with []
             * .. next iteration
             *  cont0
             *  YIELD
             *  cont1
             *  reset0
             *  shift1
             *  .. second item returns ..
             */

            List<Integer> evens = reset(() -> {
                Integer a = amb(new IntRange(1, 100));
                guard(a % 2 == 0);
                return Lists.newArrayList(a);
            });
            System.out.println(evens);

            Integer x = reset(() -> {
                Integer a = shift((SuspendableFunction<Integer, Integer> cont) -> cont.invoke(1) + cont.invoke(2));
                Integer b = shift((SuspendableFunction<Integer, Integer> cont) -> cont.invoke(3) + cont.invoke(4));
                return a + b;
            });
            System.out.println(x);

            return 0;
        });
    }
}
