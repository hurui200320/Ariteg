package info.skyblond.ariteg.proto.storage

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.objects.toMultihashBase58
import io.ipfs.multihash.Multihash

class MultihashNotMatchException(expected: Multihash, actual: Multihash) :
    Exception("Hash not match: expected: ${expected.toBase58()}, actual: ${actual.toBase58()}")

class ObjectNotFoundException(base58Link: String) : Exception("Link: $base58Link not found") {
    constructor(multiHash: Multihash) : this(multiHash.toBase58())
    constructor(link: AritegLink) : this(link.toMultihashBase58())
}

class ObjectNotPreparedException(base58Link: String) : Exception("Link: $base58Link not prepared") {
    constructor(multiHash: Multihash) : this(multiHash.toBase58())
    constructor(link: AritegLink) : this(link.toMultihashBase58())
}

class ObjectNotReadyException(base58Link: String) : Exception("Link: $base58Link not ready") {
    constructor(multiHash: Multihash) : this(multiHash.toBase58())
    constructor(link: AritegLink) : this(link.toMultihashBase58())
}
