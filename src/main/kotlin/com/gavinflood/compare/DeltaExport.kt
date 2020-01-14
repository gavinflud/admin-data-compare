package com.gavinflood.compare

import com.gavinflood.compare.domain.AdminData
import com.gavinflood.compare.domain.Node
import com.gavinflood.compare.domain.Version
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class DeltaExport(
    private val directoryPath: String,
    private val adminData: AdminData,
    private val baseFileName: String
) {

    // Key is the file name. Value is a map with the value being an import entity and the key being its public-id
    private var fileChangesStore = mutableMapOf<String, MutableMap<String, Node>>()

    /**
     * Export the files.
     */
    fun export() {
        println("Start: Exporting delta files")

        populateFileChangesStore()
        fileChangesStore.forEach { createDeltaFile(it.key, it.value) }

        println("Complete: Exporting delta files")
    }

    /**
     * Iterates over the entities and populates the map with the changes made for each of them.
     */
    private fun populateFileChangesStore() {
        adminData.entities.forEach { populateStoreWithNodeChanges(it.key, it.value) }
    }

    /**
     * Goes through the different values this node has had and adds entries to the changeFiles map. Also recursively
     * calls itself for each child node the current node has to add their changes to the map as well.
     *
     * @param publicId The identifier for the node
     * @param node The node to check for changes on and populate the map accordingly
     */
    private fun populateStoreWithNodeChanges(publicId: String, node: Node) {
        node.versions.forEach { version ->
            val entitiesUnderFile = fileChangesStore[version.fileName] ?: mutableMapOf()
            if (entitiesUnderFile[publicId] == null) {
                entitiesUnderFile[publicId] = getRootNode(node)
            }
            fileChangesStore[version.fileName] = entitiesUnderFile
        }

        node.children.forEach { populateStoreWithNodeChanges(publicId, it) }
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
     * @param _entities A map where the key is the public-id and the value is the entity that was changed in this file
     */
    private fun createDeltaFile(fileName: String, _entities: Map<String, Node>) {
        if (fileName == baseFileName) {
            return
        }

        val entities = _entities.toSortedMap()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        document.xmlStandalone = true
        val rootElement = document.createElement("import")
        document.appendChild(rootElement)

        // For each root node, create a new element under rootElement. Then iterate over its child nodes and parse them
        entities.forEach { entity ->
            val element = document.createElement(entity.value.name)
            element.setAttribute("public-id", entity.value.publicId)

            var shouldAdd = false
            entity.value.children.forEach { child ->
                if (parseNode(document, fileName, child, element)) {
                    shouldAdd = true
                }
            }

            if (shouldAdd) rootElement.appendChild(element)
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

        if (node.publicId != null) {
            element.setAttribute("public-id", node.publicId)
        }

        node.versions.forEach { version ->
            if (shouldIncludeRegularNode(version, node, fileName) || shouldIncludeArrayNode(version, node, fileName)) {
                element.textContent = version.value
                shouldAdd = true
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

    private fun shouldIncludeRegularNode(version: Version, node: Node, fileName: String): Boolean {
        if (version.fileName == fileName
            && ((version.previousVersion == null && (version.value != "" || node.publicId != null))
                    || version.previousVersion != null && version.previousVersion.value != version.value)
        ) {
            return true
        }

        return false
    }

    private fun shouldIncludeArrayNode(version: Version, node: Node, fileName: String): Boolean {
        if (version.fileName == fileName && isArrayNode(node) && isOneArrayElementChanged(node, fileName)) {
            return true
        }

        return false
    }

    /**
     * Check if the current node is part of an array.
     */
    private fun isArrayNode(node: Node): Boolean {
        val parentNode = node.parentNode
        if (parentNode != null && parentNode.children.size > 1) {
            val firstChildName = parentNode.children[0].name
            return parentNode.children.all { childNode -> childNode.name == firstChildName }
        }

        return false
    }

    private fun isOneArrayElementChanged(node: Node, fileName: String): Boolean {
        val parentNode = node.parentNode
        if (parentNode != null) {
            return parentNode.children.any { arrayElement ->
                arrayElement.children.any { child ->
                    child.versions.any { version ->
                        version.fileName == fileName
                                && (version.previousVersion == null || version.previousVersion.value != version.value)
                    }
                }
            }
        }

        return false
    }

}