# Ariteg

Archiving your data using Merkle tree/dag, hopefully you can save some space by doing so.

The name is generated randomly by [uniq.site](https://uniq.site).

***Use [restic](https://github.com/restic/restic) if you just want to store your
file and dedup at chunk level. It supports AWS S3 and is easier to use. Also more
reliable.***

***WARNING: This is a project for my own use and learn. There is no easy way
to just download and start using it. You can clone it and compile it by yourself,
with that process, you know what you're doing. There is no promise about stability,
performance, or compatibility. This repo has got at least 3 breaking changes which
requires you to dump everything out and in, in order to upgrade to newer version.
I make no promise to others, maybe not even to myself. You're using this at your own risk.
You have been warned.***

### How to use?

There are 5 maven modules which you can build and publish to your local repo.
This is not a long term project and I don't want to maintain it in the future,
so, no maven repo is published.

The group id is `info.skyblond.ariteg`. The artifact id is `ariteg-cmd`, `ariteg-core`,
`storage-core`, `storage-file`, and `storage-minio`.

The `ariteg-cmd` is the cli interface, it offers a lot of features using the rest
of 4 modules. It contains a properly configured `application` gradle plugin,
which allows you to build this cli app in a click.

The `ariteg-core` is the logic implementation. It is designed as a lib so that
any one can use it. It contains some core logic like how to put a stream/file
into the system and how to get it out. It relies on the storage things, which it
will instruct the actual storage backend to do the low-level read and write.

The `storage-core` is the meta part of the storage backend. It doesn't handle
the IO operations, but it defines the interface and implement the common functions
like translate request from `ariteg-core` to the actual data to read or write.
This is useful when you need to encrypt things with different backends. Surely
you don't want each backend has their own encryption method, right? This meta part
helps keep different backend in sync.

The `storage-file` is the storage backend based on Java's File api, aka the filesystem.
You can use it with hard drives, SSDs, SMB, NFS, what ever you want, as long as you're 
good with it. 

The `storage-minio` is the storage backend based on minio sdk, which basically is
self-hosted S3.

### AGPL? WTF??

Yes, I choose AGPL v3. Since my plan is to write code and push, no maintain in the future,
you shouldn't use my code in your production env, unless you carefully reviewed the code.
And to prevent someone accidentally use those code, I use AGPL v3 license.

However, if you want a commercial friendly license, emailed me (or open an issue),
and I'm glad to give you one, totally free (just don't blame me if something goes wrong).
