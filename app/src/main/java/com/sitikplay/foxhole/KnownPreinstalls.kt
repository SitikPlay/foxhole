package com.sitikplay.foxhole

object KnownPreinstalls {

    data class Item(val pkg: String, val note: Int)

    val ALL: List<Item> = listOf(
        Item("com.facebook.system", R.string.t441),
        Item("com.facebook.services", R.string.t442),
        Item("com.facebook.appmanager", R.string.t443),
        Item("com.spotify.music", R.string.t444),
        Item("com.netflix.mediaclient", R.string.t445),
        Item("com.zhiliaoapp.musically", R.string.t446),
        Item("com.linkedin.android", R.string.t447),
        Item("com.microsoft.skydrive", R.string.t448),
        Item("com.skype.raider", R.string.t449),
        Item("com.ebay.mobile", R.string.t450)
    )
}
