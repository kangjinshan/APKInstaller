package com.apkinstaller.app

data class Device(
    val ip: String,
    val port: Int = 5555,
    var isSelected: Boolean = false,
    var status: String = "在线"
) {
    override fun toString(): String {
        return "$ip:$port"
    }
}
