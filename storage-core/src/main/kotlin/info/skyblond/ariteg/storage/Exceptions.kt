package info.skyblond.ariteg.storage

/**
 * When the objs actual hash doesn't match the expected one.
 * */
class HashNotMatchException(
    expected: String, actual: String,
) : Exception("expected hash: $expected, got: $actual")

class ObjectAlreadyExistsException(
    cause: Throwable?
) : Exception(cause)

class ObjectNotFoundException(
    cause: Throwable?
) : Exception(cause)
