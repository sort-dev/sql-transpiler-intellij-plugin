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

The plugin distributes brikk-sql, brikk-sql-metadata and brikk-sql-verify 0.20.0,
DuckDB JDBC 1.5.6.0, Trino parser/grammar 483, ShardingSphere 5.5.2 and their
transitive libraries. Their existing JAR license/notice resources are retained.
brikk-sql includes SQLGlot-derived code by Toby Mao and contributors under MIT.

## Brikk SQL core, metadata and verification

The plugin embeds these Apache-2.0 libraries from https://github.com/brikk/brikk-sql:

- `dev.brikk.house:brikk-sql-jvm:0.20.0`
- `dev.brikk.house:brikk-sql-metadata-jvm:0.20.0`
- `dev.brikk.house:brikk-sql-verify-api:0.20.0`
- `dev.brikk.house:brikk-sql-verify-trino:0.20.0`
- `dev.brikk.house:brikk-sql-verify-duckdb:0.20.0`
- `dev.brikk.house:brikk-sql-verify-doris:0.20.0`
- `dev.brikk.house:brikk-sql-verify-shardingsphere:0.20.0`

The POMs list Jayson Minard and Sortdev SRL as developers. The core and
metadata JARs are named `brikk-sql-jvmMain-0.20.0.jar` and
`brikk-sql-metadata-jvmMain-0.20.0.jar` in Gradle module metadata. Their existing
license and notice resources, including third-party-derived portions, are retained.

## Brikk SQL IntelliJ support

`dev.brikk.house:brikk-sql-intellij-support:0.2.1` is an Apache-2.0 library from
https://github.com/brikk/brikk-sql. Its JAR retains its LICENSE and NOTICE. It is
embedded privately, with no plugin registrations or dependencies on peer plugins.

## Brikk Kotlin IntelliJ support

`dev.brikk.house:brikk-engine-kotlin-intellij-support:0.3.0` is an Apache-2.0
library from https://github.com/brikk/brikk-house. Its JAR retains its LICENSE and NOTICE.
Formal builds resolve it from Maven Central. Kotlin-aware entry points are behind the
optional Kotlin descriptor;
the library is not a compiler plugin and supplies no query execution.
