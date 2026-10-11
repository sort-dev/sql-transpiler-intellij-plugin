# Doris parser provenance

`lib/doris-fe-sql-parser-4.1.4-gad35a140c7fd.jar` is the standalone Apache Doris
parser used by the Doris plugin, copied byte-for-byte from its reviewed artifact.
It is not published on Maven Central. SQL Transpiler owns this parser and does not
search installed plugins, working directories or `brikk.doris.parser.jar`.

- Grammar: Apache Doris tag 4.1.4, `ad35a140c7fd0b842f18c23300bac581f7d04326`.
- Standalone facade/build module: `2a58ad96ac3ca6330a6df3d0b90f437351f63701`.
- The facade's internal version is 1.2-SNAPSHOT; it is not a published coordinate.
- SHA-256: `358ecccb39e84c65e62dc72b4090191c445fad4be1fb64e79ddaf68787d8703a`.
- License: Apache-2.0, with LICENSE and NOTICE inside the JAR.
- Parser generated with ANTLR 4.13.1; this plugin uses compatible runtime 4.13.2
  to align with its existing Trino verifier dependency.

The pinned [rebuild procedure](https://github.com/sort-dev/doris-intellij-plugin/blob/178918b358d483aba750c90e23384a4baf2e9b6a/vendor/README.md)
and [build script](https://github.com/sort-dev/doris-intellij-plugin/blob/178918b358d483aba750c90e23384a4baf2e9b6a/vendor/build-parser.sh)
combine that exact grammar with the separately pinned facade. No grammar or facade
source is changed by this plugin. Parser acceptance means that this pinned grammar
accepts the SQL, not that every server version supports it.
