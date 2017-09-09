package net.exfalso.qont

import co.paralleluniverse.fibers.*
import co.paralleluniverse.strands.SuspendableCallable

typealias ResetOffset = Int

/**
 * @param stackOffset The stack offset of the shift() frame
 */
data class ShiftData<R>(
        internal val stackOffset: Int,
        internal val block: SuspendableBlock<SuspendableBlock<*, R>, R>
)

private val SHIFT_DATA = ThreadLocal<ShiftData<Any?>>()
private val RESET_OFFSET = ThreadLocal<ResetOffset>()
private val SHIFT_RESULT = ThreadLocal<Any?>()

private inline fun <reified R, A> getFieldGetter(name: String): (R?) -> A {
    val field = R::class.java.getDeclaredField(name)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return { field.get(it) as A }
}

private inline fun <reified R, A> getFieldSetter(name: String): (R?, A) -> Unit {
    val field = R::class.java.getDeclaredField(name)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return { obj, value -> field.set(obj, value) }
}


private val getFiberStack = getFieldGetter<Fiber<*>, Stack>("stack")
private val getPrimitiveStack = getFieldGetter<Stack, LongArray>("dataLong")
private val getObjectStack = getFieldGetter<Stack, Array<Any?>>("dataObject")
private val getStackPointer = getFieldGetter<Stack, Int>("sp")
private val setPrimitiveStack = getFieldSetter<Stack, LongArray>("dataLong")
private val setObjectStack = getFieldSetter<Stack, Array<Any?>>("dataObject")

private val MASK_FULL: Long = -1L

private fun getNumSlots(record: Long): Int {
    return getUnsignedBits(record, 14, 16).toInt()
}

private fun getPrevNumSlots(record: Long): Int {
    return getUnsignedBits(record, 30, 16).toInt()
}

private fun setBits(word: Long, offset: Int, length: Int, value: Long): Long {
    val a = 64 - length
    val b = a - offset
    return word and (MASK_FULL.ushr(a) shl b).inv() or (value shl b)
}

private fun getEntry(word: Long): Long {
    return getUnsignedBits(word, 0, 14)
}

private fun setEntry(word: Long, value: Long): Long {
    return setBits(word, 0, 14, value)
}

private fun setNumSlots(word: Long, value: Long): Long {
    return setBits(word, 14, 16, value)
}

private fun setPrevNumSlots(word: Long, value: Long): Long {
    return setBits(word, 30, 16, value)
}

private fun getUnsignedBits(word: Long, offset: Int, length: Int): Long {
    val a = 64 - length
    val b = a - offset
    return word.ushr(b) and MASK_FULL.ushr(a)
}

@Suspendable
fun <R> reset(block: SuspendableCallable<R>): R {
    return if (block == block) {
        RESET_OFFSET.set(getStackPointer(getFiberStack(Fiber.currentFiber())) - 1)
        block.run()
    } else {
        Fiber.yield()
        val shiftToken = SHIFT_DATA.get() as ShiftData<R>
        return shiftToken.block.invoke(createContinuation(block, shiftToken.stackOffset))
    }
}

private fun <R> createContinuation(block: SuspendableCallable<R>, shiftOffset: Int): ShiftContinuation<Any?, R> {
    val stack = getFiberStack(Fiber.currentFiber())
    val resetOffset = getStackPointer(stack) - 1
    val primitiveStack = getPrimitiveStack(stack)
    val objectStack = getObjectStack(stack)
    val shiftRecord = primitiveStack[shiftOffset]
    val resetRecord = primitiveStack[resetOffset]
    val usedStackSize = shiftOffset + getNumSlots(shiftRecord) + 1
    val primitiveSubStack = primitiveStack.copyOfRange(resetOffset, usedStackSize)
    val objectSubStack = objectStack.copyOfRange(resetOffset, usedStackSize)
    primitiveSubStack[shiftOffset - resetOffset] = setEntry(shiftRecord, getEntry(shiftRecord) + 1)
    primitiveSubStack[0] = setPrevNumSlots(setEntry(resetRecord, getEntry(resetRecord) - 1), 0)
    return ShiftContinuation(block, primitiveSubStack, objectSubStack)
}

class ShiftContinuation<A, R>(
        private val block: SuspendableCallable<R>,
        private val shiftPrimitiveStack: LongArray,
        private val shiftObjectStack: Array<Any?>
) : SuspendableBlock<A, R> {

    @Suspendable
    override fun invoke(argument: A): R {
        if (argument == argument) {
            jumpIntoReset(argument)
        } else {
            return reset(block)
        }
    }

    private fun jumpIntoReset(argument: A): Nothing {
        val stack = getFiberStack(Fiber.currentFiber())
        val primitiveStack = getPrimitiveStack(stack)
        val objectStack = getObjectStack(stack)
        val executeStackOffset = getStackPointer(stack) - 1

        val resetStackOffset = executeStackOffset + 1
        primitiveStack[executeStackOffset] = setPrevNumSlots(setNumSlots(setEntry(0, 1), 0), 0)
        SHIFT_RESULT.set(argument)
        RESET_OFFSET.set(resetStackOffset)

        val requiredSize = resetStackOffset + shiftPrimitiveStack.size
        if (requiredSize >= primitiveStack.size) {
            var newStackSize = primitiveStack.size
            while (newStackSize <= requiredSize) {
                newStackSize = newStackSize * 2
            }
            val newPrimitiveStack = LongArray(newStackSize)
            val newObjectStack = arrayOfNulls<Any>(newStackSize)
            System.arraycopy(primitiveStack, 0, newPrimitiveStack, 0, primitiveStack.size)
            System.arraycopy(objectStack, 0, newObjectStack, 0, objectStack.size)
            System.arraycopy(shiftPrimitiveStack, 0, newPrimitiveStack, resetStackOffset, shiftPrimitiveStack.size)
            System.arraycopy(shiftObjectStack, 0, newObjectStack, resetStackOffset, shiftObjectStack.size)
            setPrimitiveStack(stack, newPrimitiveStack)
            setObjectStack(stack, newObjectStack)
        } else {
            System.arraycopy(shiftPrimitiveStack, 0, primitiveStack, resetStackOffset, shiftPrimitiveStack.size)
            System.arraycopy(shiftObjectStack, 0, objectStack, resetStackOffset, shiftObjectStack.size)
        }
        throw yieldException
    }
}

fun prettyRecord(record: Long?): String {
    return if (record == null) {
        "NULL"
    } else {
        "entry[${getEntry(record)}] slots[${getNumSlots(record)}] prev_slots[${getPrevNumSlots(record)}]"
    }
}

private val yieldException = getFieldGetter<SuspendExecution, SuspendExecution>("YIELD")(null)

@Suspendable
fun <A, R> shift(block: SuspendableBlock<SuspendableBlock<A, R>, R>): A {
    if (block == block) {
        setupResetJump(block)
        yieldNoSuspend()
    } else {
        Fiber.yield()
        return SHIFT_RESULT.get() as A
    }
}

private fun <A, R> setupResetJump(block: SuspendableBlock<SuspendableBlock<A, R>, R>) {
    val stack = getFiberStack(Fiber.currentFiber())
    val primitiveStack = getPrimitiveStack(stack)
    val stackOffset = RESET_OFFSET.get()!!
    RESET_OFFSET.remove()
    val resetRecord = primitiveStack[stackOffset]
    primitiveStack[stackOffset] = setEntry(resetRecord, getEntry(resetRecord) + 1)
    SHIFT_DATA.set(ShiftData(getStackPointer(stack) - 1, block as SuspendableBlock<SuspendableBlock<*, Any?>, Any?>))
}

private fun yieldNoSuspend(): Nothing {
    throw yieldException
}

@Suspendable
fun <B> amb(list: Iterable<B>): B {
    return shift<B, List<*>>(SuspendableBlock @Suspendable { cont -> list.flatMap { cont(it) } })
}

@Suspendable
fun <S, A> state(transform: SuspendableBlock<S, Pair<A, S>>): A {
    return shift<A, SuspendableBlock<S, Pair<*, S>>>(SuspendableBlock @Suspendable { cont ->
        SuspendableBlock @Suspendable { state ->
            val (value, nextState) = transform(state)
            cont.invoke(value)(nextState)
        }
    })
}

@Suspendable
fun <S> getState() = state<S, S>(SuspendableBlock @Suspendable { s -> Pair(s, s) })
@Suspendable
fun <S> setState(state: S) = state<S, Unit>(SuspendableBlock @Suspendable { _ -> Pair(Unit, state) })
@Suspendable
fun <A, S> returnState(value: A) = SuspendableBlock @Suspendable { state: S -> Pair(value, state) }

@Suspendable
fun guard(condition: Boolean) {
    return amb(if (condition) listOf(Unit) else emptyList())
}

var fiber: Fiber<*>? = null
fun main(args: Array<String>) {
    val scheduler = FiberForkJoinScheduler("scheduler", 8)
    scheduler.newFiber @Suspendable {
        fiber = Fiber.currentFiber()

        reset(SuspendableCallable @Suspendable {
            val a = amb((1 .. 6))
            val b = amb((1 .. 6))
            guard(a + b == 7)
            println(a to b)
            emptyList<Unit>()
        })

        val (result, state) = reset(SuspendableCallable @Suspendable {
            val a: Int = getState()
            println(a)
            setState(a + 2)
            returnState<String, Int>("HELLO")
        })(2)

        println(result)
        println(state)

//        val result = Qont.reset<Int> @Suspendable {
//            val a = 1 + Qont.shift<Int, Int>(it) @Suspendable { cont ->
//                cont(2) + cont(40)
//            }
//            println(a)
//            val b = 2 + Qont.shift<Int, Int>(it) @Suspendable { cont ->
//                cont(3)
//            }
//            println(b)
//            a + b
//        }
//        println(result)
    }.start().get()
}
