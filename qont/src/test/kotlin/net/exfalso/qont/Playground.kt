package net.exfalso.qont

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.SuspendableCallable
import com.google.common.collect.Lists
import net.exfalso.monad.list.Amb
import net.exfalso.qont.Qont.*


@Suspendable
fun <S, A> state(transform: SuspendableFunction<S, Pair<A, S>>): A {
    return shift<A, SuspendableFunction<S, Pair<*, S>>>(SuspendableFunction @Suspendable { cont ->
        SuspendableFunction @Suspendable { state ->
            val (value, nextState) = transform(state)
            cont.invoke(value)(nextState)
        }
    })
}

@Suspendable
fun <S> getState() = state<S, S>(SuspendableFunction @Suspendable { s -> Pair(s, s) })
@Suspendable
fun <S> setState(state: S) = state<S, Unit>(SuspendableFunction @Suspendable { _ -> Pair(Unit, state) })
@Suspendable
fun <A, S> returnState(value: A) = SuspendableFunction @Suspendable { state: S -> Pair(value, state) }

fun main(args: Array<String>) {

    shiftResetWorks()
//
//    localFiber(SuspendableCallable @Suspendable {
//        reset(SuspendableCallable @Suspendable {
//            val a = amb((1..6))
//            val b = amb((1..6))
//            guard(a + b == 7)
//            println(a to b)
//            emptyList<Unit>()
//        })
//        val (result, state) = reset(SuspendableCallable @Suspendable {
//            val a: Int = getState()
//            println(a)
//            setState(a + 2)
//            returnState<String, Int>("HELLO")
//        })(2)
//
//        println(result)
//        println(state)
//    })
}

@Suspendable
fun shiftResetWorks() {
    localFiber(SuspendableCallable @Suspendable {

        val hello = reset @Suspendable {
            checkInstrumented()
            1 + shift(object : SuspendableFunction<SuspendableFunction<Int, Int>, Int> {
                @Suspendable
                override fun invoke(continuation: SuspendableFunction<Int, Int>): Int {
                    checkInstrumented()
                    return continuation.invoke(2) + continuation.invoke(3)
                }
            }
            )
        }

        println(hello)

        checkInstrumented()

        Amb._do<Int> @Suspendable {
            val a = it.bind(Lists.newArrayList(1, 2, 3, 4, 5, 6))
            it.guard(false)
            println(a)
            Lists.newArrayList<Int>()
        }

        0
    })
}
