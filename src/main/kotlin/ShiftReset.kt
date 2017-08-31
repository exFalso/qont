import co.paralleluniverse.fibers.*

/**
 * @param stackOffset The stack offset of the reset() frame
 */
data class ResetToken<R>(
        internal val stackOffset: Int
)

/**
 * @param stackOffset The stack offset of the shift() frame
 */
data class ShiftToken<R>(
        internal val stackOffset: Int,
        internal val block: SuspendableBlock<SuspendableBlock<*, R>, R>
)

private val SHIFT_TOKEN = ThreadLocal<ShiftToken<Any?>>()
private val SHIFT_RESULT = ThreadLocal<Any?>()

data class ShiftResumption<A, out R>(
        val value: A,
        val resume: (A) -> R
)

private inline fun <reified R, A> getFieldGetter(name: String): (R?) -> A {
    val field = R::class.java.getDeclaredField(name)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return { field.get(it) as A }
}
private inline fun <reified R, A> R.getField(name: String): A {
    val field = R::class.java.getDeclaredField(name)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(this) as A
}

private val getFiberStack = getFieldGetter<Fiber<*>, Stack>("stack")
private val getObjectStack = getFieldGetter<Stack, Array<Any?>>("dataObject")
private val getPrimitiveStack = getFieldGetter<Stack, LongArray>("dataLong")
private val getStackPointer = getFieldGetter<Stack, Int>("sp")

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
fun <R> reset_(block: SuspendableBlock<ResetToken<R>, R>): R {
//    println("reset_ called")
    return if (block == block) {
        val stack = getFiberStack(Fiber.currentFiber())
        val resetOffset = getStackPointer(stack) - 1
        block.invoke(ResetToken(resetOffset))
    } else {
        Fiber.yield()
        val shiftToken = SHIFT_TOKEN.get() as ShiftToken<R>
        val stack = getFiberStack(Fiber.currentFiber())
        val resetOffset = getStackPointer(stack) - 1
        val primitiveStack = getPrimitiveStack(stack)
        val objectStack = getObjectStack(stack)
        val shiftRecord = primitiveStack[shiftToken.stackOffset]
        val resetRecord = primitiveStack[resetOffset]
        val usedStackSize = shiftToken.stackOffset + getNumSlots(shiftRecord) + 1
        val primitiveSubStack = primitiveStack.copyOfRange(resetOffset, usedStackSize)
        val objectSubStack = objectStack.copyOfRange(resetOffset, usedStackSize)
        primitiveSubStack[shiftToken.stackOffset - resetOffset] = setEntry(shiftRecord, getEntry(shiftRecord) + 1)
        primitiveSubStack[0] = setPrevNumSlots(setEntry(resetRecord, getEntry(resetRecord) - 1), 0)
        return shiftToken.block.invoke(ResumeBlock<Any?, R>(block, primitiveSubStack, objectSubStack))
    }
}

class ResumeBlock<A, R>(
        val block: SuspendableBlock<ResetToken<R>, R>,
        val shiftPrimitiveStack: LongArray,
        val shiftObjectStack: Array<Any?>
) : SuspendableBlock<A, R> {

    @Suspendable
    override fun invoke(argument: A): R {
        if (block == block) {
            jumpIntoReset(argument)
        } else {
            return reset_(block)
        }
    }

    private fun jumpIntoReset(argument: A): Nothing {
        // 10133133523943424
        val stack = getFiberStack(Fiber.currentFiber())
        val primitiveStack = getPrimitiveStack(stack)
        val objectStack = getObjectStack(stack)
        val executeStackOffset = getStackPointer(stack) - 1

        val resetStackOffset = executeStackOffset + 1
        // 1125899906842624
        primitiveStack[executeStackOffset] = setPrevNumSlots(setNumSlots(setEntry(0, 1), 0), 0)
        SHIFT_RESULT.set(argument)
        System.arraycopy(shiftPrimitiveStack, 0, primitiveStack, resetStackOffset, shiftPrimitiveStack.size)
        System.arraycopy(shiftObjectStack, 0, objectStack, resetStackOffset, shiftObjectStack.size)
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

interface Asd<out A> {
    fun f(): A
}

@Suspendable
fun <A, R> ResetToken<R>.shift_(block: SuspendableBlock<SuspendableBlock<A, R>, R>): A {
    if (block == block) {
        val stack = getFiberStack(Fiber.currentFiber())
        val primitiveStack = getPrimitiveStack(stack)
        val resetRecord = primitiveStack[stackOffset]
        primitiveStack[stackOffset] = setEntry(resetRecord, getEntry(resetRecord) + 1)
        SHIFT_TOKEN.set(ShiftToken(getStackPointer(stack) - 1, block as SuspendableBlock<SuspendableBlock<*, Any?>, Any?>))
        throw yieldException
    } else {
        Fiber.yield()
        return SHIFT_RESULT.get() as A
    }
}

class ListMonad<out A>(
        private val resetToken: ResetToken<List<A>>
) {
    @Suspendable
    fun <B> bind(list: List<B>): B {
        return Qont.shift(resetToken) @Suspendable { cont ->
            list.flatMap { cont(it) }
        }
    }
}

inline fun <A> listM(crossinline block: ListMonad<A>.() -> List<A>): List<A> {
    return Qont.reset @Suspendable { block(ListMonad(it)) }
}

var fiber: Fiber<*>? = null
fun main(args: Array<String>) {
    val scheduler = FiberForkJoinScheduler("fiber-scheduler", 8)
    scheduler.newFiber @Suspendable {
        fiber = Fiber.currentFiber()

        val strings = listM<String> {
            val number = bind(listOf(1, 2, 3))
            listOf(number.toString())
        }

        println(strings)

        val result = Qont.reset<Int> @Suspendable {
            val str = 1 +
                    Qont.shift<Int, Int>(it) @Suspendable { cont ->
                        val bello = cont(2)
                        val chello = cont(3)
                        bello + chello
                    }
            println(str)
            str
        }
        println(result)
    }.start().get()
}