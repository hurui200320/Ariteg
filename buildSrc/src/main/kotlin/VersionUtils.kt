import java.util.*

object VersionUtils {
    private val calendar = Calendar.getInstance()

    /**
     * e.g.: 2022.07.$majorVersion
     * */
    const val majorVersion = "1"

    /**
     * e.g.: 2022.07.2-$suffix
     * The hyphen will be hidden if the suffix is empty.
     * */
    const val suffix = "alpha"

    @JvmStatic
    fun getYear(): String = calendar.get(Calendar.YEAR).toString()

    @JvmStatic
    fun getMonth(): String = "%02d".format(calendar.get(Calendar.MONTH) + 1)

    @JvmStatic
    fun getDayOfMonth(): String = "%02d".format(calendar.get(Calendar.DAY_OF_MONTH))

    @JvmStatic
    fun getVersion(): String =
        "${getYear()}.${getMonth()}.${majorVersion}${if (suffix.isNotEmpty()) "-" else ""}${suffix}"

}
