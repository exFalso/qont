package net.exfalso.qont;

import co.paralleluniverse.fibers.Instrumented;
import co.paralleluniverse.fibers.Stack;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.SuspendableCallable;

final class ShiftContinuation<A, R> implements SuspendableFunction<A, R> {
    private SuspendableCallable<R> block;
    private long[] shiftPrimitiveStack;
    private Object[] shiftObjectStack;

    ShiftContinuation(SuspendableCallable<R> block, long[] shiftPrimitiveStack, Object[] shiftObjectStack) {
        this.block = block;
        this.shiftPrimitiveStack = shiftPrimitiveStack;
        this.shiftObjectStack = shiftObjectStack;
    }

    @Override
    @Suspendable
    public R invoke(A argument) throws InterruptedException, SuspendExecution {
        if (argument == argument) {
            return jumpIntoResetWithContinuationPrepared(argument);
        } else {
            return Qont.reset(block);
        }
    }

    @Instrumented(methodOptimized = true)
    private R jumpIntoResetWithContinuationPrepared(A argument) throws SuspendExecution {
        Stack stack = Stack.getStack();
        long[] primitiveStack = QuasarUtilities.getPrimitiveStack(stack);
        Object[] objectStack = QuasarUtilities.getObjectStack(stack);
        int executeStackOffset = QuasarUtilities.getStackPointer(stack) - 1;
        int resetStackOffset = executeStackOffset + 1;
        if (executeStackOffset >= 0) {
            primitiveStack[executeStackOffset] = QuasarUtilities.setPrevNumSlots(QuasarUtilities.setNumSlots(QuasarUtilities.setEntry(0, 2), 0), 0);
        }
        Qont.SHIFT_RESULT.set(argument);
        Qont.RESET_OFFSET.set(resetStackOffset);

        int requiredSize = resetStackOffset + shiftPrimitiveStack.length;
        if (requiredSize >= primitiveStack.length) {
            int newStackSize = primitiveStack.length;
            while (newStackSize <= requiredSize) {
                newStackSize = newStackSize * 2;
            }
            long[] newPrimitiveStack = new long[newStackSize];
            Object[]newObjectStack = new Object[newStackSize];
            System.arraycopy(primitiveStack, 0, newPrimitiveStack, 0, primitiveStack.length);
            System.arraycopy(objectStack, 0, newObjectStack, 0, objectStack.length);
            System.arraycopy(shiftPrimitiveStack, 0, newPrimitiveStack, resetStackOffset, shiftPrimitiveStack.length);
            System.arraycopy(shiftObjectStack, 0, newObjectStack, resetStackOffset, shiftObjectStack.length);
            QuasarUtilities.setPrimitiveStack(stack, newPrimitiveStack);
            QuasarUtilities.setObjectStack(stack, newObjectStack);
        } else {
            System.arraycopy(shiftPrimitiveStack, 0, primitiveStack, resetStackOffset, shiftPrimitiveStack.length);
            System.arraycopy(shiftObjectStack, 0, objectStack, resetStackOffset, shiftObjectStack.length);
        }
        Qont.REENTER_ON_YIELD.set(Boolean.TRUE);
        throw QuasarUtilities.yieldException;
    }
}

