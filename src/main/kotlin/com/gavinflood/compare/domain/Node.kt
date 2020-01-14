package com.gavinflood.compare.domain

class Node(
    val name: String,
    val children: MutableList<Node> = mutableListOf(),
    val versions: MutableList<Version> = mutableListOf(),
    var publicId: String? = null,
    var parentNode: Node? = null
)