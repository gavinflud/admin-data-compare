package com.gavinflood.compare

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Responsible for tracking admin data changes across various import files.
 *
 * @param filePath The path to the admin data directory
 * @param includeNewEmptyFields If true, new fields that were not on the previous version of the element will be
 *                              included in the delta, even if they have a value of null
 */
class AdminDataChangeTracker(private val filePath: String, private val includeNewEmptyFields: Boolean) {

    private val orderFileName = "order.txt"
    private val adminData = AdminData()
    private val fileOrder = ArrayList<String>()
    private lateinit var originFileName: String

    /**
     * Process the admin data files.
     */
    fun process() {
        println("Beginning processing admin data")

        val orderFile = File("$filePath\\$orderFileName")
        var i = 0

        orderFile.forEachLine { fileName ->
            // Use the origin file as the base. Probably not great to require a complete export as the base.
            originFileName = if (i == 0) fileName else originFileName; ++i
            fileOrder.add(fileName)

            // Process an individual admin data import file
            FileProcessor(File("$filePath\\$fileName"), adminData.importItems).process()
        }

        println("Finished processing admin data")
    }

    /**
     * Creates a change list file that details the changes made by each file in human-readable form
     */
    fun generateChangeList() {
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream("$filePath\\changes.txt")))
        val changes = getChangesForEachFile()
        fileOrder.forEach { fileName ->
            val changeList = changes[fileName]
            if (changeList != null) {
                writer.write("\n- - - - - - - - - - - - - - - - - - - - - - - - - - -\n")
                writer.write("${fileName}:\n")
                changeList.forEach { change -> writer.write("$change\n") }
            }
        }
        writer.flush()
    }

    /**
     * Exports the generated delta files that contain only the entities and their properties that were changed as
     * part of an admin data import file.
     */
    fun exportDeltas() {
        val deltaExport = DeltaExport("$filePath\\deltas", adminData, includeNewEmptyFields)
        deltaExport.export()
    }

    /**
     * Get a map of the changes made for each file in human-readable form. The key is the file name and the value is a
     * list of the change descriptions.
     */
    private fun getChangesForEachFile(): Map<String, List<String>> {
        val changesPerFile = mutableMapOf<String, MutableList<String>>()
        adminData.importItems.forEach { entry -> populateChanges(entry.key, entry.value, changesPerFile) }
        changesPerFile.values.forEach { changeList -> changeList.sort() }
        return changesPerFile
    }

    /**
     * Goes through a the different versions for the node value and attributes and populates the map of changes.
     *
     * @param id The public-id of the root node
     * @param node The node to check for changes on
     * @param changesPerFile A map of the changes made for each file
     */
    private fun populateChanges(id: String, node: Node, changesPerFile: MutableMap<String, MutableList<String>>) {
        node.attributes.forEach { attribute ->
            attribute.value.forEachIndexed { i, version ->
                // If the file name is not the base file and there is no previous version, then it is a new entry
                if (version.fileName != originFileName && version.previousVersion == null && node.parentNode == null
                    && !hasNewAdminDataEntryMessageAlreadyBeenAdded(changesPerFile, node, version.fileName)
                ) {
                    val message = newAdminDataEntryMessage(getRootNode(node).name, id)
                    val changes = changesPerFile[version.fileName] ?: mutableListOf()
                    changes.add(message)
                    changesPerFile[version.fileName] = changes
                } else if (version.fileName != originFileName && version.previousVersion != null) {
                    // Otherwise, the attribute value has changed.
                    // TODO: I think public-id is the only attribute used in import files, so this seems unnecessary
                    val isChange = i > 0
                    val changes = changesPerFile[version.fileName] ?: mutableListOf()
                    changes.add(attributeMessage(getNodePath(node), id, attribute.key, version, isChange))
                    changesPerFile[version.fileName] = changes
                }
            }
        }

        node.valueVersions.forEachIndexed { i, version ->
            // If the file name is not the base file and there are previous versions for this value, it is a change
            if (version.fileName != originFileName && version.previousVersion != null) {
                val isChange = i > 0
                val changes = changesPerFile[version.fileName] ?: mutableListOf()
                changes.add(valueMessage(getNodePath(node), id, version, isChange))
                changesPerFile[version.fileName] = changes

            } else if (version.fileName != originFileName && (version.newValue != "" || includeNewEmptyFields)
                && !hasNewAdminDataEntryMessageAlreadyBeenAdded(changesPerFile, node, version.fileName)
            ) {
                // If the file name is not the base file and we haven't already indicated this is a new entry
                val changes = changesPerFile[version.fileName] ?: mutableListOf()
                changes.add(valueMessage(getNodePath(node), id, version, false))
                changesPerFile[version.fileName] = changes
            }
        }

        // Recursively traverse the nodes all the way down to include all changes
        node.children.forEach { childNode -> populateChanges(id, childNode, changesPerFile) }
    }

    private fun attributeMessage(
        nodePath: String, id: String, attribute: String, version: NodeValueVersion,
        isChange: Boolean
    ): String {
        val addedOrChanged = if (isChange) "Changed" else "Added"
        return "$addedOrChanged $attribute attribute on $nodePath for '$id' to ${version.newValue}"
    }

    private fun valueMessage(nodePath: String, id: String, version: NodeValueVersion, isChange: Boolean): String {
        return if (isChange) {
            "Changed $nodePath for '$id' to have value '${version.newValue}'"
        } else {
            "Added $nodePath for '$id' with value '${version.newValue}'"
        }
    }

    private fun newAdminDataEntryMessage(nodePath: String, id: String?): String {
        return "Added new $nodePath with public-id: $id"
    }

    /**
     * Returns true if a message has already been added to the change list indicating this node is part of a brand new
     * entry in the import file.
     */
    private fun hasNewAdminDataEntryMessageAlreadyBeenAdded(
        changesPerFile: MutableMap<String, MutableList<String>>,
        node: Node, fileName: String
    ): Boolean {
        val changeList = changesPerFile[fileName]
        val rootNode = getRootNode(node)
        val message = newAdminDataEntryMessage(rootNode.name, rootNode.attributes["public-id"]?.first()?.newValue)
        return changeList != null && changeList.contains(message)
    }

    /**
     * Returns a human-readable path to the node passed in from the root node.
     */
    private fun getNodePath(node: Node, builder: StringBuilder = StringBuilder()): String {
        val parent = node.parentNode
        // Recursively traverse the parents, passing the string builder up to each one and populating it
        if (parent != null) {
            getNodePath(parent, builder)
            builder.append(".${node.name}")
        } else {
            builder.append(node.name)
        }
        return builder.toString()
    }

    /**
     * Recursively gets the root node for the node passed in.
     */
    private fun getRootNode(node: Node): Node {
        var rootNode = node
        val parent = node.parentNode
        if (parent != null) {
            rootNode = getRootNode(parent)
        }
        return rootNode
    }

}