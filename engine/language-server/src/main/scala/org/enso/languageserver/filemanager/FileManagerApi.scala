package org.enso.languageserver.filemanager

import org.enso.languageserver.jsonrpc.{HasParams, HasResult, Method, Unused}

/**
  * The file manager JSON RPC API provided by the language server.
  * See [[https://github.com/luna/enso/blob/master/doc/design/engine/engine-services.md]]
  * for message specifications.
  */
object FileManagerApi {

  case object FileWrite extends Method("file/write") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = FileWriteParams
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case class FileWriteParams(path: Path, content: String)

}
