# Enso Compiler and Interpreter

### Truffle Nodes creation convention

All Truffle nodes that are expected to be created as part of ASTs
should implement a public, static `build` method for creating an instance.
If the node is DSL generated, the `build` method should delegate to the
autogenerated `create` method, so that nodes are always created with `build`.
Such a convention allows us to easily switch node back and forth between 
manual and DSL generated implementations, without the need to change its
clients.

The only exception are nodes that are never expected to be a part of an AST
– e.g. root nodes of builtin functions, for which an `asFunction` method
should be implemented instead.

This convention should be implemented for every node throughout this codebase
– if you see one not obeying it – please fix it.