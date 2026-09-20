# B16 v0→v1 migration tool

This is an explicit, offline migration aid for the nine B16 content filenames. It accepts the
known unversioned legacy-v0 root shape and adds only the root line `schemaVersion: 1`. It does not
rebalance values, rename IDs, convert units, or modify the input file.

The normal plugin bootstrap does not call this tool. It still rejects an unversioned file; an
operator must choose the output file and then replace/merge it deliberately. The runtime parser and
the full B16 schema remain in `rpg-platform`/`rpg-content`.

## Usage

```powershell
pwsh -NoProfile -File tools/b16-migration/scripts/migrate-b16.ps1 `
  -InputPath .\legacy\classes.yml `
  -OutputPath .\migrated\classes.yml `
  -BackupPath .\backup\classes.yml
```

All paths are resolved before the operation. Input, output and backup must be different. An
existing output or backup is an error; the script never uses overwrite/force semantics. A backup is
optional, but when supplied it is a byte-for-byte copy of the untouched source. The output is
created only after the legacy structure has passed the allow-list check; it is first written to an
exclusive temporary file in the destination directory and then atomically moved to `OutputPath`.
Temporary output files are removed on failure.

Already versioned `schemaVersion: 1` input is copied as an explicit `UNCHANGED` no-op. Missing,
unknown, duplicated, or unsupported root structure fails with the source filename and document
path. The resulting file must still pass the normal B16 runtime schema and cross-domain validation.

## Verification

```powershell
pwsh -NoProfile -File tools/b16-migration/tests/test-migrate-b16.ps1
```

The test covers source protection, explicit backup, version insertion, semantic value retention,
idempotent repeat migration, existing-target protection and ambiguous legacy input.
