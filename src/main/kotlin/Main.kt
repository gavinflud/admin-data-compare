import com.gavinflood.compare.AdminDataChangeTracker

/**
 * Main Entry Point.
 */
fun main(args: Array<String>) {
    val adminDataFilesDirPath = args[0]
    val includeNewEmptyFields = args.size > 1 && args[1] == "true"
    val changeTracker = AdminDataChangeTracker(adminDataFilesDirPath, includeNewEmptyFields)
    changeTracker.process()
    changeTracker.generateChangeList()
    changeTracker.exportDeltas()

}