# Ariteg

**This project has been fully moved to [hurui200320/ArchiveDAG](https://github.com/hurui200320/ArchiveDAG), which has all ideas implemented in this repo before [commit 85cb71c7488a866c42363fd8fd25d112a983ef7f](https://github.com/hurui200320/Ariteg/commit/85cb71c7488a866c42363fd8fd25d112a983ef7f), and has a Spring Boot based HTTP API. This repo is preserved for exploring new ideas since Kotlin is every easy to build prototype.**



Archiving your data in a IPFS style (aka storing your data using Merkle DAG and
archive them later).

***Use IPFS Drive if you only want to store data using Merkle DAG.
If the IPFS Drive can acknowledge the data it needed is archived and extra steps
are needed to make them ready to read, then I might abandon this project and use
IPFS Drive instead. But now (2021/10/14) I cannot find the source code and the 
available documents are very limited, so I'll continue developing this for now.***

## Why Merkle DAG?

If you know IPFS, then you might know they are using Merkle DAG to store data.
Using Merkle DAG gives you a lot of benefits, like easily deduplication. The downside
is instead of one file, now you get a bunch of smaller one, you need take good care
of them otherwise you won't recreate your data.

This program is not well-tested, and potentially corrupt your data. So use it in
your own risk.

Anyway, I wrote this program because I need someway to store \~40 TB of video files,
where I cannot host my own NAS (for a lot of reasons) and have to store them to
AWS S3. Since they are very cold data and don't need to frequently access, so I'll
store them using Glacier Deep Archive, which offers the cheapest price but need
12\~48 hours to prepare the data before it's ready to read.

Although it's the cheapest tier, 40 TB still cost me 40 USD per month, and everyone
knows that video files are hard to compress, so I came up the idea to store them
using Merkle DAG, hopefully I can reuse some data and save some cost.

This is different from IPFS, IPFS save space by sharing content globally. If
10 people holds a copy of OpenSUSE's installation ISO, then it's 44 GB. But If
everyone store it on IPFS, it gives same hash, thus share the same copy, it's only
4.4 GB globally. But this program stores data in a centralized way, you probably
won't share S3 bucket with everyone, so I don't know how well/bad it performs.
Also, S3 Glacier need additional 40 KB to hold headers, so each blob need 256 KB
for data, and spend additional 40 KB, which reduce the efficiency.

In conclusion, you don't need Merkle DAG to archive your data. This is just an
experiment, for fun.

## What's the different

The major different is this program ensures your data won't be corrupted by hash
collision. IPFS just assume SHA256 won't collision, if it does, then they upgrade
to a new hash function. But you won't know if there is a hash collision until you
find your file is not the one you put into the system.

This program use two different hash functions to identify the hash collision. 
Just like IPFS, you can swap to use any hash functions by implementing the 
[`MultihashProvider`](/src/main/kotlin/info/skyblond/ariteg/multihash/MultihashProvider.kt)
interface. For my personal flavor, I'll use SHA3-512 as the primary hash function,
the one used to address content, and blake2b-512 as the secondary hash function.
I want to use blake3 but sadly multihash don't officially support it yet. 

The primary hash function just like the SHA256 in IPFS, every link is hashed from
that function. But for each object, we record the secondary hash. Everytime before
we read and write, we check both the primary hash and secondary hash, if the 
primary hash gives the same value, but secondary don't, then there is a hash collision
on the primary hash, the program will throw an error and won't rewrite the existing
data. You will know it immediately since the writing request will fail.

This is not technically safe, but SHA3-512 and blake2b-512 collide at the same time?
You're the chosen one if you met that.

Other than that, there are tons of designs that different from IPFS, just because
I didn't think I HAVE TO stick with the IPFS design, I got my own thinking here.
It might not the best, or it might worse than the IPFS' one. If you have a good
one, then you're welcome to submit new issue/pr.

## Code in a glance

There are no finished API, and everything is just good enough to use.

There is [a protobuf file](/src/main/proto/info/skyblond/ariteg/protos/objects.proto)
defined all objects. All different types of objects shares the same structure.
And `info.skyblond.ariteg.objects` are the code representation of them. Currently
useless, but might useful in the future.

`info.skyblond.ariteg.multihash` contains codes about multihash, like the 
`MultihashProvier` interface, which gives a unified way to calculate multihash
when there are different implementations of hash functions.

`info.skyblond.ariteg.proto` is the APIs that help you manipulate proto objects.

`meta` has a [`ProtoMetaService`](/src/main/kotlin/info/skyblond/ariteg/proto/meta/ProtoMetaService.kt)
interface that defines all you need to store the metadata of a given proto object
stored on the backend (FileSystem, S3, etc.). That metadata contains the primary
hash, secondary hash, type of the object, and a temp value for write lock. It's
not necessary to have, but good to have if you want to use it in a concurrent way.

`storage` is the package where the backend codes live. [`ProtoStorageService`](/src/main/kotlin/info/skyblond/ariteg/proto/storage/ProtoStorageService.kt)
interface defined the basic operations a backend need to implement. It just stores
the proto and don't care how to deduplicate or something else.

To make `meta` and `storage` work together, there is a [`ProtoWriteService`](/src/main/kotlin/info/skyblond/ariteg/proto/ProtoWriteService.kt),
since the read operation is complicated (restore, etc.), there is only a writing 
implementation for now. It uses meta to decide if the current proto is exists
and uses storage to store the proto to the storage backend. Thus, individual
implementation won't bother how to deduplicate in there context.

But it's now enough to manipulate only protobuf objects. We need to store and
maybe read files and folders in and out. To save an index of known files and folders,
there is a [`FileIndexSerivce`](/src/main/kotlin/info/skyblond/ariteg/file/FileIndexService.kt)
interface to define all operations needed to maintain an index.

The [`FileWriteService`](/src/main/kotlin/info/skyblond/ariteg/file/FileWriteService.kt)
is an implementation of writing files and folders. Reading is complicated, so I want
some HTTP APIs to do that, like restore a list of protobuf object, then read it.

To use this program, you might have to understand the code and write your own code
to use it. This is far from a finished project or library, and again, it's only for
experiment with new ideas. If you like the idea and think it's good enough to be 
a standalone library or project, then feel free to ask me rewrite it into a library,
or submit your PR to make it, I'll appreciate it a lot.

If you really want to have a try, the existing test code might help you.
