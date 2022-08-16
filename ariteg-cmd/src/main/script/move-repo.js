/**
 * Decrypt/encrypt repo.
 * By downloading all content and uploading again.
 * */
let context = Packages.info.skyblond.ariteg.cmd.CmdContext
let utils = Packages.info.skyblond.ariteg.cmd.CmdUtils
let Arrays = Packages.java.util.Arrays
let Entry = Java.type("info.skyblond.ariteg.Entry")
let File = Java.type("java.io.File")

let logger = context.getLogger()
let entries = context.listEntry()
let /** @type File */downloadFolder = utils.createFolder('/path/to/temp/download/folder')
let oldStorage = context.getStorage()
// Leave the password empty -> no encryption
// password is base64 encoded 32 bytes
let newStorage = utils.createFileStorage(utils.createFolder("/path/to/new/repo"), "")

for each (let /** @type Entry */ entry in entries) {
    let entryName = entry.getName()
    let oldEntryId = entry.getId()
    logger.info(`Downloading ${entryName} (${oldEntryId}) from old repo`)
    context.setStorage(oldStorage)
    context.download(oldEntryId, downloadFolder)
    let file = downloadFolder.listFiles()[0]
    context.setStorage(newStorage)
    logger.info(`Uploading ${entryName} to new repo`)
    context.upload(Arrays.asList(file))
    utils.deleteFile(file)
}
