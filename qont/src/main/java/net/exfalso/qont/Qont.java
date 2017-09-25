package net.exfalso.qont;

import co.paralleluniverse.fibers.*;
import co.paralleluniverse.strands.SuspendableCallable;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

public final class Qont {

    @Suspendable
    public static <R> R reset(SuspendableCallable<R> block) throws SuspendExecution, InterruptedException {
        if (block == block) {
            RESET_OFFSET.set(QuasarUtilities.getStackPointer(Stack.getStack()) - 1);
            return block.run();
        } else {
            Fiber.yield();
            REENTER_ON_YIELD.remove();
            ShiftData<R> shiftToken = (ShiftData<R>) SHIFT_DATA.get();
            return shiftToken.block.invoke(createContinuation(block, shiftToken.stackOffset));
        }
    }

    @Suspendable
    public static <A, R> A shift(SuspendableFunction<SuspendableFunction<A, R>, R> block) throws SuspendExecution, InterruptedException {
        if (block == block) {
            return jumpIntoResetWithShiftBlock(coerce(block));
        } else {
            Fiber.yield();
            REENTER_ON_YIELD.remove();
            return (A) SHIFT_RESULT.get();
        }
    }

    private static Stack gstack;
    public static <R> R localFiber(SuspendableCallable<R> yieldingBlock) throws InterruptedException {
        try {
            initialiseDummyFiber();
            Stack stack = Stack.getStack();
            gstack = stack;
            int stackPointer = QuasarUtilities.getStackPointer(stack);
            while (true) {
                try {
                    return yieldingBlock.run();
                } catch (SuspendExecution reenter) {
                    if (reenter == QuasarUtilities.yieldException && REENTER_ON_YIELD.get() == Boolean.TRUE) {
                        QuasarUtilities.setStackPointer(stack, stackPointer);
                    } else {
                        throw new RuntimeException(reenter);
                    }
                }
            }
        } finally {
            removeDummyFiber();
        }
    }

    public static void checkInstrumented() throws ClassNotFoundException {
        boolean isDebug = false;
        assert isDebug = true;
        if (!isDebug) return;

        StackTraceElement stackTraceElement = null;
        for (StackTraceElement element : new Exception().getStackTrace()) {
            if (!Objects.equals(element.getFileName(), "Qont.java") && element.getLineNumber() != 65535) {
                stackTraceElement = element;
                break;
            }
        }
        assert stackTraceElement != null;

        HashMap<String, Method> methods = new HashMap<>();
        for (Method method : Class.forName(stackTraceElement.getClassName()).getDeclaredMethods()) {
            methods.put(method.getName(), method);
        }
        Annotation instrumentedAnnotation = null;
        for (Annotation annotation : methods.get(stackTraceElement.getMethodName()).getAnnotations()) {
            if (annotation instanceof Instrumented) {
                instrumentedAnnotation = annotation;
                break;
            }
        }
        System.out.println(stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + " " + instrumentedAnnotation);
    }

    @NotNull
    private static <R> ShiftContinuation<Object, R> createContinuation(SuspendableCallable<R> block, int shiftOffset) {
        Stack stack = Stack.getStack();
        int resetOffset = QuasarUtilities.getStackPointer(stack) - 1;
        long[] primitiveStack = QuasarUtilities.getPrimitiveStack(stack);
        Object[] objectStack = QuasarUtilities.getObjectStack(stack);
        long shiftRecord = primitiveStack[shiftOffset];
        long resetRecord = primitiveStack[resetOffset];
        int usedStackSize = shiftOffset + QuasarUtilities.getNumSlots(shiftRecord) + 1;
        long[] primitiveSubStack = Arrays.copyOfRange(primitiveStack, resetOffset, usedStackSize);
        Object[] objectSubStack = Arrays.copyOfRange(objectStack, resetOffset, usedStackSize);
        primitiveSubStack[shiftOffset - resetOffset] = QuasarUtilities.setEntry(shiftRecord, QuasarUtilities.getEntry(shiftRecord) + 1);
        primitiveSubStack[0] = QuasarUtilities.setPrevNumSlots(QuasarUtilities.setEntry(resetRecord, QuasarUtilities.getEntry(resetRecord) - 1), 0);
        for (int i = resetOffset; i < usedStackSize; i++) {
            objectStack[i] = null;
        }
        return new ShiftContinuation<>(block, primitiveSubStack, objectSubStack);
    }

    @Instrumented(methodOptimized = true)
    private static <A> A jumpIntoResetWithShiftBlock(SuspendableFunction<SuspendableFunction<Object, Object>, Object> block) throws SuspendExecution {
        Stack stack = Stack.getStack();
        long[] primitiveStack = QuasarUtilities.getPrimitiveStack(stack);
        int stackOffset = RESET_OFFSET.get();
        RESET_OFFSET.remove();
        long resetRecord = primitiveStack[stackOffset];
        primitiveStack[stackOffset] = QuasarUtilities.setEntry(resetRecord, QuasarUtilities.getEntry(resetRecord) + 1);
        SHIFT_DATA.set(new ShiftData<>(QuasarUtilities.getStackPointer(stack) - 1, block));
        REENTER_ON_YIELD.set(Boolean.TRUE);
        throw QuasarUtilities.yieldException;
    }

    private static void initialiseDummyFiber() {
        Fiber fiber = Fiber.currentFiber();
        if (fiber == null) {
            QuasarUtilities.getCurrentStrand().set(new DummyFiber(1));
        } else if (fiber instanceof DummyFiber) {
            ((DummyFiber) fiber).count++;
        }
    }

    private static void removeDummyFiber() {
        Fiber fiber = Fiber.currentFiber();
        if (fiber instanceof DummyFiber) {
            if (--((DummyFiber) fiber).count == 0) {
                QuasarUtilities.getCurrentStrand().set(null);
            }
        }
    }

    static ThreadLocal<Integer> RESET_OFFSET = new ThreadLocal<>();
    static ThreadLocal<Boolean> REENTER_ON_YIELD = new ThreadLocal<>();
    static ThreadLocal<ShiftData<Object>> SHIFT_DATA = new ThreadLocal<>();
    static ThreadLocal<Object> SHIFT_RESULT = new ThreadLocal<>();

    private static final class ShiftData<R> {
        int stackOffset;
        SuspendableFunction<SuspendableFunction<Object, R>, R> block;

        ShiftData(int stackOffset, SuspendableFunction<SuspendableFunction<Object, R>, R> block) {
            this.stackOffset = stackOffset;
            this.block = block;
        }
    }

    private static final class DummyFiber extends Fiber<Object> {
        int count;
        DummyFiber(int count) {
            this.count = count;
        }

        @Override
        @Suspendable
        public Object run() {
            throw new IllegalStateException("Shouldn't be called");
        }
    }

    private static <A, B> B coerce(A a) {
        return (B) a;
    }
}
