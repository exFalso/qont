package net.exfalso.qont;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.Lists;
import net.exfalso.monad.list.Amb;
import org.junit.Test;

import java.util.List;

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

            List<Integer> evens = Amb._do((amb) -> {
                Integer a = amb.bind(Lists.newArrayList(1, 2, 3, 4, 5, 6));
                amb.guard(a % 2 == 0);
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
