# An ETC2 atlas, the editor's (r173, r140)

`city.etc2.atlas` and `city_1.zktx`: byte copies of the editor's ETC2 export of City (r125, the editor repository's `tools/r125-etc2-export.txt`):
one 512x1024 page, `GL_COMPRESSED_RGBA8_ETC2_EAC`, `MipMapLinearLinear`, its 11 mip levels in the file.
`core/test/.../pages/Etc2AtlasMipmapTest` loads it. Never re-export or overwrite them.

`city.atlas`, `city_1.png` and `city.plax`: the same export's PNG atlas and page (r140), byte copies too. `tools/android-etc2-check` packs this folder as its assets, and draws the page from either atlas on Android.
