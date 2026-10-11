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

The plugin also distributes brikk-sql, brikk-sql-metadata and brikk-sql-verify 0.6.0,
DuckDB JDBC 1.5.4.0, Trino parser/grammar 481, ShardingSphere 5.5.2 and their
transitive libraries. Their existing JAR license/notice resources are retained.
brikk-sql includes SQLGlot-derived code by Toby Mao and contributors under MIT.
