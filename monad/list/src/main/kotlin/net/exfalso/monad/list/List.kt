@file:JvmName("ListMonad")
package net.exfalso.monad.list

import co.paralleluniverse.fibers.Suspendable
import net.exfalso.qont.Qont.shift
import net.exfalso.qont.SuspendableFunction

@Suspendable
fun <B> amb(iterable: Iterable<B>): B {
    return shift<B, Iterable<*>>(SuspendableFunction @Suspendable { cont ->
        iterable.flatMap {
            cont(it)
        }
    }
    )
}

@Suspendable
fun guard(condition: Boolean) {
    return amb(if (condition) listOf(Unit) else emptyList())
}
