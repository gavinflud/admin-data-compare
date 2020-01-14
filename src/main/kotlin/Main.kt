import com.gavinflood.compare.AdminDataManager

fun main(args: Array<String>) {
    val manager = AdminDataManager(args[0])
    manager.process()
    manager.exportDeltas()
}