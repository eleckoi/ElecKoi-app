# ElecKoi local runtime third-party notices

This directory is packaged into the APK. It covers the native host plus the pinned Linux and Agent Harness components shipped with ElecKoi. It does not select or change the license of ElecKoi itself.

## Native host shipped in the APK

### PRoot 5.1.107.84

- License: GPL-2.0-or-later. For this distribution, the GPL version 3 option is
  selected so the packaged PRoot executable can coexist unambiguously with the
  LGPL-3.0-or-later `talloc` library and ElecKoi's AGPLv3 application.
- Upstream: https://github.com/termux/proot/tree/v5.1.107.84
- Verified source SHA-256: `a44ddbf18bc72c9780d56948b03aeda6d285392503ece0cae17cfc02e7bc7928`
- License texts: `proot-GPL-2.0.txt` and `GPL-3.0.txt`
- ElecKoi modification: the packaged `libtalloc.so.2` dependency string is shortened to `libtalloc.so`; its runtime loader and temporary-directory fallbacks are overridden with app-owned paths.

### talloc 2.4.3 shared library

- Upstream library license: LGPL-3.0-or-later
- Upstream: https://www.samba.org/ftp/talloc/talloc-2.4.3.tar.gz
- Verified source SHA-256: `dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd`
- License text: `talloc-LGPL-3.0.txt`
- Incorporated GNU GPL version 3 text: `GPL-3.0.txt`
- The audited Termux package recipe declares GPL-3.0. ElecKoi records that package declaration separately from the license stated by the library source itself.
- ElecKoi modification: the ELF SONAME is shortened from `libtalloc.so.2` to `libtalloc.so` for Android APK packaging.

### libandroid-shmem 0.7

- License: BSD-3-Clause
- Upstream: https://github.com/termux/libandroid-shmem/tree/v0.7
- Verified source SHA-256: `1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867`
- License text: `libandroid-shmem-BSD-3-Clause.txt`
- ElecKoi modification: rebuilt with the pinned patch `runtime/host/patches/libandroid-shmem-runtime-dir.patch`, which resolves key files through an app-provided writable directory instead of embedding Termux's private package path.

The exact Termux package-recipe commit, source and binary hashes, patch hash, NDK
version, reproducible build script, and generated output hashes live under
`runtime/host/` in the corresponding ElecKoi source tree. Anyone distributing an
APK must make the exact corresponding PRoot, talloc and libandroid-shmem source
archives, matching recipes, patches and build material available with the same
release. A binary-only APK is not the complete GPL/LGPL distribution package.

## Packaged Linux and Agent Harness components

### DeepSeek Harness 0.1.5-rc.2

- License: MIT
- Source commit: `fb2c4b9e698e30edb738bca4cf0618587db7d203`
- Upstream: https://github.com/deepseek-ai/deepseek-harness
- ElecKoi bundle: `deepseek-harness-0.1.5-rc.2-eleckoi.3-arm64.egruntime`
- Bundle SHA-256: `ca5c76ed90fbf61e7132697d7cfbeb908b052b93888c64891152f87e6a2d33dc`
- ElecKoi source patch: `runtime/deepseek/patches/0001-sdk-session-control.patch` (SHA-256 `ff001edf858716d8a03c64b092b253b4965328d7ce2c55ccf11c4984f1ecaf93`)
- License text: `deepseek-harness-MIT.txt`; the upstream license and complete upstream dependency notice are also retained inside the Harness bundle under `licenses/deepseek-harness/`.
- Embedded executable SHA-256: `331296587e4fb3c87e1fac1b920237df120adddafb86794efecd63b8142d2cc4`.
- The exact Linux ARM64 JavaScript package closure and retained license/notice
  texts are in `deepseek-harness-arm64-npm-third-party-notices.md`; that
  inventory is limited to the packaged production closure rather than the
  broader set of optional profiles and development tools in the upstream repository.
- Packaging: built with upstream `scripts/build-exe-for-python-sdk.ts`, using ElecKoi's stdout-clean Cordis composition and pinned ARM64 build inputs.

### DeepSeek Harness Landlock launcher

- Component: `@deepseek-ai/node-addon-system` native launcher, compiled
  from `native/system/packages/entry/src/main.c` at the DeepSeek Harness
  source commit recorded above.
- License: BSD-3-Clause, Copyright (c) 2026 node-addon-landlock-run contributors
- Packaged binary: `bin/landlock-run`
- Binary SHA-256: `366be5ad016f3fa498b59fc0f9d61f64300ab6375e82c2380c3bbfa2bd9dfc8b`
- License text: `landlock-run-BSD-3-Clause.txt` in the APK asset root and
  `licenses/landlock-run/LICENSE` inside the Harness bundle.

### Node.js 24.19.0 embedded in the DeepSeek Harness executable

- License: MIT for Node.js itself, with separately licensed bundled dependencies listed in Node.js's complete `LICENSE` file.
- Source tag: `v24.19.0`
- Upstream: https://github.com/nodejs/node/tree/v24.19.0
- Download: https://nodejs.org/download/release/v24.19.0/node-v24.19.0-linux-arm64.tar.gz
- Verified archive SHA-256: `d28c8a5bf0a808f0ed434a1dce8c54ae98f0371c0bd86ac58abc613f73e6643f`
- The complete license record from that exact archive is retained inside the Harness bundle at `licenses/node/LICENSE`.

### sharp-libvips Linux ARM64 1.3.2

- Package: `@img/sharp-libvips-linux-arm64@1.3.2`
- npm archive: https://registry.npmjs.org/@img/sharp-libvips-linux-arm64/-/sharp-libvips-linux-arm64-1.3.2.tgz
- Archive SHA-256: `8e57184950f004478587574f84d2b042b888ed2a4679e2c0e801ecd809a36404`
- Archive npm integrity: `sha512-dqVSFynCox4C/J8kT16V7SIFAns0IjgLwkvYT7p8LQVmJ5OS5b6tI9IGflxTeuBS//zXeFIUbwt5dwxyZ17cnA==`
- Packaging source: https://github.com/lovell/sharp-libvips/tree/v1.3.2, commit `4da6d14c0d59866adfb9d8cf52bcaa53846dc4f6`
- Primary library: libvips 8.18.6, LGPL-2.1-or-later; this distribution may
  exercise the license under LGPL-3.0 through the "or later" option.
- Packaged binary: `lib/sharp/libvips-cpp.so.8.18.6`, SHA-256 `264d3092d69de80f5acdb71c930efec8db5bd9627f41659ed3416566b9ae34b4`
- The unmodified upstream dependency notice is retained as
  `sharp-libvips-1.3.2-THIRD-PARTY-NOTICES.md`, followed there by ElecKoi's
  Linux ARM64 corrections. In particular, Cairo 1.18.4 is LGPL-2.1-only or
  MPL-1.1, not MPL-2.0, and `proxy-libintl` is not built for this glibc target.
- Exact native component license and patent texts are retained in
  `sharp-libvips-1.3.2-native-license-texts.md`.
- librsvg's source-reconstructed normal Rust dependency graph and retained
  license/copyright texts are in
  `sharp-libvips-1.3.2-librsvg-rust-third-party-notices.md`.
- The LGPL-3.0 text is retained inside the Harness bundle at
  `licenses/sharp-libvips/LGPL-3.0.txt` and in the APK asset root as
  `talloc-LGPL-3.0.txt`.
- Exact source and replacement information is in
  `sharp-libvips-1.3.2-SOURCE.md`.
- A distributor of the APK must provide the corresponding source for this exact
  library build and preserve a practical way to replace it with an
  interface-compatible modified build.

### @vscode/ripgrep Linux ARM64 1.18.0

- Package: `@vscode/ripgrep-linux-arm64@1.18.0`
- Archive: `https://registry.npmjs.org/@vscode/ripgrep-linux-arm64/-/ripgrep-linux-arm64-1.18.0.tgz`
- Archive SHA-256: `2d65504a71ea421d1c457177ebefcbe0d2d3a1f60f9709b6337f1d933553064b`
- Packaged `bin/rg` SHA-256: `e152ea689d6e8420357e592f0d8253b96476c164118ca3e6e13074fa1705ddda`
- License: MIT

The runtime uses this pinned executable for both official DSH `glob`/`grep` and ElecKoi virtual-setting search. Its exact package license is retained at `licenses/ripgrep/LICENSE`; DeepSeek Harness' generated dependency notice also records the package.

### Ubuntu Base 24.04.4 ARM64

- Upstream: https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/
- Verified archive SHA-256: `04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2`
- Ubuntu Base contains independently licensed packages. Their package-specific copyright and license records remain inside the installed root filesystem under `/usr/share/doc/*/copyright`.

### Ubuntu ca-certificates 20260601~24.04.1

- Upstream package: https://security.ubuntu.com/ubuntu/pool/main/c/ca-certificates/ca-certificates_20260601~24.04.1_all.deb
- Verified package SHA-256: `6bac2a01979e210d9eac1d4d56747ec709ea60654744d66705dc3c36e7629e50`
- The package's complete copyright and licensing record is retained in the DSH runtime bundle at `runtime-resources/ca-certificates/copyright`.
- ElecKoi deterministically concatenates the package's Mozilla-format root certificates into `ca-certificates.crt`; it does not add a project-specific or developer-specific certificate.

The runtime catalog at `runtime/catalog/runtime-catalog.json` is the authoritative machine-readable record of downloaded artifact versions, URLs and archive hashes. The source commits above identify the corresponding upstream source revisions; an archive SHA-256 identifies the exact distributed binary archive and is not interchangeable with a source commit.

Node.js 24.18.0 and pnpm 11.7.0 named in
`runtime/deepseek/build-manifest.json` are build-tool inputs. They are not
standalone components of the packaged `.egruntime`; the embedded executable's
Node.js 24.19.0 license record is listed above.
