# Third-party notices

This software contains third-party libraries
used under the terms of the following licenses:
| Library       | Used under the terms of                                                                                   |
|---------------|-----------------------------------------------------------------------------------------------------------|
| aom           | BSD 2-Clause + [Alliance for Open Media Patent License 1.0](https://aomedia.org/license/patent-license/)  |
| cairo         | Mozilla Public License 2.0                                                                                |
| cgif          | MIT License                                                                                               |
| expat         | MIT License                                                                                               |
| fontconfig    | [fontconfig License](https://gitlab.freedesktop.org/fontconfig/fontconfig/blob/main/COPYING) (BSD-like)   |
| freetype      | [freetype License](https://git.savannah.gnu.org/cgit/freetype/freetype2.git/tree/docs/FTL.TXT) (BSD-like) |
| fribidi       | LGPLv3                                                                                                    |
| glib          | LGPLv3                                                                                                    |
| harfbuzz      | MIT License                                                                                               |
| highway       | BSD 3-Clause                                                                                              |
| lcms          | MIT License                                                                                               |
| libarchive    | BSD 2-Clause                                                                                              |
| libexif       | LGPLv3                                                                                                    |
| libffi        | MIT License                                                                                               |
| libheif       | LGPLv3                                                                                                    |
| libimagequant | [BSD 2-Clause](https://github.com/lovell/libimagequant/blob/main/COPYRIGHT)                               |
| libnsgif      | MIT License                                                                                               |
| libpng        | [libpng License](https://github.com/pnggroup/libpng/blob/master/LICENSE)                                  |
| librsvg       | LGPLv3                                                                                                    |
| libtiff       | [libtiff License](https://gitlab.com/libtiff/libtiff/blob/master/LICENSE.md) (BSD-like)                   |
| libultrahdr   | MIT License                                                                                               |
| libvips       | LGPLv3                                                                                                    |
| libwebp       | New BSD License                                                                                           |
| libxml2       | MIT License                                                                                               |
| mozjpeg       | [zlib License, IJG License, BSD 3-Clause](https://github.com/mozilla/mozjpeg/blob/master/LICENSE.md)      |
| pango         | LGPLv3                                                                                                    |
| pixman        | MIT License                                                                                               |
| proxy-libintl | LGPLv3                                                                                                    |
| zlib-ng       | [zlib License](https://github.com/zlib-ng/zlib-ng/blob/develop/LICENSE.md)                                |
Use of libraries under the terms of the LGPLv3 is via the
"any later version" clause of the LGPLv2 or LGPLv2.1.

Please report any errors or omissions via
https://github.com/lovell/sharp-libvips/issues/new

Source: https://github.com/lovell/sharp-libvips/blob/v1.3.2/THIRD-PARTY-NOTICES.md

## ElecKoi Linux ARM64 audit corrections

The table above is retained verbatim from sharp-libvips v1.3.2. It is a broad
upstream notice and is not exact for the Linux glibc ARM64 artifact distributed
by ElecKoi. The following corrections were verified against the v1.3.2 build
scripts and the exact component sources pinned by `versions.properties`:

- `proxy-libintl` is built only for Linux musl and macOS. It is not part of the
  Linux glibc ARM64 artifact.
- Cairo 1.18.4 is dual-licensed under LGPL-2.1-only or MPL-1.1. The upstream
  table's MPL-2.0 entry is not the license stated by Cairo's exact source.
- Highway 1.4.0 offers Apache-2.0 or BSD-3-Clause, rather than BSD-3-Clause
  alone.
- libultrahdr offers MIT and Apache-2.0 terms, rather than MIT alone.
- librsvg 2.62.90 is LGPL-2.1-or-later and also incorporates a Rust dependency
  closure that the upstream table does not enumerate.
- libnsgif is vendored inside libvips 8.18.3 and is covered by its retained MIT
  text.

The exact native component texts are retained in
`sharp-libvips-1.3.2-native-license-texts.md`. The source-reconstructed librsvg
Rust package graph and its license/copyright texts are retained in
`sharp-libvips-1.3.2-librsvg-rust-third-party-notices.md`.
