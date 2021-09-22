package info.skyblond.ariteg.storage

import info.skyblond.ariteg.AritegLink
import io.ipfs.multihash.Multihash

class HashNotMatchException(expected: Multihash, actual: Multihash) :
    Exception("Hash not match: expected: ${expected.toBase58()}, actual: ${actual.toBase58()}")

class ObjectNotFoundException(base58Link: String) : Exception("Link: $base58Link not found") {
    constructor(multiHash: Multihash) : this(multiHash.toBase58())
    constructor(link: AritegLink) : this(link.toMultihashBase58())
}

class ObjectNotReadyException(base58Link: String) : Exception("Link: $base58Link not found") {
    constructor(multiHash: Multihash) : this(multiHash.toBase58())
    constructor(link: AritegLink) : this(link.toMultihashBase58())
}

class ObjectNotSupportedException(message: String) : Exception(message)
