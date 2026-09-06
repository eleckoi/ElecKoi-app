# sharp-libvips 1.3.2 source and replacement information

ElecKoi distributes the unmodified `libvips-cpp.so.8.18.3` from
`@img/sharp-libvips-linux-arm64@1.3.2` inside the DeepSeek Harness runtime
bundle. The binary and npm archive hashes are recorded in
`THIRD_PARTY_NOTICES.md` beside this file.

## Corresponding source

- Complete packaging and build source:
  https://github.com/lovell/sharp-libvips/tree/v1.3.2
- Exact packaging commit:
  `4da6d14c0d59866adfb9d8cf52bcaa53846dc4f6`
- Exact dependency versions and upstream source locations are defined by
  `versions.properties` and the build scripts at that tag.
- Primary LGPL library source:
  https://github.com/libvips/libvips/tree/v8.18.3
- Exact native component license texts are retained in
  `sharp-libvips-1.3.2-native-license-texts.md`.
- The librsvg Rust dependency graph and retained crate notices are in
  `sharp-libvips-1.3.2-librsvg-rust-third-party-notices.md`.
- The complete LGPL-3.0 license is packaged as `talloc-LGPL-3.0.txt` in the APK
  asset root and as `licenses/sharp-libvips/LGPL-3.0.txt` inside the Harness
  runtime bundle.

The sharp-libvips build edits librsvg features and then runs
`cargo update --workspace`; the resulting Cargo.lock is not included in the
published npm binary archive. Starting from librsvg tag 2.62.90's checked-in
lockfile and applying the exact feature edits removes only `color_quant`, `gif`
and `image-webp`; the resulting reconstructed lockfile SHA-256 is
`39a321a47d50375109eeb75a6f50e1c43b3731be85bcd7a44661bb80c9f636b9`.
The retained Rust notice records that source-locked graph, while the npm archive
and `.so` hashes above identify the exact distributed object code.

## Replacing the library

The `.egruntime` file is a gzip-compressed tar archive. To use a compatible
modified build:

1. Build an ARM64 glibc-compatible libvips stack from the source above.
2. Keep the ABI and archive path
   `lib/sharp/libvips-cpp.so.8.18.3`, or update all matching runtime consumers.
3. Extract the Harness `.egruntime`, replace that file, and retain or update
   the applicable license and third-party notices for the replacement build.
4. Repack the archive, update its SHA-256 and revision in
   `runtime/catalog/runtime-catalog.json`, then build and sign the APK from this
   source tree.

The application does not require an official ElecKoi signing key to run a
locally built APK. A modified APK must use the builder's own signing key and
may need the official installation removed before installation.
