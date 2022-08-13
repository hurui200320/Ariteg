# Ariteg

Archiving your data using Merkle tree/dag, hopefully you can save some space by doing so.

***Use [restic](https://github.com/restic/restic) if you just want to store your
file and dedup at chunk level. It supports AWS S3.***

***WARNING: This is a simple project which is not meant to be maintained for a long time.
This is for my own use and learning. There is no promise about stability, performance, or safety.
You're using this at your own risk. You have been warned.***

### How to use?

Read [wiki](https://github.com/hurui200320/Ariteg/wiki) for more detailed info.

There are 3 maven modules which you can build and publish to your local repo.
This is not a long term project and I don't want to maintain it in the future,
so, no maven repo is published.

The group id is `info.skyblond.ariteg`. The artifact id is `ariteg-cmd`, `ariteg-core`,
and `ariteg-minio`.

The `ariteg-core` is the pure implementation. It can be used as a lib. By default,
it supports File storage, where each node is a file on the disk/SMB/NFS. If you
want to use minio directly, you need `ariteg-minio`, which add MinioStorage, where
each node is a object.

The `ariteg-cmd` is mostly useless. That module offers a default CLI for user to
upload, download, and list data. Normally, you won't need to import that module.

The CLI interface also offers a simple JavaScript runtime, implemented by [Mozilla Rhino](https://github.com/mozilla/rhino).
Read wiki for more detailed info.

For CLI artifact, you can refer to [GitHub Action](https://github.com/hurui200320/Ariteg/actions/workflows/gradle-build-ariteg-cmd.yml),
where the GitHub action will automatically build and publish.


### AGPL? WTF??

Yes, I choose AGPL v3. Since my plan is to write code and push, no maintain in the future,
you shouldn't use my code in your production env, unless you carefully reviewed the code.
And to prevent someone accidentally use those code, I use AGPL v3 license.

However, if you want a commercial friendly license, emailed me (or open an issue),
and I'm glad to give you one.
