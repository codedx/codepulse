package com.secdec.bytefrog.hq.util

import java.util.concurrent.atomic.AtomicBoolean

/** A utility for wrapping existing functions so that they may be called at most one time.
  *
  * This is not a replacement for lazy vals; the idea is that a function may be called with
  * some parameters while not caring about the return value.
  *
  * Example Usage: {{{
  * val initializeEverything = DoOnce{(param: InitParam) =>
  *   actuallyDoInitialization(param)
  * }
  *
  * initializeEverything(param) // calls actuallyDoInitialization
  * initializeEverything(param) // no op
  * initializeEverything(param2) // no op, even with different params
  * }}}
  */
object DoOnce {

	def apply(func: Function0[_]): Function0[Unit] = {
		val done = new AtomicBoolean(false)

		() => if (done.compareAndSet(false, true)) {
			func()
		}
	}

	def apply[T1](func: Function1[T1, _]): Function1[T1, Unit] = {
		val done = new AtomicBoolean(false)

		(arg: T1) => if (done.compareAndSet(false, true)) {
			func(arg)
		}
	}
}