package info.skyblond.ariteg.slicers

import java.util.concurrent.CompletableFuture

interface Slicer : Iterable<CompletableFuture<BlobDescriptor>>
