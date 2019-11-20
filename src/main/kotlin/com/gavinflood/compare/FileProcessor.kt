package com.gavinflood.compare

import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * Processes a single admin data import file.
 *
 * @param file The file to process
 * @param importNodes A map where the key is the entry public-id and the value is the node it is associated with
 */
class FileProcessor(
    private val file: File, private val importNodes: MutableMap<String, Node>
) {

    private val xmlReader = getReader()
    private var startElementName = ""
    private var endElementName = ""
    private var lastElementName = ""

    // Indicates when the next element you hit will be the name of the admin data entities this file will import
    // TODO: This could pose issues because import files don't only have to contain one type of root element
    private var isNextElementAdminDataTypeName = false

    // Will be reassigned as soon as "isNextElementAdminDataTypeName" is set to true
    // TODO: Same issue as "isNextElementAdminDataTypeName"
    private var adminDataTypeName = "temp"

    // Will be immediately reassigned inside "handleStartElement" and this is never stored
    private var currentNode = Node("temp")

    // Stores the parent nodes in a list that we can pop them out of once we hit the closing element
    private val parentNodes = mutableListOf<Node>()

    // Will be the public-id for the root node
    private var rootNodeIdentifierValue = "temp"

    private val importItemIdentifier = "public-id"

    /**
     * Parse the XML file.
     */
    fun process() {
        println("Beginning processing ${file.name}")

        while (xmlReader.hasNext()) {
            when (xmlReader.next()) {
                XMLStreamConstants.START_ELEMENT -> handleStartElement()
                XMLStreamConstants.CHARACTERS -> handleCharacters()
                XMLStreamConstants.END_ELEMENT -> handleEndElement()
            }
        }

        println("Finished processing ${file.name}")
    }

    /**
     * Get an XML stream reader.
     */
    private fun getReader(): XMLStreamReader {
        return XMLInputFactory
            .newInstance()
            .createXMLStreamReader(InputStreamReader(FileInputStream(file)))
    }

    /**
     * Handle the situation where we encounter a start element (e.g. <FormPattern public-id="pc:1234">).
     */
    private fun handleStartElement() {
        startElementName = xmlReader.localName

        // The element directly beneath the "import" element is the root admin data import type
        if (startElementName == "import") {
            isNextElementAdminDataTypeName = true
        } else {
            setCurrentNode()
            setParentNode()
            setAdminDataTypeName()
            addNodeInHierarchy()
            handleElementAttributes()
        }
    }

    /**
     * Handle the situation where we encounter an element value (characters between two elements).
     */
    private fun handleCharacters() {
        val content = if (xmlReader.text.contains("\n")) "" else xmlReader.text

        if (currentNode.name != lastElementName || startElementName != endElementName) {
            if (currentNode.valueVersions.isEmpty()) {
                currentNode.valueVersions.add(NodeValueVersion(file.name, content))
            } else if (currentNode.name != lastElementName) {
                val latestVersion = currentNode.valueVersions[currentNode.valueVersions.size - 1]
                if (latestVersion.newValue != content) {
                    currentNode.valueVersions.add(NodeValueVersion(file.name, content, latestVersion))
                }
            }

            lastElementName = currentNode.name
        }
    }

    /**
     * Handle the situation where we encounter an end element (e.g. </FormPattern>).
     */
    private fun handleEndElement() {
        endElementName = xmlReader.localName

        // If the element name matches the data type and there is only one parent node (this), then it is a new entry
        if (endElementName == adminDataTypeName && parentNodes.size == 1) {
            importNodes[rootNodeIdentifierValue] = parentNodes[0]
        }

        // Remove the last parent node from the list since this is the closing tag for it
        if (parentNodes.isNotEmpty()) {
            parentNodes.removeAt(parentNodes.size - 1)
        }
    }

    /**
     * Adds the current node to the parent nodes list. Also sets the current node's parent to the last node in the list.
     */
    private fun setParentNode() {
        if (parentNodes.isNotEmpty()) {
            currentNode.parentNode = parentNodes[parentNodes.size - 1]
        }
        parentNodes.add(currentNode)
    }

    /**
     * Set the current node.
     */
    private fun setCurrentNode() {
        currentNode = Node(xmlReader.localName)

        // Check if this node already exists as part of the last parent node's children. This means it has already been
        // added as part of an earlier file, so we want to assign that as the current node rather than create a new one.
        if (parentNodes.isNotEmpty()) {
            val matches = parentNodes[parentNodes.size - 1].children.filter { node ->
                node.name == startElementName
                        && (xmlReader.attributeCount == 0
                        || node.attributes["public-id"]?.get(0)?.newValue == xmlReader.getAttributeValue(0))
            }
            currentNode = if (matches.size == 1) matches[0] else currentNode
        }
    }

    private fun setAdminDataTypeName() {
        if (isNextElementAdminDataTypeName) {
            adminDataTypeName = startElementName
            isNextElementAdminDataTypeName = false
        }
    }

    /**
     * Adds the current node into the hierarchy, either as a root node or as a child of a parent node.
     */
    private fun addNodeInHierarchy() {
        if (startElementName == adminDataTypeName && parentNodes.size == 1) {
            rootNodeIdentifierValue = "temp"
            importNodes[rootNodeIdentifierValue] = currentNode
        } else {
            val parentNode = currentNode.parentNode

            // If there is a parent node and it doesn't have any children that match the current element
            if (parentNode != null && !parentNode.children.any { child ->
                    child.name == currentNode.name
                            && (xmlReader.attributeCount == 0
                            || child.attributes["public-id"]?.get(0)?.newValue == xmlReader.getAttributeValue(0))
                }) {
                parentNode.children.add(currentNode)
            }
        }
    }

    /**
     * Handle the attributes for an element.
     *
     * TODO: Not sure how necessary this is since public-id seems to be the only attribute in import files
     */
    private fun handleElementAttributes() {
        if (xmlReader.attributeCount > 0) {
            for (i in 0 until xmlReader.attributeCount) {
                val attributeName = xmlReader.getAttributeName(i).localPart
                val attributeValue = xmlReader.getAttributeValue(i)

                // If the attribute is public-id and this is a root node
                if (attributeName == importItemIdentifier && startElementName == adminDataTypeName
                    && parentNodes.size == 1
                ) {
                    currentNode = importNodes[attributeValue] ?: currentNode
                    importNodes.remove(rootNodeIdentifierValue)
                    parentNodes[0] = currentNode
                    rootNodeIdentifierValue = attributeValue
                }

                // Update the attribute value versions
                val attributeVersions = currentNode.attributes[attributeName] ?: mutableListOf()
                if (attributeVersions.isEmpty()) {
                    attributeVersions.add(NodeValueVersion(file.name, attributeValue))
                } else {
                    val latestVersion = attributeVersions[attributeVersions.size - 1]
                    if (latestVersion.newValue != attributeValue) {
                        attributeVersions.add(NodeValueVersion(file.name, attributeValue, latestVersion))
                    }
                }

                currentNode.attributes[attributeName] = attributeVersions
            }
        }
    }

}