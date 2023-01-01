/**
 * List entries in the repo, filter them by name and download them.
 * */
let context = Packages.info.skyblond.ariteg.cmd.CmdContext
let utils = Packages.info.skyblond.ariteg.cmd.CmdUtils
let Entry = Packages.info.skyblond.ariteg.Entry

let logger = context.getLogger()

entries = context.listEntry()
downloadFolder = utils.createFolder('/path/to/download/folder')

for each (let /** @type Entry */ entry in entries) {
    let entryName = entry.getName()
    let entryId = entry.getId()
    logger.info(`${entryName} (${entryId})`)
    if (entryName.indexOf("something") !== -1) {
        context.download(entryId, downloadFolder)
    }
}



