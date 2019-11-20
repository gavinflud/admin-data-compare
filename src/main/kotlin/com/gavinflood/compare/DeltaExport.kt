package com.gavinflood.compare

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Exports the delta files for the admin data.
 *
 * @param directoryPath The directory to export the delta files to
 * @param adminData The admin data structure that contains the history of the entries
 * @param includeNewEmptyFields If true, new fields that were not on the previous version of the element will be
 *                              included in the delta, even if they have a value of null
 */
class DeltaExport(
    private val directoryPath: String, private val adminData: AdminData,
    private val includeNewEmptyFields: Boolean
) {

    // Map with the key being the file name and the value being another map of the nodes changed by that file and their
    // public-ids
    private val changeFiles = mutableMapOf<String, MutableMap<String, Node>>()

    /**
     * Export the files.
     */
    fun export() {
        populateChangeFiles()
        changeFiles.forEach { changesForFile -> createDeltaFile(changesForFile.key, changesForFile.value) }
        print("Finished delta export")
    }

    /**
     * Iterates over the root nodes and populates the map with the changes made for each of them.
     */
    private fun populateChangeFiles() {
        adminData.importItems.forEach { importItem -> populateChangeFilesFromNode(importItem.key, importItem.value) }
    }

    /**
     * Goes through the different values this node has had and adds entries to the changeFiles map. Also recursively
     * calls itself for each child node the current node has to add their changes to the map as well.
     *
     * @param id The identifier for the node
     * @param node The node to check for changes on and populate the map accordingly
     */
    private fun populateChangeFilesFromNode(id: String, node: Node) {
        node.valueVersions.forEach { version ->
            val existingNodesForFile = changeFiles[version.fileName] ?: mutableMapOf()
            if (existingNodesForFile[id] == null) {
                existingNodesForFile[id] = getRootNode(node)
            }
            changeFiles[version.fileName] = existingNodesForFile
        }

        node.children.forEach { childNode -> populateChangeFilesFromNode(id, childNode) }
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

    /**
     * Creates a single delta file.
     *
     * @param fileName The name of the file (based on the original file that made the changes)
     * @param nodes A map where the key is the public-id and the value is the node that was changed in this file
     */
    private fun createDeltaFile(fileName: String, nodes: Map<String, Node>) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        document.xmlStandalone = true
        val rootElement = document.createElement("import")
        document.appendChild(rootElement)

        // For each root node, create a new element under rootElement. Then iterate over its child nodes and parse them
        nodes.forEach { node ->
            val element = document.createElement(node.value.name)
            node.value.attributes.forEach { att -> element.setAttribute(att.key, att.value.last().newValue) }
            rootElement.appendChild(element)
            node.value.children.forEach { child -> parseNode(document, fileName, child, element) }
        }

        // Exports the xml file
        val outputDirectory = File("$directoryPath\\")
        if (!outputDirectory.exists()) outputDirectory.mkdir()
        val transformerFactory = TransformerFactory.newInstance()
        transformerFactory.setAttribute("indent-number", 2)
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.transform(DOMSource(document), StreamResult(File("${outputDirectory.path}\\$fileName")))
    }

    /**
     * Parse the current node.
     *
     * Check for versions of the value and see if they match the file name. If they do, then this indicates that this
     * node was changed by this file and this change needs to be included in the delta file.
     */
    private fun parseNode(document: Document, fileName: String, node: Node, parent: Element): Boolean {
        val element = document.createElement(node.name)
        var shouldAdd = false
        node.attributes.forEach { att -> element.setAttribute(att.key, att.value.last().newValue) }
        node.valueVersions.forEach { version ->
            if (version.fileName == fileName) {
                if (!(version.newValue == "" && version.previousVersion == null && !includeNewEmptyFields)) {
                    element.textContent = version.newValue
                    shouldAdd = true
                }
            }
        }

        // Recursively parses all child nodes as well. Uses the shouldAdd flag to indicate whether the elements we're
        // creating should be actually added to the file.
        node.children.forEach { child ->
            if (parseNode(document, fileName, child, element)) {
                shouldAdd = true
            }
        }

        if (shouldAdd) parent.appendChild(element)

        return shouldAdd
    }

}