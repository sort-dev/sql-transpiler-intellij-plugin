# SQL Transpiler 0.4.2 dogfood build

Ready for local installation. Use Settings > Plugins > gear menu > Install Plugin
from Disk and select:

```text
/home/jayson/DEV/sortdev/sql-transpiler-intellij-plugin/build/dogfood/sql-transpiler-intellij-plugin-0.4.2.zip
```

SHA-256:

```text
e80450989a1c566206c943bd08c54fa59da37ff4070d272184dd3c89d4d11d9f
```

A `.zip.sha256` file is beside the ZIP. Size: 104,524,850 bytes. The packaged
descriptor says 0.4.2, with IDE branches 261/262. Previous versioned dogfood ZIPs
are preserved.

## Validation

This is a version/change-notes bump over [validated 0.4.1](DOGFOOD-0.4.1.md).
`./gradlew buildPlugin verifyPluginProjectConfiguration --console=plain` succeeded.
The configuration checker retains its known recommendation to remove `until-build`;
the advertised range remains intentional.

The new ZIP's 71 product class files and 57 runtime library JARs were compared with
0.4.1 and are byte-identical. The previous default/host/classloader tests and
six-target binary verification are documented in the 0.4.1 report; they were not
rerun for this metadata-only build.

Dependencies remain SQL core/metadata/verify 0.19.1, SQL IntelliJ support 0.1.1,
and Brikk Kotlin IntelliJ support 0.2.0. No Maven snapshot dependency is selected.
The fixed vendored Doris parser retains its documented internal 1.2-SNAPSHOT label.

Build log: `/tmp/opencode/sql-transpiler-042-build.log`. This is a local ZIP,
not a Marketplace or GitHub release.
