package com.gavinflood.compare

/**
 * Root class that stores all of the admin data entries (and their history).
 *
 * @param importItems Map where the key is the admin data entry public-id and the value is the associated [Node]
 */
data class AdminData(val importItems: MutableMap<String, Node> = mutableMapOf())

/**
 * A node represents an XML element.
 *
 * @param name The name of the element (e.g. FormPattern, Role, etc.)
 * @param children A list of child nodes that have this node as their parent
 * @param valueVersions A list of the different values this node has had
 * @param attributes A map of the attributes attached to this node and a list of the different values they each have had
 * @param parentNode The parent of this node. Can be null if this is a root node.
 */
data class Node(
    val name: String, val children: MutableList<Node> = mutableListOf(),
    val valueVersions: MutableList<NodeValueVersion> = mutableListOf(),
    val attributes: MutableMap<String, MutableList<NodeValueVersion>> = mutableMapOf(),
    var parentNode: Node? = null
) {
    /**
     * If you don't override this, you get a StackOverflowError when this is called while processing large XML files
     * because it tries to return everything by default (parent->children->parent->children).
     */
    override fun toString(): String {
        return name
    }
}

/**
 * Stores a single version of a node/attribute value.
 *
 * @param fileName The file that this version was introduced in
 * @param newValue The new value for the node/attribute
 * @param previousVersion The previous version of the node/attribute. Can be null if this is a new node/attribute.
 */
data class NodeValueVersion(val fileName: String, val newValue: String, var previousVersion: NodeValueVersion? = null)