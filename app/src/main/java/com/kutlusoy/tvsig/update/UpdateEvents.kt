package com.kutlusoy.tvsig.update

import kotlinx.coroutines.flow.MutableSharedFlow

/** Carries the result of the installer from the receiver to the screen. */
object UpdateEvents {
    val installFailure = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
