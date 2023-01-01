import java.util.*

object VersionUtils {
    private val calendar = Calendar.getInstance()

    @JvmStatic
    fun getYear(): String = calendar.get(Calendar.YEAR).toString()

    @JvmStatic
    fun getMonth(): String = "%02d".format(calendar.get(Calendar.MONTH) + 1)

    @JvmStatic
    fun getDayHourMinuteSecond(): String = "%02d%02d%02d%02d".format(
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        calendar.get(Calendar.SECOND),
    )

    @JvmStatic
    fun getHour(): String = "%02d".format(calendar.get(Calendar.HOUR_OF_DAY))

    @JvmStatic
    fun get(): String = "%02d".format(calendar.get(Calendar.HOUR_OF_DAY))

    @JvmStatic
    fun getVersion(): String = "${getYear()}.${getMonth()}.${getDayHourMinuteSecond()}"

}
