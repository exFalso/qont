package net.exfalso.qont;

import co.paralleluniverse.fibers.Stack;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;

import java.lang.reflect.Field;

final class QuasarUtilities {

    private static Field getField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static <A> A getFieldValue(Field field, Object object) {
        try {
            return (A) field.get(object);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static <A> void setFieldValue(Field field, Object object, A value) {
        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static final SuspendExecution yieldException = getFieldValue(getField(SuspendExecution.class, "YIELD"), null);
    private static final Field primitiveStackField = getField(Stack.class, "dataLong");
    static long[] getPrimitiveStack(Stack stack) {
        return getFieldValue(primitiveStackField, stack);
    }
    static void setPrimitiveStack(Stack stack, long[] value) {
        setFieldValue(primitiveStackField, stack, value);
    }
    private static final Field objectStackField = getField(Stack.class, "dataObject");
    static Object[] getObjectStack(Stack stack) {
        return getFieldValue(objectStackField, stack);
    }
    static void setObjectStack(Stack stack, Object[] value) {
        setFieldValue(objectStackField, stack, value);
    }
    private static final Field stackPointerField = getField(Stack.class, "sp");
    static int getStackPointer(Stack stack) {
        return getFieldValue(stackPointerField, stack);
    }
    static void setStackPointer(Stack stack, int value) {
        setFieldValue(stackPointerField, stack, value);
    }
    private static final Field currentStrandField = getField(Strand.class, "currentStrand");
    static ThreadLocal<Strand> getCurrentStrand() {
        return getFieldValue(currentStrandField, null);
    }

    // Entry manipulation
    private static long MASK_FULL = -1L;
    static long getUnsignedBits(long word, int offset, int length) {
        int a = 64 - length;
        int b = a - offset;
        return (word >>> b) & (MASK_FULL >>> a);
    }
    static int getNumSlots(long record) {
        return (int) getUnsignedBits(record, 14, 16);
    }
    static int getPrevNumSlots(long record) {
        return (int) getUnsignedBits(record, 30, 16);
    }
    static int getEntry(long record) {
        return (int) getUnsignedBits(record, 0, 14);
    }

    private static long setBits(long word, int offset, int length, long value) {
        int a = 64 - length;
        int b = a - offset;
        word = word & ~((MASK_FULL >>> a) << b);
        word = word | (value << b);
        return word;
    }
    static long setNumSlots(long record, int numSlots) {
        return setBits(record, 14, 16, numSlots);
    }
    static long setPrevNumSlots(long record, int numSlots) {
        return setBits(record, 30, 16, numSlots);
    }
    static long setEntry(long record, int entry) {
        return setBits(record, 0, 14, entry);
    }
}

//
//final class QuasarUtilities {
//    internal val yieldException = getFieldGetter<SuspendExecution, SuspendExecution>("YIELD")(null)
//    internal val getPrimitiveStack = getFieldGetter<Stack, LongArray>("dataLong")
//    internal val getObjectStack = getFieldGetter<Stack, Array<Any?>>("dataObject")
//    internal val getStackPointer = getFieldGetter<Stack, Int>("sp")
//    internal val getCurrentStrand = getFieldGetter<Strand, ThreadLocal<Strand>>("currentStrand")
//    internal val setPrimitiveStack = getFieldSetter<Stack, LongArray>("dataLong")
//    internal val setObjectStack = getFieldSetter<Stack, Array<Any?>>("dataObject")
//    internal val setStackPointer = getFieldSetter<Stack, Int>("sp")
//
//    private fun getUnsignedBits(word: Long, offset: Int, length: Int): Long {
//        val a = 64 - length
//        val b = a - offset
//        return word.ushr(b) and MASK_FULL.ushr(a)
//    }
//
//    internal fun getNumSlots(record: Long): Int {
//        return getUnsignedBits(record, 14, 16).toInt()
//    }
//
//    internal fun getPrevNumSlots(record: Long): Int {
//        return getUnsignedBits(record, 30, 16).toInt()
//    }
//
//    internal fun getEntry(word: Long): Long {
//        return getUnsignedBits(word, 0, 14)
//    }
//
//    internal fun setBits(word: Long, offset: Int, length: Int, value: Long): Long {
//        val a = 64 - length
//        val b = a - offset
//        return word and (MASK_FULL.ushr(a) shl b).inv() or (value shl b)
//    }
//
//    internal fun setEntry(word: Long, value: Long): Long {
//        return setBits(word, 0, 14, value)
//    }
//
//    internal fun setNumSlots(word: Long, value: Long): Long {
//        return setBits(word, 14, 16, value)
//    }
//
//    internal fun setPrevNumSlots(word: Long, value: Long): Long {
//        return setBits(word, 30, 16, value)
//    }
//
//    private inline fun <reified R, A> getFieldGetter(name: String): (R?) -> A {
//        val field = R::class.java.getDeclaredField(name)
//        field.isAccessible = true
//        @Suppress("UNCHECKED_CAST")
//        return { field.get(it) as A }
//    }
//
//    private inline fun <reified R, A> getFieldSetter(name: String): (R?, A) -> Unit {
//        val field = R::class.java.getDeclaredField(name)
//        field.isAccessible = true
//        @Suppress("UNCHECKED_CAST")
//        return { obj, value -> field.set(obj, value) }
//    }
//
//    private val MASK_FULL: Long = -1L
//}
