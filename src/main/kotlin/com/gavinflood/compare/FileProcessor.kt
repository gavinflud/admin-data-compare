package com.gavinflood.compare

import com.gavinflood.compare.domain.Node
import com.gavinflood.compare.domain.Version
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

class FileProcessor(
    private val file: File,
    private val entities: MutableMap<String, Node>
) {

    private val xmlReader = getReader()
    private var startElementName = ""
    private var endElementName = ""
    private var lastElementName = ""

    // Indicates when the next element you hit will be the name of the admin data entities this file will import
    private var isNextElementAdminDataTypeName = false

    // Will be reassigned as soon as "isNextElementAdminDataTypeName" is set to true
    private var adminDataTypeName = "temp"

    // Will be immediately reassigned inside "handleStartElement" and this is never stored
    private var currentNode = Node("temp")

    // Stores the parent nodes in a list that we can pop them out of once we hit the closing element
    private val parentNodes = mutableListOf<Node>()

    // Will be the public-id for the root node
    private var rootNodeIdentifierValue = "temp"

    /**
     * Parse the XML file.
     */
    fun process() {
        println("Start: Processing ${file.name}")

        while (xmlReader.hasNext()) {
            when (xmlReader.next()) {
                XMLStreamConstants.START_ELEMENT -> handleStartElement()
                XMLStreamConstants.CHARACTERS -> handleCharacters()
                XMLStreamConstants.END_ELEMENT -> handleEndElement()
            }
        }

        println("Complete: Processing ${file.name}")
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
            if (currentNode.versions.isEmpty()) {
                currentNode.versions.add(Version(file.name, content))
            } else if (currentNode.name != lastElementName) {
                val latestVersion = currentNode.versions[currentNode.versions.size - 1]
                currentNode.versions.add(Version(file.name, content, latestVersion))
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
            entities[rootNodeIdentifierValue] = parentNodes[0]
        }

        // Remove the last parent node from the list since this is the closing tag for it
        if (parentNodes.isNotEmpty()) {
            parentNodes.removeAt(parentNodes.size - 1)
        }
    }

    /**
     * Set the current node.
     *
     * Before confirming it as a new node, check if it already exists under the last parent node's children. This means
     * it was introduced by a previous file and we want to use that one instead.
     */
    private fun setCurrentNode() {
        currentNode = Node(xmlReader.localName)

        if (parentNodes.isNotEmpty()) {
            val matches = parentNodes[parentNodes.size - 1].children.filter { node ->
                node.name == startElementName
                        && (xmlReader.attributeCount == 0 || node.publicId == xmlReader.getAttributeValue(0))
            }
            currentNode = if (matches.size == 1) matches[0] else currentNode
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
     * Set the type name for the entities that will be imported (limited to one entity type per file).
     */
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
            entities[rootNodeIdentifierValue] = currentNode
        } else {
            val parentNode = currentNode.parentNode

            // If there is a parent node and it doesn't have any children that match the current element
            if (parentNode != null && !parentNode.children.any { child ->
                    child.name == currentNode.name
                            && (xmlReader.attributeCount == 0 || child.publicId == xmlReader.getAttributeValue(0))
                }) {
                parentNode.children.add(currentNode)
            }
        }
    }

    /**
     * Handle the attributes for an element.
     */
    private fun handleElementAttributes() {
        if (xmlReader.attributeCount > 0) {
            val publicId = xmlReader.getAttributeValue(0)
            currentNode.publicId = publicId

            if (startElementName == adminDataTypeName && parentNodes.size == 1) {
                currentNode = entities[publicId] ?: currentNode
                entities.remove(rootNodeIdentifierValue)
                parentNodes[0] = currentNode
                rootNodeIdentifierValue = publicId
            }
        }
    }

}