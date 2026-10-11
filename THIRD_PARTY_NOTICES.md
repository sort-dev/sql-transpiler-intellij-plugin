# Third-party notices

The distribution retains the licenses and notices in its third-party library JARs.

## Apache Doris parser

This plugin includes the Apache Doris FE SQL parser, licensed under Apache License
2.0. Its 4.1.4 grammar and standalone facade have separate source pins documented
in `vendor/README.md`. The JAR includes its Apache license and this notice:

```text
Doris FE SQL Parser
Copyright 2022 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).
```

## ANTLR runtime

The parser uses `org.antlr:antlr4-runtime:4.13.2`, licensed under the BSD 3-Clause
license. The runtime JAR retains its license under `META-INF`.

## Existing runtime libraries

The development build distributes brikk-sql and brikk-sql-metadata 0.19.0-SNAPSHOT,
and brikk-sql-verify 0.19.0,
DuckDB JDBC 1.5.6.0, Trino parser/grammar 483, ShardingSphere 5.5.2 and their
transitive libraries. Their existing JAR license/notice resources are retained.
brikk-sql includes SQLGlot-derived code by Toby Mao and contributors under MIT.

## Brikk SQL IntelliJ support

`dev.brikk.house:brikk-sql-intellij-support:0.1.0` is an Apache-2.0 library from
https://github.com/brikk/brikk-sql. Its JAR retains its LICENSE and NOTICE. It is
embedded privately, with no plugin registrations or dependencies on peer plugins.

## Brikk Kotlin IntelliJ support

`dev.brikk.house:brikk-engine-kotlin-intellij-support:0.2.0-SNAPSHOT` is an Apache-2.0
library from https://github.com/brikk/brikk-house. This development artifact is
resolved only from the owner's Maven Local publication and contains its own LICENSE
and NOTICE. Kotlin-aware entry points are behind the optional Kotlin descriptor;
the library is not a compiler plugin and supplies no query execution.
