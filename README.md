# Ariteg

**This project has been restarted.**


Archiving your data using Merkle tree/dag, ~~and store it with S3 deep archive layer.~~

***Use [restic](https://github.com/restic/restic) if you just want to store your
file and dedup at chunk level. It supports AWS S3.***

## Merkle DAG?

Just like IPFS, restic and other chunk level deduplication system, this program
slice files into smaller chunks. Some chunks might have identical data, so we can
keep only one copy of those chunks and call it duplication. To organize those
chunks, we use hash and some tree-like structure. However, the deduplication breaks
the tree: normally a node in a tree can only have 1 father node, but now with
deduplication, a node can have multiple father node, so it becomes a DAG.

The hash part is not ideal in real world: Hash can collide. In this program, I used
two different hash function and concat the result. More specifically, SHA3-512 and
blake2b-512, using multihash base58 encode, concat with `,`. This will ensure the
safety even SHA3-512 is collided on some chunks. But there is still a change when
SHA3-512 and black2b-512 collided at the same time, and, if this happens to you,
congratulations, you're the chosen one, LOL.

## S3? Deep archive?

S3 is a reliable storage service from AWS. The deep archive layer offers a great
price to store cold data. And I originally want to design a tool to utilize that,
aka, slice files into chunks and uploaded to S3 deep archive layer. When I don't
need those data, I can let them set there for around 1 USD per TB per month. And
whenever I need it, the tool will restore those chunks and reconstruct the file.

The storage price is cheap, however, the request fee is not. Assuming the max chunk
size is 2MB, then a 1TB file will be 524288 chunks or more. With each chunk equals
to a S3 object, the restore request itself will cost around 13 USD. And before that,
to make your object become deep archive, you need around 26 USD for lifecycle
transition requests fee. The lifecycle fee is one time, and restore fee is charged
everytime you restore files. And the last fee, data transfer fee, is 90 USD per TB.

For example, I have 40TB of Blu-ray disc rip off to storage. assuming every chunk
is max size, then, at least we need to deal with 20971520 chunks, not including
other metadata. Uploading those data will cost 1048.576 USD. Assuming I consume
1 TB per month, aka restore and download 1 TB of contents from AWS S3, the restored
copy stay 5 days before deleted,the restore fee is 13.1072 USD per month, the temp
storage fee is 4 USD per month, the download request fee is 0.2097 USD per month,
and the download fee is 92.16 USD per month. In total, monthly cost is around
40.5504 USD (storage) + 110 USD (usage) = 150 USD, with initial setup fee 1048.58 USD.
Also, deep archive layer requires you to store your data at least 180 days,
roughly 6 months. So the minimal fee is 1048.58 + 243.3 = 1291.9 USD (just store 6 months).

If I have a self-hosted NAS, for example, Synology DS1520+ with 5 x 12TB disks.
The initial setup fee will be 700 USD + 5 x 230 USD = 1850 USD. The monthly fee
is roughly less than 20 USD (120W x 24H per day x 30 day). Even with 2 disk
replacements per year (very unlikely), the monthly cost is less than 100 USD.

But this is not the end of the story. S3 protocol itself is very useful. And this
program support [Minio](https://min.io/), so you can host your own s3 gateway on
your home lab server/nas.
