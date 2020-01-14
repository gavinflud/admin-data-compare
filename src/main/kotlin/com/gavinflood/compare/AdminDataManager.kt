package com.gavinflood.compare

import com.gavinflood.compare.domain.AdminData
import java.io.File

class AdminDataManager(private val directoryPath: String) {

    private val orderFileName = "order.txt"
    private val adminData = AdminData()
    private val fileNameList = mutableListOf<String>()
    private lateinit var baseFileName: String

    /**
     * Process the admin data files.
     */
    fun process() {
        println("Start: Processing admin data")

        val orderFile = File("${directoryPath}\\${orderFileName}")
        var i = 0
        orderFile.forEachLine { fileName ->
            // Use the origin file as the base. Probably not great to require a complete export as the base.
            baseFileName = if (i == 0) fileName else baseFileName; ++i
            fileNameList.add(fileName)

            FileProcessor(File("${directoryPath}\\${fileName}"), adminData.entities).process()
        }

        println("Complete: Processing admin data")
    }

    /**
     * Exports the generated delta files that contain only the entities and their properties that were changed as
     * part of an admin data import file.
     */
    fun exportDeltas() {
        DeltaExport("${directoryPath}\\deltas", adminData, baseFileName).export()
    }

}