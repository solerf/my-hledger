package app.frontend

/** Lifecycle of an asynchronously loaded resource. Shared across views so each
  * one can independently render a loading panel, an error message, or its
  * content from a single value.
  */
enum Loadable[+A]:
  case Loading
  case Loaded(value: A)
  case Failed(message: String)
