package org.enso.languageserver.filemanager

import java.util.UUID

/**
  * A representation of a path relative to a specified content root.
  *
  * @param rootId a content root id that the path is relative to
  * @param segments path segments
  */
case class Path(rootId: UUID, segments: List[String])
