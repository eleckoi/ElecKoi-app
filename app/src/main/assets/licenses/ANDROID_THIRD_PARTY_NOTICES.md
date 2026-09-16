# ElecKoi Android third-party notices

This file covers the libraries and reusable visual assets packaged by the Android app. It does not select or change the license of ElecKoi itself. ElecKoi's AGPL-3.0-or-later text is packaged as `ElecKoi-AGPL-3.0-or-later.txt`. The exact resolved Maven artifact list and SHA-256 hashes are in `maven-release-runtime-components.txt` beside this file.

## Maven runtime libraries

The following component families are licensed under Apache License 2.0. The complete Apache-2.0 terms are packaged as `Apache-2.0.txt` in the APK's asset root.

- AndroidX, AndroidX Compose, Material Icons and AndroidX lifecycle/navigation/data libraries (`androidx.*`), https://github.com/androidx/androidx
- Kotlin, kotlinx libraries, JetBrains Compose and JetBrains annotations (`org.jetbrains.*`), https://github.com/JetBrains/kotlin and https://github.com/JetBrains/compose-multiplatform
- Coil 3.5.0 (`io.coil-kt.coil3:*`), https://github.com/coil-kt/coil
- Reorderable 3.1.0 (`sh.calvin.reorderable:*`), https://github.com/Calvin-LL/Reorderable
- Poko annotations 0.18.2 (`dev.drewhamilton.poko:poko-annotations-jvm`), an Apache-2.0 transitive dependency of MaterialKolor, https://github.com/drewhamilton/Poko
- AndroidSVG 1.4 (`com.caverock:androidsvg-aar`), https://github.com/BigBadaboom/androidsvg
- Cloudy 1.0.0-alpha01 (`com.github.skydoves:cloudy-android` and `cloudy-native`), https://github.com/skydoves/Cloudy
- OkHttp 4.12.0 (`com.squareup.okhttp3:okhttp`), https://github.com/square/okhttp
- Okio 3.17.0 (`com.squareup.okio:*`), https://github.com/square/okio
- Google Accompanist (`com.google.accompanist:*`), https://github.com/google/accompanist
- Guava (`com.google.guava:*`) and its Error Prone and J2ObjC annotation dependencies (`com.google.errorprone:error_prone_annotations`, `com.google.j2objc:j2objc-annotations`), https://github.com/google/guava
- Apache Commons Compress 1.28.0, Codec 1.19.0, IO 2.20.0 and Lang 3.18.0, https://commons.apache.org/; required attributions are in `apache-commons.NOTICE.txt`
- JSpecify 1.0.0 (`org.jspecify:jspecify`), https://github.com/jspecify/jspecify

Exceptions and additional licenses:

- AndroidX DataStore's repackaged Protocol Buffers runtime is BSD-3-Clause; see `androidx-datastore-protobuf.BSD-3-Clause.txt`.
- Checker Framework qualifiers 3.33.0 are MIT; see `checker-framework.MIT.txt`.
- Kotlin Multiplatform LaTeX Renderer 1.4.7 is MIT; see `latex-renderer.MIT.txt`.
- zstd-jni 1.5.7-16 is BSD-2-Clause; see `zstd-jni.BSD-2-Clause.txt`.
- MaterialKolor Material Color Utilities 2.0.2 (`com.materialkolor:material-color-utilities:*`) is published under MIT and is a Kotlin Multiplatform port of Google's Apache-2.0 Material Color Utilities source. ElecKoi packages `materialkolor-2.0.2.MIT.txt` and the common `Apache-2.0.txt` terms for both layers.
- JSch 2.28.0 is BSD-3-Clause, and its bundled JZlib and jBCrypt portions are BSD-3-Clause and ISC respectively; see `jsch-2.28.0.LICENSES.txt`.
- Bouncy Castle Provider 1.83 is licensed under the Bouncy Castle MIT-style license; see `bouncycastle-1.83.LICENSE.txt`.
- OkHttp's compiled Mozilla Public Suffix List data is MPL-2.0; its retained upstream notice is in `okhttp-publicsuffix-list.NOTICE.txt`. The complete MPL-2.0 terms are also packaged in `dompurify-3.3.2.LICENSE.txt`.
- JSR-305 3.0.2 has conflicting published metadata: its Maven POM declares Apache-2.0, while the archived reference-implementation source carries BSD-3-Clause terms and four concurrency annotations carry CC BY 2.5 attribution headers. ElecKoi retains all of these records in `jsr305-3.0.2.LICENSES.txt` rather than relying on the POM alone.

## JavaScript and icon assets

- Zod 4.4.3 bundled variable runtime (bundle SHA-256 `25eb39724d74b22b922fb2cf064b63c1a5efe54c22786d3084fec86aac76689e`): MIT; see `variable-runtime/zod.LICENSE.txt`.
- Showdown 2.1.0 Markdown converter: MIT; see `showdown-2.1.0.MIT.txt`.
- DOMPurify 3.3.2 HTML sanitizer: Apache-2.0 OR MPL-2.0; see `dompurify-3.3.2.LICENSE.txt`.
- TanStack Virtual Core 3.17.8 transcript virtualizer: MIT; see `tanstack-virtual-core-3.17.8-MIT.txt`.
- Phosphor icon paths: MIT; see `phosphor-icons.LICENSE.txt`. The exact upstream source revision was not recorded when the paths were imported.
- Lucide-derived icon paths: ISC, with Feather-derived icons under MIT; see `lucide-icons.ISC-MIT.txt`. The exact imported source revision was not recorded; the packaged license text was audited against Lucide commit `b442632ee6fe6250bf24fef026e44244a33812c9` on 2026-07-15.
- GitHub Primer Octicons `mark-github-24`: MIT; see `primer-octicons.MIT.txt`. The GitHub logo is used only to identify the GitHub project destination and does not imply endorsement.
- Lobe Icons model-provider SVGs: MIT; see `lobe-icons.MIT.txt` and `MODEL_ICON_PROVENANCE.md`.
- Selected DeepSeek Harness icon geometry: MIT, Copyright (c) 2026 DeepSeek; the
  applicable license text is packaged at the APK asset root as
  `deepseek-harness-MIT.txt`. The source locations are `DshPermissionIcons.kt`,
  `DshTimelineIcons.kt`, and the `DshIconPaths` section of `ElecKoiIconPaths.kt`.
- Selected Font Awesome Free icon geometry: CC BY 4.0, copyright Fonticons, Inc.
  The paths are reproduced without shape changes in `ElecKoiIconPaths.kt`; project and
  license links are https://fontawesome.com and
  https://creativecommons.org/licenses/by/4.0/. The imported version was not recorded.

## Native Markdown engine

- The ElecKoi JNI bridge links the pinned `xai-grok-markdown` and
  `xai-grok-markdown-core` crates from `xai-org/grok-build` commit
  `98c3b2438aa922fbbe6178a5c0a4c48f85edc8ce` under
  Apache-2.0, plus the vendored `mermaid-to-svg` engine under MIT.
- The resolved Rust dependency closure and applicable notices are packaged as
  `rust-markdown-third-party-notices.html`.

Provider names and logos can also be protected trademarks. The Lobe Icons software license does not grant trademark rights or imply endorsement by OpenAI, Anthropic, Google, DeepSeek or xAI.

ElecKoi's first-party application artwork is recorded separately in
`FIRST_PARTY_ASSETS.md`; it is not part of the third-party list above.

The packaged Linux and Agent Harness runtimes have their own notice files in the APK asset root. Native GPL/LGPL source-delivery obligations are described there and in the corresponding public source tree.
