package com.kutlusoy.tvsig.models

/** How the empty area around an image or video is filled. */
enum class BackdropMode(val id: Int) {
    /** A zoomed, strongly blurred copy of the picture. */
    ZOOM(0),

    /** The picture continued mirrored over its edges, then blurred. The colors meet without a seam. */
    MIRROR(1);

    companion object {
        val DEFAULT = ZOOM

        fun fromId(id: Int): BackdropMode = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
